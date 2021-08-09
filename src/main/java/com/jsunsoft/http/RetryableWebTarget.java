/*
 * Copyright (c) 2021. Benik Arakelyan
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
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Beta
class RetryableWebTarget extends BasicWebTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryableWebTarget.class);

    private final RetryContext retryContext;

    RetryableWebTarget(CloseableHttpClient closeableHttpClient, URI uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, RetryContext retryContext, ResponseBodyReaderConfig responseBodyReaderConfig) {
        super(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
        this.retryContext = retryContext;
    }

    RetryableWebTarget(CloseableHttpClient closeableHttpClient, String uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, RetryContext retryContext, ResponseBodyReaderConfig responseBodyReaderConfig) throws URISyntaxException {
        super(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
        this.retryContext = retryContext;
    }

    @Override
    public Response request(HttpMethod method) {
        Response response = super.request(method);

        int retryCount = retryContext.getRetryCount();

        try {
            while (retryCount > 0 && retryContext.mustBeRetried(response)) {
                LOGGER.debug("Request to URI: [{}] has been retried. Response code: [{}]", response.getURI(), response.getStatusCode());

                TimeUnit.SECONDS.sleep(retryContext.getRetryDelay(response));

                response = retryContext.beforeRetry(this).request(method);
                retryCount--;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread was interrupted.", e);
        }

        return response;
    }
}
