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

import java.net.*;
import java.util.*;
import java.util.function.UnaryOperator;

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
    private final boolean disallowPrivateAndLoopbackHosts;
    private final UnaryOperator<String> payloadRedactor;

    BasicHttpRequest(CloseableHttpClient closeableHttpClient,
                     Collection<Header> defaultHeaders,
                     Collection<NameValuePair> defaultRequestParameters,
                     ResponseBodyReaderConfig responseBodyReaderConfig,
                     RequestBodySerializeConfig requestBodySerializeConfig,
                     Collection<String> allowedSchemes,
                     boolean requestPayloadLogging,
                     boolean disallowPrivateAndLoopbackHosts,
                     UnaryOperator<String> payloadRedactor) {
        this.closeableHttpClient = ArgsCheck.notNull(closeableHttpClient, "closeableHttpClient");
        this.defaultHeaders = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(defaultHeaders, "defaultHeaders")));
        this.defaultRequestParameters = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(defaultRequestParameters, "defaultRequestParameters")));
        this.responseBodyReaderConfig = ArgsCheck.notNull(responseBodyReaderConfig, "responseBodyReaderConfig");
        this.requestBodySerializeConfig = ArgsCheck.notNull(requestBodySerializeConfig, "requestBodySerializeConfig");
        this.allowedSchemes = Collections.unmodifiableSet(new LinkedHashSet<>(ArgsCheck.notNull(allowedSchemes, "allowedSchemes")));
        this.requestPayloadLogging = requestPayloadLogging;
        this.disallowPrivateAndLoopbackHosts = disallowPrivateAndLoopbackHosts;
        this.payloadRedactor = ArgsCheck.notNull(payloadRedactor, "payloadRedactor");
    }

    @Override
    public WebTarget target(URI uri) {
        ArgsCheck.notNull(uri, "uri");
        validateUriScheme(uri);
        validateUriHost(uri);
        return new BasicWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging, payloadRedactor);
    }

    @Override
    public WebTarget target(String uri) {
        ArgsCheck.notNull(uri, "uri");
        try {
            URI parsed = new URIBuilder(uri).build();
            validateUriScheme(parsed);
            validateUriHost(parsed);
            return new BasicWebTarget(closeableHttpClient, parsed, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging, payloadRedactor);
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
        validateUriHost(uri);
        return new RetryableWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, retryContext, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging, payloadRedactor);
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
            validateUriHost(parsed);
            return new RetryableWebTarget(closeableHttpClient, parsed, defaultHeaders, defaultRequestParameters, retryContext, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging, payloadRedactor);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public WebTarget immutableTarget(URI uri) {
        ArgsCheck.notNull(uri, "uri");
        validateUriScheme(uri);
        validateUriHost(uri);
        return new ImmutableWebTarget(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging, payloadRedactor);
    }

    @Override
    public WebTarget immutableTarget(String uri) {
        ArgsCheck.notNull(uri, "uri");
        try {
            URI parsed = new URIBuilder(uri).build();
            validateUriScheme(parsed);
            validateUriHost(parsed);
            return new ImmutableWebTarget(closeableHttpClient, parsed, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig, requestPayloadLogging, payloadRedactor);
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

    /**
     * SSRF guard. When enabled via {@link HttpRequestBuilder#disallowPrivateAndLoopbackHosts()},
     * resolves the URI host and rejects any address that is loopback ({@code 127.0.0.0/8},
     * {@code ::1}), unspecified ({@code 0.0.0.0}, {@code ::}), link-local ({@code 169.254.0.0/16},
     * {@code fe80::/10} — covers cloud-metadata endpoints like {@code 169.254.169.254}),
     * IPv4 site-local / RFC 1918 ({@code 10/8}, {@code 172.16/12}, {@code 192.168/16}), or
     * IPv6 unique-local ({@code fc00::/7}).
     * <p>
     * <b>Caveat: TOCTOU.</b> The DNS lookup happens at {@code target(...)} time. A malicious
     * resolver could return a public IP here and a private IP at request time (DNS rebinding).
     * This guard catches the most common attack vector — a user-controlled URL pointing
     * directly at a private hostname or literal — but is not a complete defence. For
     * defense-in-depth, also restrict outbound traffic at the network layer.
     */
    private void validateUriHost(URI uri) {
        if (!disallowPrivateAndLoopbackHosts) {
            return;
        }
        String host = uri.getHost();
        if (host == null) {
            return; // relative URI; let downstream surface a real error
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // Host doesn't resolve — not our problem; the connection will fail naturally.
            return;
        }
        for (InetAddress addr : addresses) {
            if (isPrivateLoopbackOrLocal(addr)) {
                throw new IllegalArgumentException(
                        "URI host '" + host + "' resolves to a private/loopback/link-local address (" +
                                addr.getHostAddress() + "). Rejected by disallowPrivateAndLoopbackHosts().");
            }
        }
    }

    private static boolean isPrivateLoopbackOrLocal(InetAddress addr) {
        if (addr.isLoopbackAddress()) return true;       // 127/8, ::1
        if (addr.isAnyLocalAddress()) return true;       // 0.0.0.0, ::
        if (addr.isLinkLocalAddress()) return true;      // 169.254/16, fe80::/10
        if (addr.isSiteLocalAddress()) return true;      // RFC 1918 — IPv4 only on JDK
        if (addr instanceof Inet6Address) {
            // RFC 4193 unique-local: fc00::/7. Inet6Address has no convenience method for this.
            byte[] b = addr.getAddress();
            if ((b[0] & 0xfe) == 0xfc) return true;
        }
        return false;
    }
}
