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
 * Builder for {@link HttpRequest}. Configure the builder fluently and call {@link #build()} to
 * produce an immutable, thread-safe {@link HttpRequest}.
 *
 * <h2>Reuse contract</h2>
 *
 * This builder is mutable and not thread-safe. Mutating setters such as
 * {@link #addDefaultHeader(String, String)} and
 * {@link #addDefaultRequestParameter(String, String) addDefaultRequestParameter}
 * <em>accumulate</em> state across calls — calling {@code addDefaultHeader} twice on the same
 * builder adds two headers; subsequent {@link #build()} calls each snapshot the current
 * accumulated state. The typical pattern is build-once-discard:
 *
 * <pre>{@code
 *     HttpRequest req = HttpRequestBuilder.create(client)
 *             .addDefaultHeader("Authorization", "Bearer ...")
 *             .build();
 * }</pre>
 *
 * Reuse for a second {@link #build()} is supported (each call produces an independent
 * {@code HttpRequest}); just remember that prior mutations are <em>not</em> reset between calls.
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
     * When a custom {@link ObjectMapper} is supplied via {@link #setDefaultJsonMapper(ObjectMapper)}
     * or {@link #setDefaultXmlMapper(ObjectMapper)}, the pattern is applied on a {@link ObjectMapper#copy() copy}
     * of the provided mapper — the caller's instance is left unmodified — and is registered as a
     * Jackson {@code configOverride} for the given type. A {@code configOverride} already set by the
     * caller for the same type will be replaced on the copy.
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
     * Adds a date serialization pattern for the request body serialization.
     * <p>
     * When a custom {@link ObjectMapper} is supplied via {@link #setDefaultJsonMapper(ObjectMapper)}
     * or {@link #setDefaultXmlMapper(ObjectMapper)}, the pattern is applied on a {@link ObjectMapper#copy() copy}
     * of the provided mapper — the caller's instance is left unmodified — and is registered as a
     * Jackson {@code configOverride} for the given type. A {@code configOverride} already set by the
     * caller for the same type will be replaced on the copy.
     *
     * @param dateType the date type
     * @param pattern  the pattern to use for serialization
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addRequestDefaultDateSerializationPattern(Class<?> dateType, String pattern) {
        requestBodySerializeConfigBuilder.addDateDeserializationPattern(dateType, pattern);
        return this;
    }

    /**
     * Sets the default JSON mapper used for request body serialization and response body deserialization.
     * <p>
     * A defensive {@link ObjectMapper#copy() copy} of the supplied mapper is taken at the moment this
     * method is called. The caller's instance is never mutated by the library, and any later changes
     * to it are ignored — the builder uses the snapshot captured here. Pass {@code null} to fall back
     * to the library default mapper.
     * <p>
     * <b>Strict vs. lenient deserialization.</b> When no mapper is supplied, the library default
     * disables {@code FAIL_ON_UNKNOWN_PROPERTIES} — unrecognized JSON fields are silently dropped.
     * That trade-off favours forward-compatibility (the server can add new fields without
     * breaking existing clients), but masks typos in field names and silent API drift in
     * development. To opt into Jackson's stricter default (throw on unknown properties), pass an
     * {@code ObjectMapper} you've configured yourself — your choice is preserved on the snapshot
     * since the library never re-applies its own defaults to a user-supplied mapper:
     * <pre>{@code
     *   HttpRequestBuilder.create(client)
     *           .setDefaultJsonMapper(new ObjectMapper())  // Jackson default = strict
     *           .build();
     * }</pre>
     *
     * @param defaultJsonMapper the JSON mapper to snapshot, or {@code null} to restore the default
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultJsonMapper(ObjectMapper defaultJsonMapper) {
        // Eager defensive copy: one independent snapshot per downstream config. Protects the caller's
        // instance from library-side mutation and keeps each config's date-pattern overrides from
        // leaking into the other. Runs twice at builder-build time; not on the per-request hot path.
        requestBodySerializeConfigBuilder.setDefaultJsonMapper(defaultJsonMapper == null ? null : defaultJsonMapper.copy());
        responseBodyReaderConfigBuilder.setDefaultJsonMapper(defaultJsonMapper == null ? null : defaultJsonMapper.copy());
        return this;
    }

    /**
     * Sets the default XML mapper used for request body serialization and response body deserialization.
     * <p>
     * A defensive {@link ObjectMapper#copy() copy} of the supplied mapper is taken at the moment this
     * method is called. The caller's instance is never mutated by the library, and any later changes
     * to it are ignored — the builder uses the snapshot captured here. Pass {@code null} to fall back
     * to the library default mapper.
     * <p>
     * Strict / lenient deserialization works the same as for the JSON mapper — see
     * {@link #setDefaultJsonMapper(ObjectMapper)} for the discussion.
     *
     * @param defaultXmlMapper the XML mapper to snapshot, or {@code null} to restore the default
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultXmlMapper(ObjectMapper defaultXmlMapper) {
        // See setDefaultJsonMapper — eager per-config defensive copy.
        requestBodySerializeConfigBuilder.setDefaultXmlMapper(defaultXmlMapper == null ? null : defaultXmlMapper.copy());
        responseBodyReaderConfigBuilder.setDefaultXmlMapper(defaultXmlMapper == null ? null : defaultXmlMapper.copy());
        return this;
    }

    /**
     * Adds preemptive HTTP Basic authentication to every request built from this {@code HttpRequest}.
     * <p>
     * <b>Use HTTPS.</b> HTTP Basic transmits credentials with only Base64 encoding, not encryption.
     * <p>
     * <b>In-memory exposure.</b> The {@code password} {@link String} is the worst form of secret
     * holder on the JVM — string literals are interned in the constant pool and even non-literal
     * {@code String}s cannot be zeroed. Once this method runs, an {@code "Basic &lt;base64&gt;"}
     * header value is also retained as a {@link String} in this builder's default-header list for
     * the lifetime of the resulting {@code HttpRequest}. This overload exists because it is the
     * shortest way to plug in a non-rotating credential, but for production code that rotates
     * secrets or wants any chance of zeroing, prefer {@link #basicAuth(String, char[])} (which
     * accepts a mutable buffer and zeros the source array). For full per-request rotation,
     * configure Apache HC5's {@code BasicCredentialsProvider} on a custom {@link
     * org.apache.hc.core5.http.protocol.HttpContext} instead of using this method.
     *
     * @param username the username (must not contain ':')
     * @param password the password (kept as a {@code String}; cannot be zeroed)
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
     * Adds preemptive HTTP Basic authentication using a {@code char[]} for the password.
     * <p>
     * The supplied {@code password} array is zeroed before this method returns; the intermediate
     * encoding buffers are also zeroed. The <em>final</em> {@code "Basic <base64>"} header value
     * is, however, still stored as a {@link String} in this builder's default-header list for
     * the lifetime of the resulting {@code HttpRequest} — a {@link String} cannot be zeroed in
     * the JVM. So this overload reduces, but does not eliminate, the period during which the
     * cleartext credential is recoverable from a heap dump.
     * <p>
     * For production rotation / vault-backed secrets, configure Apache HC5's
     * {@code BasicCredentialsProvider} on a custom {@link
     * org.apache.hc.core5.http.protocol.HttpContext} instead.
     * <p>
     * <b>Use HTTPS.</b> HTTP Basic is Base64, not encryption.
     *
     * @param username the username (must not contain ':')
     * @param password the password (will be zeroed after use)
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
     * Sets a cap on the number of bytes the library will read from a response body before
     * throwing {@link InvalidContentLengthException}.
     * <p>
     * Pass a strictly-positive value (e.g. {@code 1024}) to enable the cap. Pass {@code 0} or any
     * negative value to disable it — that is also the default state of a fresh builder, so a
     * caller who simply does not want a cap should not call this method at all.
     *
     * @param maxResponseBodySizeBytes positive byte cap, or {@code <= 0} to disable.
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder setMaxResponseBodySizeBytes(long maxResponseBodySizeBytes) {
        responseBodyReaderConfigBuilder.setMaxResponseBodySizeBytes(maxResponseBodySizeBytes);
        return this;
    }

    /**
     * Sets the charset used to decode response bodies into {@code String} when the response's
     * {@code Content-Type} header carries no {@code charset} parameter. Defaults to
     * {@link java.nio.charset.StandardCharsets#UTF_8 UTF-8} — the right choice in 2026 and a
     * stricter default than Apache HC5's bare ISO-8859-1 fallback.
     * <p>
     * When the server <em>does</em> include a {@code charset=...} on the response, that value
     * always takes precedence; this setting only affects the no-charset-header path.
     *
     * @param defaultResponseCharset the charset to use for charset-less responses; must not be
     *                               {@code null}.
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultResponseCharset(java.nio.charset.Charset defaultResponseCharset) {
        responseBodyReaderConfigBuilder.setDefaultResponseCharset(defaultResponseCharset);
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
