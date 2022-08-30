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

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Basic implementation of HttpRequest
 */
class BasicHttpRequest implements HttpRequest {
    private final CloseableHttpClient closeableHttpClient;
    private final Collection<Header> defaultHeaders;
    private final Collection<NameValuePair> defaultRequestParameters;
    private final ResponseBodyReaderConfig responseBodyReaderConfig;

    BasicHttpRequest(CloseableHttpClient closeableHttpClient, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig) {
        this.closeableHttpClient = ArgsCheck.notNull(closeableHttpClient, "closeableHttpClient");
        this.defaultHeaders = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(defaultHeaders, "defaultHeaders")));
        this.defaultRequestParameters = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(defaultRequestParameters, "defaultRequestParameters")));
        this.responseBodyReaderConfig = ArgsCheck.notNull(responseBodyReaderConfig, "responseBodyReaderConfig");
    }

    @Override
    public WebTarget target(URI uri) {
        ArgsCheck.notNull(uri, "uri");
        return new BasicWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
    }

    @Override
    public WebTarget target(String uri) {
        ArgsCheck.notNull(uri, "uri");
        try {
            return new BasicWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * @param uri          web resource URI. Must not be {@code null}.
     * @param retryContext retryContext. Must not be {@code null}.
     *
     * @return Retryable WebTarget instance
     *
     * @throws NullPointerException in case the supplied argument is {@code null}.
     */
    @Override
    public WebTarget retryableTarget(URI uri, RetryContext retryContext) {
        ArgsCheck.notNull(uri, "uri");
        ArgsCheck.notNull(retryContext, "retryContext");
        return new RetryableWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, retryContext, responseBodyReaderConfig);
    }

    /**
     * @param uri          The string to be parsed into a URI
     * @param retryContext retryContext. Must not be {@code null}.
     *
     * @return Retryable WebTarget instance
     *
     * @throws NullPointerException     If {@code str} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */
    @Override
    public WebTarget retryableTarget(String uri, RetryContext retryContext) {
        ArgsCheck.notNull(uri, "uri");
        ArgsCheck.notNull(retryContext, "retryContext");
        try {
            return new RetryableWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, retryContext, responseBodyReaderConfig);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public WebTarget immutableTarget(URI uri) {
        ArgsCheck.notNull(uri, "uri");
        return new ImmutableWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
    }

    @Override
    public WebTarget immutableTarget(String uri) {
        ArgsCheck.notNull(uri, "uri");
        try {
            return new ImmutableWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
