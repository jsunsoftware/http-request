/*
 * Copyright (c) 2024. Benik Arakelyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jsunsoft.http;

import com.jsunsoft.http.annotations.Beta;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Beta
class RetryableWebTarget extends BasicWebTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryableWebTarget.class);

    private final RetryContext retryContext;

    RetryableWebTarget(CloseableHttpClient closeableHttpClient, URI uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, RetryContext retryContext, ResponseBodyReaderConfig responseBodyReaderConfig, RequestBodySerializeConfig requestBodySerializeConfig, boolean requestPayloadLogging) {
        super(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging);
        this.retryContext = retryContext;
    }

    RetryableWebTarget(CloseableHttpClient closeableHttpClient, String uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, RetryContext retryContext, ResponseBodyReaderConfig responseBodyReaderConfig, RequestBodySerializeConfig requestBodySerializeConfig, boolean requestPayloadLogging) throws URISyntaxException {
        super(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging);
        this.retryContext = retryContext;
    }

    @Override
    public Response request(HttpMethod method, HttpContext context) {
        Response response = super.request(method, context);

        int retryCount = retryContext.getRetryCount();

        try {
            while (retryCount > 0 && retryContext.mustBeRetried(response)) {
                LOGGER.debug("Request to URI: [{}] has been retried. Response code: [{}]", response.getURI(), response.getCode());

                TimeUnit.SECONDS.sleep(retryContext.getRetryDelay(response));

                closeResponse(response);

                WebTarget retryTarget = retryContext.beforeRetry(this);
                if (retryTarget instanceof RetryableWebTarget) {
                    // Avoid recursion (and retryCount reset) when beforeRetry returns the same retryable instance.
                    // Execute a single request attempt using a non-retryable target copy.
                    response = new BasicWebTarget((BasicWebTarget) retryTarget).request(method, context);
                } else {
                    response = retryTarget.request(method, context);
                }
                retryCount--;
            }
        } catch (InterruptedException e) {
            closeResponse(response);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread was interrupted.", e);
        }

        return response;
    }

    private void closeResponse(Response response) {
        if (response != null) {
            try {
                response.close();
            } catch (IOException ex) {
                LOGGER.warn("Failed to close response.", ex);
            }
        }
    }
}
