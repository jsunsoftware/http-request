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

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Basic implementation of HttpRequest
 */
class BasicHttpRequest implements HttpRequest {
    private final CloseableHttpClient closeableHttpClient;
    private final Collection<Header> defaultHeaders;
    private final Collection<NameValuePair> defaultRequestParameters;
    private final ResponseBodyReaderConfig responseBodyReaderConfig;
    private final RequestBodySerializeConfig requestBodySerializeConfig;
    private final Set<String> allowedSchemes;
    private final boolean requestPayloadLogging;

    BasicHttpRequest(CloseableHttpClient closeableHttpClient,
                     Collection<Header> defaultHeaders,
                     Collection<NameValuePair> defaultRequestParameters,
                     ResponseBodyReaderConfig responseBodyReaderConfig,
                     RequestBodySerializeConfig requestBodySerializeConfig,
                     Collection<String> allowedSchemes,
                     boolean requestPayloadLogging) {
        this.closeableHttpClient = ArgsCheck.notNull(closeableHttpClient, "closeableHttpClient");
        this.defaultHeaders = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(defaultHeaders, "defaultHeaders")));
        this.defaultRequestParameters = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(defaultRequestParameters, "defaultRequestParameters")));
        this.responseBodyReaderConfig = ArgsCheck.notNull(responseBodyReaderConfig, "responseBodyReaderConfig");
        this.requestBodySerializeConfig = ArgsCheck.notNull(requestBodySerializeConfig, "requestBodySerializeConfig");
        this.allowedSchemes = Collections.unmodifiableSet(new LinkedHashSet<>(ArgsCheck.notNull(allowedSchemes, "allowedSchemes")));
        this.requestPayloadLogging = requestPayloadLogging;
    }

    @Override
    public WebTarget target(URI uri) {
        ArgsCheck.notNull(uri, "uri");
        validateUriScheme(uri);
        return new BasicWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging);
    }

    @Override
    public WebTarget target(String uri) {
        ArgsCheck.notNull(uri, "uri");
        try {
            URI parsed = new URIBuilder(uri).build();
            validateUriScheme(parsed);
            return new BasicWebTarget(closeableHttpClient, parsed, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * @param uri          web resource URI. Must not be {@code null}.
     * @param retryContext retryContext. Must not be {@code null}.
     * @return Retryable WebTarget instance
     * @throws NullPointerException in case the supplied argument is {@code null}.
     */
    @Override
    public WebTarget retryableTarget(URI uri, RetryContext retryContext) {
        ArgsCheck.notNull(uri, "uri");
        ArgsCheck.notNull(retryContext, "retryContext");
        validateUriScheme(uri);
        return new RetryableWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, retryContext, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging);
    }

    /**
     * @param uri          The string to be parsed into a URI
     * @param retryContext retryContext. Must not be {@code null}.
     * @return Retryable WebTarget instance
     * @throws NullPointerException     If {@code str} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */
    @Override
    public WebTarget retryableTarget(String uri, RetryContext retryContext) {
        ArgsCheck.notNull(uri, "uri");
        ArgsCheck.notNull(retryContext, "retryContext");
        try {
            URI parsed = new URIBuilder(uri).build();
            validateUriScheme(parsed);
            return new RetryableWebTarget(closeableHttpClient, parsed, defaultHeaders, defaultRequestParameters, retryContext, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public WebTarget immutableTarget(URI uri) {
        ArgsCheck.notNull(uri, "uri");
        validateUriScheme(uri);
        return new ImmutableWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging);
    }

    @Override
    public WebTarget immutableTarget(String uri) {
        ArgsCheck.notNull(uri, "uri");
        try {
            URI parsed = new URIBuilder(uri).build();
            validateUriScheme(parsed);
            return new ImmutableWebTarget(closeableHttpClient, parsed, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private void validateUriScheme(URI uri) {
        if (allowedSchemes.isEmpty()) {
            return;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !allowedSchemes.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported URI scheme: " + scheme + ". Allowed schemes: " + allowedSchemes);
        }
    }
}
