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
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Args;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;

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
    private UnaryOperator<String> payloadRedactor;
    private Charset defaultQueryCharset;
    private Charset defaultBodyCharset;

    private HttpRequestBuilder(CloseableHttpClient closeableHttpClient) {
        this.closeableHttpClient = ArgsCheck.notNull(closeableHttpClient, "closeableHttpClient");
    }

    /**
     * Creates a new instance of HttpRequestBuilder.
     *
     * <h4>Lifecycle ownership</h4>
     *
     * The supplied {@code closeableHttpClient} is <b>not</b> closed by this builder or by the
     * resulting {@link HttpRequest} — the caller retains full ownership. The library never
     * calls {@link CloseableHttpClient#close()} on the supplied client, because the same client
     * is typically shared across multiple {@code HttpRequest} instances and may be needed
     * past the lifetime of any individual one.
     *
     * <p>To release the underlying connection pool at shutdown, the caller must close the
     * client explicitly. Three idiomatic patterns:
     *
     * <ul>
     *   <li><b>Try-with-resources</b> (single-use scripts and short-lived tools):
     * <pre>{@code
     * try (CloseableHttpClient client = ClientBuilder.create().build()) {
     *     HttpRequest req = HttpRequestBuilder.create(client).build();
     *     // use req...
     * } // pool released here
     * }</pre></li>
     *   <li><b>Spring-managed bean</b> (long-running services):
     * <pre>{@code
     * @Bean(destroyMethod = "close")
     * CloseableHttpClient httpClient() { return ClientBuilder.create().build(); }
     * }</pre></li>
     *   <li><b>Application shutdown hook</b> (non-Spring servers):
     * <pre>{@code
     * CloseableHttpClient client = ClientBuilder.create().build();
     * Runtime.getRuntime().addShutdownHook(new Thread(() -> client.close()));
     * }</pre></li>
     * </ul>
     *
     * Forgetting to close the client leaks the connection pool — sockets remain open until the
     * JVM exits, which can exhaust the file-descriptor limit on long-running processes.
     *
     * @param closeableHttpClient the HTTP client to use; must not be {@code null}. The caller
     *                            owns this client and is responsible for closing it.
     * @return a new instance of HttpRequestBuilder
     * @throws NullPointerException if {@code closeableHttpClient} is {@code null}
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
     * or {@link #setDefaultXmlMapper(ObjectMapper)}, the pattern is installed on a derived mapper
     * produced via {@link ObjectMapper#rebuild() rebuild()} of the provided mapper — the caller's
     * instance is left unmodified (it is immutable in any case under Jackson 3) — and is registered
     * as a Jackson {@code configOverride} for the given type. A {@code configOverride} already set
     * by the caller for the same type will be replaced on the derivative.
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
     * or {@link #setDefaultXmlMapper(ObjectMapper)}, the pattern is installed on a derived mapper
     * produced via {@link ObjectMapper#rebuild() rebuild()} of the provided mapper — the caller's
     * instance is left unmodified — and is registered as a Jackson {@code configOverride} for the
     * given type. A {@code configOverride} already set by the caller for the same type will be
     * replaced on the derivative.
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
     * Jackson 3 mappers are immutable, so the supplied instance is stored by reference without any
     * defensive copy: nothing the library does can affect it. When a per-config date pattern is
     * registered via {@link #addResponseDefaultDateDeserializationPattern(Class, String)} /
     * {@link #addRequestDefaultDateSerializationPattern(Class, String)}, a derived mapper is built
     * via {@link ObjectMapper#rebuild() rebuild()} and used for that config only; the caller's
     * instance is never mutated. Pass {@code null} to fall back to the library default mapper.
     * <p>
     * <b>Strict vs. lenient deserialization.</b> When no mapper is supplied, the library default
     * disables {@code FAIL_ON_UNKNOWN_PROPERTIES} — unrecognized JSON fields are silently dropped.
     * That trade-off favours forward-compatibility (the server can add new fields without
     * breaking existing clients), but masks typos in field names and silent API drift in
     * development. To opt into Jackson's stricter default (throw on unknown properties), pass a
     * {@link tools.jackson.databind.json.JsonMapper} you've configured yourself — your choice is
     * preserved on the snapshot since the library never re-applies its own defaults to a
     * user-supplied mapper:
     * <pre>{@code
     *   import tools.jackson.databind.json.JsonMapper;
     *
     *   HttpRequestBuilder.create(client)
     *           .setDefaultJsonMapper(JsonMapper.builder().build())  // Jackson default = strict
     *           .build();
     * }</pre>
     *
     * @param defaultJsonMapper the JSON mapper to use, or {@code null} to restore the default
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultJsonMapper(ObjectMapper defaultJsonMapper) {
        // Jackson 3 mappers are immutable, so no defensive copy is needed: nothing the library does
        // (or any subsequent caller code) can mutate the instance. Per-config date-pattern overrides
        // are applied at build() time via ObjectMapper#rebuild(), which produces a fresh derivative
        // and leaves the original untouched — request- and response-side configs therefore stay
        // independent even though they share the same source reference here.
        requestBodySerializeConfigBuilder.setDefaultJsonMapper(defaultJsonMapper);
        responseBodyReaderConfigBuilder.setDefaultJsonMapper(defaultJsonMapper);
        return this;
    }

    /**
     * Sets the default XML mapper used for request body serialization and response body deserialization.
     * <p>
     * Jackson 3 mappers are immutable, so the supplied instance is stored by reference; no defensive
     * copy is necessary. Pass {@code null} to fall back to the library default mapper.
     * <p>
     * Strict / lenient deserialization works the same as for the JSON mapper — see
     * {@link #setDefaultJsonMapper(ObjectMapper)} for the discussion.
     *
     * @param defaultXmlMapper the XML mapper to use, or {@code null} to restore the default
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultXmlMapper(ObjectMapper defaultXmlMapper) {
        // See setDefaultJsonMapper — Jackson 3 mappers are immutable, no defensive copy required.
        requestBodySerializeConfigBuilder.setDefaultXmlMapper(defaultXmlMapper);
        responseBodyReaderConfigBuilder.setDefaultXmlMapper(defaultXmlMapper);
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
    public HttpRequestBuilder setDefaultResponseCharset(Charset defaultResponseCharset) {
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
                if (scheme == null || scheme.isBlank()) {
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
        return setAllowedSchemes(List.of("http", "https"));
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
     * Registers a redactor that runs over the request payload before it is logged via
     * {@link #enableRequestPayloadLogging()}. Multiple redactors compose left-to-right (the
     * output of one feeds the next).
     * <p>
     * Common use cases:
     * <ul>
     *   <li>Mask {@code "password"} field values in JSON bodies before logging.</li>
     *   <li>Strip {@code Authorization} header values that get serialised into the body.</li>
     *   <li>Truncate large payloads to a fixed prefix so logs don't balloon.</li>
     * </ul>
     * Redactors are only invoked when payload logging is on; if logging is disabled, registering
     * a redactor is a no-op (it's not called). Registering a redactor does <em>not</em> turn
     * logging on by itself — call {@link #enableRequestPayloadLogging()} explicitly.
     *
     * @param redactor function applied to the payload string before logging; must not be {@code null}
     *                 and must not return {@code null}.
     * @return the current instance of HttpRequestBuilder
     */
    @Beta
    public HttpRequestBuilder addPayloadRedactor(UnaryOperator<String> redactor) {
        ArgsCheck.notNull(redactor, "redactor");

        UnaryOperator<String> previous = this.payloadRedactor;

        this.payloadRedactor = previous == null
                ? redactor
                : value -> redactor.apply(previous.apply(value));
        return this;
    }

    /**
     * Sets the default charset for URI query-string percent-encoding applied to every
     * {@link WebTarget} created from the resulting {@link HttpRequest} — i.e. to all targets
     * returned by {@link HttpRequest#target(java.net.URI) target(URI)},
     * {@link HttpRequest#immutableTarget(java.net.URI) immutableTarget(URI)}, and
     * {@link HttpRequest#retryableTarget(java.net.URI, RetryContext) retryableTarget(URI, RetryContext)}.
     * <p>
     * A target may still override the charset per-call via
     * {@link WebTarget#setQueryCharset(Charset)} — this setter only sets the initial value.
     * Defaults to {@link java.nio.charset.StandardCharsets#UTF_8 UTF-8} when not called.
     * <p>
     * URI path segments are always percent-encoded as UTF-8 per RFC 3986 and are not affected
     * by this setting; pre-encode the path yourself if you need a different encoding for path
     * segments.
     *
     * @param defaultQueryCharset the charset to apply to every new target's query-string
     *                            encoding; {@code null} restores the library default (UTF-8).
     * @return the current instance of HttpRequestBuilder
     * @since 3.5.0
     */
    public HttpRequestBuilder setDefaultQueryCharset(Charset defaultQueryCharset) {
        this.defaultQueryCharset = defaultQueryCharset;
        return this;
    }

    /**
     * Sets the default charset for request-body conversion applied to every
     * {@link WebTarget} created from the resulting {@link HttpRequest} — used by the
     * {@code Object}-payload overloads (e.g. {@link WebTarget#post(Object)} when the
     * library serialises the payload into bytes).
     * <p>
     * A target may still override the charset per-call via
     * {@link WebTarget#setBodyCharset(Charset)} — this setter only sets the initial value.
     * Defaults to {@link java.nio.charset.StandardCharsets#UTF_8 UTF-8} when not called.
     *
     * @param defaultBodyCharset the charset to apply to every new target's body
     *                           conversion; {@code null} restores the library default (UTF-8).
     * @return the current instance of HttpRequestBuilder
     * @since 3.5.0
     */
    public HttpRequestBuilder setDefaultBodyCharset(Charset defaultBodyCharset) {
        this.defaultBodyCharset = defaultBodyCharset;
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

        UnaryOperator<String> effectiveRedactor = payloadRedactor != null
                ? payloadRedactor
                : UnaryOperator.identity();

        return new BasicHttpRequest(closeableHttpClient, defaultHeaders, defaultRequestParameters, responseBodyReaderConfigBuilder.build(), requestBodySerializeConfigBuilder.build(), allowedSchemes, requestPayloadLogging, effectiveRedactor, defaultQueryCharset, defaultBodyCharset);
    }
}
