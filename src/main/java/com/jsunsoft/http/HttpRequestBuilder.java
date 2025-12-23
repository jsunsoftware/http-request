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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsunsoft.http.annotations.Beta;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * Http request builder
 *
 * @see HttpRequest
 */
public class HttpRequestBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestBuilder.class);
    private final CloseableHttpClient closeableHttpClient;

    private List<NameValuePair> defaultRequestParameters;
    private Collection<Header> defaultHeaders;
    private final ResponseBodyReaderConfig.Builder responseBodyReaderConfigBuilder = ResponseBodyReaderConfig.create();
    private final RequestBodySerializeConfig.Builder requestBodySerializeConfigBuilder = RequestBodySerializeConfig.create();
    private Set<String> allowedSchemes;
    private boolean requestPayloadLogging;

    private HttpRequestBuilder(CloseableHttpClient closeableHttpClient) {
        this.closeableHttpClient = ArgsCheck.notNull(closeableHttpClient, "closeableHttpClient");
    }

    /**
     * Creates a new instance of HttpRequestBuilder.
     *
     * @param closeableHttpClient the HTTP client to use
     * @return a new instance of HttpRequestBuilder
     */
    public static HttpRequestBuilder create(CloseableHttpClient closeableHttpClient) {
        return new HttpRequestBuilder(closeableHttpClient);
    }

    /**
     * Adds a default header to be included in all requests.
     *
     * @param name  the name of the header
     * @param value the value of the header
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultHeader(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultHeader(new BasicHeader(name, value));
    }

    /**
     * Adds a default header to be included in all requests.
     *
     * @param header the header to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultHeader(Header header) {
        ArgsCheck.notNull(header, "header");

        if (defaultHeaders == null) {
            defaultHeaders = new ArrayList<>();
        }
        defaultHeaders.add(header);
        return this;
    }

    /**
     * Adds multiple default headers to be included in all requests.
     *
     * @param headers the headers to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultHeaders(Header... headers) {
        ArgsCheck.notNull(headers, "headers");

        Arrays.stream(headers).forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Adds multiple default headers to be included in all requests.
     *
     * @param headers the headers to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultHeaders(Collection<? extends Header> headers) {
        ArgsCheck.notNull(headers, "headers");

        headers.forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Sets the content type header for all requests.
     *
     * @param contentType the content type to set
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addContentType(ContentType contentType) {
        return addDefaultHeader(CONTENT_TYPE, contentType.toString());
    }

    /**
     * Adds a default request parameter to be included in all requests.
     *
     * @param name  the name of the parameter
     * @param value the value of the parameter
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultRequestParameter(new BasicNameValuePair(name, value));
    }

    /**
     * Adds multiple default request parameters to be included in all requests.
     *
     * @param nameValues the parameters to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(NameValuePair... nameValues) {
        int nameValuesLength = ArgsCheck.notNull(nameValues, "nameValues").length;
        Args.check(nameValuesLength != 0, "Length of parameter can't be ZERO");

        Arrays.stream(nameValues).forEach(this::addDefaultRequestParameter);
        return this;
    }

    /**
     * Adds a default request parameter to be included in all requests.
     *
     * @param nameValuePair the parameter to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(NameValuePair nameValuePair) {
        ArgsCheck.notNull(nameValuePair, "nameValuePair");
        if (this.defaultRequestParameters == null) {
            this.defaultRequestParameters = new ArrayList<>();
        }

        this.defaultRequestParameters.add(nameValuePair);
        return this;
    }

    /**
     * Adds multiple default request parameters to be included in all requests.
     *
     * @param defaultParameters the parameters to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(Map<String, String> defaultParameters) {
        ArgsCheck.notNull(defaultParameters, "defaultParameters");

        defaultParameters.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                .forEach(this::addDefaultRequestParameter);

        return this;
    }

    /**
     * Adds multiple default request parameters to be included in all requests.
     *
     * @param defaultRequestParameters the parameters to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(Collection<? extends NameValuePair> defaultRequestParameters) {
        ArgsCheck.notNull(defaultRequestParameters, "defaultRequestParameters");

        if (this.defaultRequestParameters == null) {
            this.defaultRequestParameters = new ArrayList<>();
        }
        defaultRequestParameters.forEach(this::addDefaultRequestParameter);

        return this;
    }

    /**
     * Adds a response body reader.
     *
     * @param responseBodyReader the response body reader to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addBodyReader(ResponseBodyReader<?> responseBodyReader) {
        responseBodyReaderConfigBuilder.addResponseBodyReader(responseBodyReader);
        return this;
    }

    /**
     * Adds a request body converter used for {@code Object} payload overloads (for example:
     * {@link WebTarget#rawRequest(HttpMethod, Object)}).
     *
     * @param requestBodyConverter the request body converter to add
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder addRequestBodyConverter(RequestBodyConverter requestBodyConverter) {
        requestBodySerializeConfigBuilder.addRequestBodyConverter(requestBodyConverter);
        return this;
    }


    /**
     * Disables the default request body converter(s).
     *
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder disableDefaultRequestBodyConverter() {
        requestBodySerializeConfigBuilder.setUseDefaultBodySerializer(false);
        return this;
    }

    /**
     * Enables the default request body converter(s).
     *
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder enableDefaultRequestBodyConverter() {
        requestBodySerializeConfigBuilder.setUseDefaultBodySerializer(true);
        return this;
    }


    /**
     * Enables the default body reader.
     *
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder enableDefaultBodyReader() {
        responseBodyReaderConfigBuilder.setUseDefaultBodyReader(true);
        return this;
    }

    /**
     * Disables the default body reader.
     *
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder disableDefaultBodyReader() {
        responseBodyReaderConfigBuilder.setUseDefaultBodyReader(false);
        return this;
    }

    /**
     * Adds a date deserialization pattern for the default response deserializer.
     * <p>
     * Note: This method will be ignored if {@link #setDefaultJsonMapper} is called.
     *
     * @param dateType the date type
     * @param pattern  the pattern to use for deserialization
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addResponseDefaultDateDeserializationPattern(Class<?> dateType, String pattern) {
        responseBodyReaderConfigBuilder.addDateDeserializationPattern(dateType, pattern);
        return this;
    }

    /**
     * Adds a date deserialization pattern for the request body serialization.
     * <p>
     * Note: This method will be ignored if {@link #setDefaultJsonMapper} is called.
     *
     * @param dateType the date type
     * @param pattern  the pattern to use for sserialization
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addRequestDefaultDateSerializationPattern(Class<?> dateType, String pattern) {
        requestBodySerializeConfigBuilder.addDateDeserializationPattern(dateType, pattern);
        return this;
    }

    /**
     * Sets the default JSON mapper for response body deserialization.
     *
     * @param defaultJsonMapper the JSON mapper to set
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultJsonMapper(ObjectMapper defaultJsonMapper) {
        requestBodySerializeConfigBuilder.setDefaultJsonMapper(defaultJsonMapper);
        responseBodyReaderConfigBuilder.setDefaultJsonMapper(defaultJsonMapper);
        return this;
    }

    /**
     * Sets the default XML mapper for response body deserialization.
     *
     * @param defaultXmlMapper the XML mapper to set
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultXmlMapper(ObjectMapper defaultXmlMapper) {
        requestBodySerializeConfigBuilder.setDefaultXmlMapper(defaultXmlMapper);
        responseBodyReaderConfigBuilder.setDefaultXmlMapper(defaultXmlMapper);
        return this;
    }

    /**
     * Adds basic authentication to the request with basic validation.
     * <p>
     * Note: Basic authentication is not encryption. Prefer HTTPS to protect credentials in transit.
     *
     * @param username the username (must not contain ':')
     * @param password the password
     * @return the current instance of HttpRequestBuilder
     * @throws IllegalArgumentException if username contains ':'
     */
    public HttpRequestBuilder basicAuth(String username, String password) {
        ArgsCheck.notNull(username, "username");
        ArgsCheck.notNull(password, "password");
        if (username.contains(":")) {
            throw new IllegalArgumentException("Username cannot contain ':' character when using basic authentication");
        }

        LOGGER.warn("Basic authentication is being used. Ensure HTTPS is used to protect credentials in transit.");

        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
        return addDefaultHeader(AUTHORIZATION, authHeader);
    }

    /**
     * Adds basic authentication to the request using {@code char[]} for password.
     * The password array will be cleared after use.
     *
     * @param username the username (must not contain ':')
     * @param password the password (will be cleared after use)
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder basicAuth(String username, char[] password) {
        ArgsCheck.notNull(username, "username");
        ArgsCheck.notNull(password, "password");
        if (username.contains(":")) {
            throw new IllegalArgumentException("Username cannot contain ':' character when using basic authentication");
        }

        LOGGER.warn("Basic authentication is being used. Ensure HTTPS is used to protect credentials in transit.");

        byte[] userBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] separator = ":".getBytes(StandardCharsets.UTF_8);

        ByteBuffer passBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(password));
        byte[] passBytes = new byte[passBuffer.remaining()];
        passBuffer.get(passBytes);

        byte[] combined = new byte[userBytes.length + separator.length + passBytes.length];
        System.arraycopy(userBytes, 0, combined, 0, userBytes.length);
        System.arraycopy(separator, 0, combined, userBytes.length, separator.length);
        System.arraycopy(passBytes, 0, combined, userBytes.length + separator.length, passBytes.length);

        byte[] encodedAuth = Base64.getEncoder().encode(combined);
        String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);

        Arrays.fill(passBytes, (byte) 0);
        Arrays.fill(combined, (byte) 0);
        // Clean ByteBuffer content if possible, though it's backed by array often
        if (passBuffer.hasArray()) {
            Arrays.fill(passBuffer.array(), (byte) 0);
        }

        try {
            return addDefaultHeader(AUTHORIZATION, authHeader);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /**
     * Sets a maximum allowed response body size in bytes.
     * <p>
     * Default is unlimited ({@code 0}).
     *
     * @param maxResponseBodySizeBytes max bytes to read from response bodies; {@code <= 0} disables the limit.
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder setMaxResponseBodySizeBytes(long maxResponseBodySizeBytes) {
        responseBodyReaderConfigBuilder.setMaxResponseBodySizeBytes(maxResponseBodySizeBytes);
        return this;
    }

    /**
     * Restrict allowed URI schemes for targets created from this {@link HttpRequest}.
     * <p>
     * By default, schemes are not restricted (backward compatible).
     *
     * @param schemes allowed schemes (e.g. "http", "https"); {@code null} clears the restriction.
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    HttpRequestBuilder setAllowedSchemes(Collection<String> schemes) {
        if (schemes == null) {
            this.allowedSchemes = null;
        } else {
            Set<String> s = new LinkedHashSet<>();
            for (String scheme : schemes) {
                if (scheme == null || scheme.trim().isEmpty()) {
                    throw new IllegalArgumentException("Scheme must not be null/blank");
                }
                s.add(scheme.trim().toLowerCase(Locale.ROOT));
            }
            this.allowedSchemes = s;
        }
        return this;
    }

    /**
     * Convenience for {@link #setAllowedSchemes(Collection)} with "http" and "https".
     *
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder allowHttpAndHttpsOnly() {
        return setAllowedSchemes(Arrays.asList("http", "https"));
    }

    /**
     * Enables request payload logging.
     *
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder enableRequestPayloadLogging() {
        this.requestPayloadLogging = true;
        return this;
    }

    /**
     * Builds the HttpRequest instance.
     *
     * @return the HttpRequest instance
     */
    public HttpRequest build() {
        if (defaultHeaders == null) {
            defaultHeaders = Collections.emptyList();
        }

        if (defaultRequestParameters == null) {
            defaultRequestParameters = Collections.emptyList();
        }

        if (allowedSchemes == null) {
            allowedSchemes = Collections.emptySet();
        }

        return new BasicHttpRequest(closeableHttpClient, defaultHeaders, defaultRequestParameters, responseBodyReaderConfigBuilder.build(), requestBodySerializeConfigBuilder.build(), allowedSchemes, requestPayloadLogging);
    }
}
