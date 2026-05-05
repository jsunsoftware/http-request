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
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;


/**
 * Builder for {@link org.apache.hc.client5.http.impl.classic.CloseableHttpClient}.
 * <p>
 * HttpClients are heavy-weight objects that manage the client-side communication infrastructure.
 * Initialization as well as disposal of a {@link org.apache.hc.client5.http.impl.classic.CloseableHttpClient} instance may be a rather expensive operation.
 * It is therefore advised to construct only a small number of {@link org.apache.hc.client5.http.impl.classic.CloseableHttpClient} instances in the application.
 *
 * <h2>Reuse contract</h2>
 *
 * This builder is mutable and not thread-safe. Mutating setters (timeouts, default headers,
 * customizers, …) <em>accumulate</em> state across calls; setters such as
 * {@link #addDefaultHeader(String, String)} append, while value-style setters such as
 * {@link #setConnectTimeout(int)} overwrite. {@link #build()} is idempotent — calling it more
 * than once produces independent {@link org.apache.hc.client5.http.impl.classic.CloseableHttpClient}
 * instances and the user-supplied customizers are applied to a fresh per-call {@code Builder}, so
 * stateful customizers don't compound across builds. The typical pattern is still
 * build-once-discard.
 */
public class ClientBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientBuilder.class);

    private final RequestConfig.Builder defaultRequestConfigBuilder = RequestConfig.custom()
            .setResponseTimeout(Timeout.ofSeconds(30))
            .setConnectionRequestTimeout(Timeout.ofSeconds(30));
    private final ConnectionConfig.Builder defaultConnectionConfigBuilder = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(10))
            .setSocketTimeout(Timeout.ofSeconds(30));
    private final HostPoolConfig hostPoolConfig = HostPoolConfig.create();
    private RedirectStrategy redirectStrategy;
    private Collection<Consumer<HttpClientBuilder>> httpClientBuilderCustomizers;
    private Collection<Consumer<RequestConfig.Builder>> defaultRequestConfigBuilderCustomizers;
    private Collection<Consumer<PoolingHttpClientConnectionManagerBuilder>> defaultConnectionManagerBuilderCustomizers;
    private Collection<Consumer<ConnectionConfig.Builder>> defaultConnectionConfigBuilderCustomizers;
    private Collection<Header> defaultHeaders;
    private HttpHost proxy;
    private boolean useDefaultProxy;
    private ClientTlsStrategyBuilder clientTlsStrategyBuilder;
    private boolean cookieManagementEnabled;
    private boolean automaticRetriesEnabled;
    private int maxResponseHeaderCount = -1;
    private int maxResponseLineLength = -1;

    ClientBuilder() {

    }

    /**
     * Creates a new {@link ClientBuilder} with default configuration.
     *
     * @return new builder instance
     */
    public static ClientBuilder create() {
        return new ClientBuilder();
    }

    /**
     * Determines the timeout in milliseconds until a connection is established.
     * A timeout value of zero is interpreted as an infinite timeout.
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * A negative value is interpreted as undefined (system default).
     * </p>
     * <p>
     * Default: {@code 10 seconds}
     * </p>
     * Note: Can be overridden by {@linkplain #addDefaultConnectionConfigCustomizer}
     *
     * @param connectTimeout The Connection Timeout (http.connection.timeout) – the time to establish the connection with the remote host.
     * @return ClientBuilder instance
     * @see org.apache.hc.client5.http.config.ConnectionConfig.Builder#setConnectTimeout(Timeout)
     */
    public ClientBuilder setConnectTimeout(Timeout connectTimeout) {
        defaultConnectionConfigBuilder.setConnectTimeout(connectTimeout);
        return this;
    }

    /**
     * @param connectTimeout The Connection Timeout (http.connection.timeout) in milliseconds – to establish the connection with the remote host.
     * @return ClientBuilder instance
     * @see #setConnectTimeout(Timeout)
     */
    public ClientBuilder setConnectTimeout(int connectTimeout) {

        return setConnectTimeout(Timeout.ofMilliseconds(connectTimeout));
    }

    /**
     * Determines the timeout until arrival of a response from the opposite
     * endpoint.
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * </p>
     * <p>
     * Please note that response timeout may be unsupported by
     * HTTP transports with message multiplexing.
     * </p>
     * <p>
     * Default: {@code 30 seconds}
     * </p>
     * Note: Can be overridden by {@linkplain #addDefaultRequestConfigCustomizer}
     *
     * @param responseTimeout The timeout waiting for data – after the connection was established.
     * @return ClientBuilder instance
     * @see org.apache.hc.client5.http.config.RequestConfig.Builder#setResponseTimeout(Timeout)
     */
    public ClientBuilder setResponseTimeout(Timeout responseTimeout) {
        defaultRequestConfigBuilder.setResponseTimeout(responseTimeout);
        return this;
    }

    /**
     * @param responseTimeout The timeout in milliseconds waiting for data – after the connection was established.
     * @return ClientBuilder instance
     * @see #setResponseTimeout(Timeout)
     */
    public ClientBuilder setResponseTimeout(int responseTimeout) {
        return setResponseTimeout(Timeout.ofMilliseconds(responseTimeout));
    }

    /**
     * The timeout in milliseconds used when requesting a connection
     * from the connection manager. A timeout value of zero is interpreted
     * as an infinite timeout.
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * A negative value is interpreted as undefined (system default).
     * </p>
     * <p>
     * Default: {@code 30 seconds}
     * </p>
     * <p>
     * Note: Can be overridden by {@linkplain #addDefaultRequestConfigCustomizer}
     *
     * @param connectionRequestTimeout The Connection Manager Timeout (http.connection-manager.timeout) –
     *                                 the time to wait for a connection from the connection manager/pool.
     * @return ClientBuilder instance
     * @see org.apache.hc.client5.http.config.RequestConfig.Builder#setConnectionRequestTimeout
     * @see #addDefaultRequestConfigCustomizer
     */
    public ClientBuilder setConnectionRequestTimeout(Timeout connectionRequestTimeout) {
        defaultRequestConfigBuilder.setConnectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    /**
     * @param connectionRequestTimeout The Connection Manager Timeout (http.connection-manager.timeout) in milliseconds –
     *                                 the time to wait for a connection from the connection manager/pool.
     * @return ClientBuilder instance
     * @see #setConnectionRequestTimeout
     */
    public ClientBuilder setConnectionRequestTimeout(int connectionRequestTimeout) {
        return setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout));
    }

    /**
     * @param defaultRequestConfigBuilderConsumer the consumer instance which provides {@link org.apache.hc.client5.http.config.RequestConfig.Builder} to customize default request config
     * @return ClientBuilder instance
     */
    public ClientBuilder addDefaultRequestConfigCustomizer(Consumer<RequestConfig.Builder> defaultRequestConfigBuilderConsumer) {
        if (defaultRequestConfigBuilderCustomizers == null) {
            defaultRequestConfigBuilderCustomizers = new LinkedHashSet<>();
        }
        defaultRequestConfigBuilderCustomizers.add(defaultRequestConfigBuilderConsumer);
        return this;
    }

    /**
     * Defines the socket timeout ({@code SO_TIMEOUT}) in milliseconds,
     * which is the timeout for waiting for data  or, put differently,
     * a maximum period inactivity between two consecutive data packets.
     * <p>
     * A timeout value of zero is interpreted as an infinite timeout.
     * A negative value is interpreted as undefined (system default).
     * </p>
     * <p>
     * Default: {@code 30000ms}
     * </p>
     * Note: Can be overridden by {@linkplain #addDefaultConnectionConfigCustomizer(Consumer)}
     *
     * @param socketTimeOut The Socket Timeout (http.socket.timeout) – the time waiting for data – after the connection was established;
     *                      maximum time of inactivity between two data packets.
     * @return ClientBuilder instance
     * @see org.apache.hc.client5.http.config.ConnectionConfig.Builder#setSocketTimeout
     * @see #addDefaultConnectionConfigCustomizer(Consumer)
     */
    public ClientBuilder setSocketTimeout(Timeout socketTimeOut) {
        defaultConnectionConfigBuilder.setSocketTimeout(socketTimeOut);
        return this;
    }

    /**
     * @param socketTimeOutMillis The Socket Timeout (http.socket.timeout) in milliseconds – the time waiting for data –
     *                            after the connection was established;
     * @return ClientBuilder instance
     * @see #setSocketTimeout
     */
    public ClientBuilder setSocketTimeout(int socketTimeOutMillis) {
        setSocketTimeout(Timeout.ofMilliseconds(socketTimeOutMillis));
        return this;
    }

    /**
     * Defines the total span of time connections can be kept alive or execute requests.
     * <p>
     * Default: {@code null} (undefined)
     * </p>
     * Note: Can be overridden by {@linkplain #addDefaultConnectionConfigCustomizer(Consumer)}
     *
     * @param timeToLive connection time to live
     * @return ClientBuilder instance
     * @see org.apache.hc.client5.http.config.ConnectionConfig.Builder#setTimeToLive(TimeValue)
     * @see #addDefaultConnectionConfigCustomizer(Consumer)
     */
    public ClientBuilder setConnectionTimeToLive(TimeValue timeToLive) {
        defaultConnectionConfigBuilder.setTimeToLive(timeToLive);
        return this;
    }

    /**
     * Defines the total span of time connections can be kept alive or execute requests.
     * <p>
     * Default: {@code null} (undefined)
     * </p>
     * Note: Can be overridden by {@linkplain #addDefaultConnectionConfigCustomizer(Consumer)}
     *
     * @param timeToLiveMillis connection time to live in milliseconds
     * @return ClientBuilder instance
     * @see org.apache.hc.client5.http.config.ConnectionConfig.Builder#setTimeToLive(TimeValue)
     * @see #addDefaultConnectionConfigCustomizer(Consumer)
     */
    public ClientBuilder setConnectionTimeToLive(int timeToLiveMillis) {
        setConnectionTimeToLive(TimeValue.ofMilliseconds(timeToLiveMillis));
        return this;
    }

    /**
     * Note: Can override any config defined in another method which is related to connection manager config
     *
     * @param defaultConnectionManagerBuilderCustomizer the consumer instance
     *                                                  which provides
     *                                                  {@link org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder} to customize default connection manager
     * @return ClientBuilder instance
     */
    public ClientBuilder addDefaultConnectionManagerBuilderCustomizer(Consumer<PoolingHttpClientConnectionManagerBuilder> defaultConnectionManagerBuilderCustomizer) {
        if (defaultConnectionManagerBuilderCustomizers == null) {
            defaultConnectionManagerBuilderCustomizers = new LinkedHashSet<>();
        }
        defaultConnectionManagerBuilderCustomizers.add(defaultConnectionManagerBuilderCustomizer);
        return this;
    }

    /**
     * @param defaultConnectionConfigBuilderConsumer the consumer instance
     *                                               which provides
     *                                               {@link org.apache.hc.client5.http.config.ConnectionConfig.Builder} to customize default request config
     * @return ClientBuilder instance
     */
    public ClientBuilder addDefaultConnectionConfigCustomizer(Consumer<ConnectionConfig.Builder> defaultConnectionConfigBuilderConsumer) {
        if (defaultConnectionConfigBuilderCustomizers == null) {
            defaultConnectionConfigBuilderCustomizers = new LinkedHashSet<>();
        }
        defaultConnectionConfigBuilderCustomizers.add(defaultConnectionConfigBuilderConsumer);
        return this;
    }

    /**
     * The method takes the {@link java.util.function.Consumer} instance
     * which gives the {@link org.apache.hc.client5.http.impl.classic.HttpClientBuilder} instance to customize
     * the {@link org.apache.hc.client5.http.impl.classic.CloseableHttpClient} before the http-request is built
     *
     * @param httpClientCustomizer consumer instance
     * @return ClientBuilder instance
     */
    public ClientBuilder addHttpClientCustomizer(Consumer<HttpClientBuilder> httpClientCustomizer) {
        if (httpClientBuilderCustomizers == null) {
            httpClientBuilderCustomizers = new LinkedHashSet<>();
        }
        httpClientBuilderCustomizers.add(httpClientCustomizer);
        return this;
    }

    /**
     * @param maxPoolSize see documentation of {@link com.jsunsoft.http.HostPoolConfig#setMaxPoolSize(int)}
     * @return ClientBuilder instance
     */
    public ClientBuilder setMaxPoolSize(int maxPoolSize) {
        this.hostPoolConfig.setMaxPoolSize(maxPoolSize);
        return this;
    }

    /**
     * @param defaultMaxPoolSizePerRoute see documentation of {@link com.jsunsoft.http.HostPoolConfig#setDefaultMaxPoolSizePerRoute(int)}
     * @return ClientBuilder instance
     */
    public ClientBuilder setDefaultMaxPoolSizePerRoute(int defaultMaxPoolSizePerRoute) {
        this.hostPoolConfig.setDefaultMaxPoolSizePerRoute(defaultMaxPoolSizePerRoute);
        return this;
    }

    /**
     * Set the connection pool default max size of concurrent connections to a specific route
     *
     * @param httpHost         httpHost
     * @param maxRoutePoolSize maxRoutePoolSize
     * @return ClientBuilder instance
     */
    public ClientBuilder setMaxPoolSizePerRoute(HttpHost httpHost, int maxRoutePoolSize) {
        hostPoolConfig.setMaxPoolSizePerRoute(httpHost, maxRoutePoolSize);
        return this;
    }

    /**
     * @return ClientBuilder instance
     * @see org.apache.hc.client5.http.impl.DefaultRedirectStrategy
     */
    public ClientBuilder enableDefaultRedirectStrategy() {
        this.redirectStrategy = DefaultRedirectStrategy.INSTANCE;
        return this;
    }

    /**
     * <p>By default disabled.
     *
     * @param redirectStrategy RedirectStrategy instance
     * @return ClientBuilder instance
     * @see org.apache.hc.client5.http.protocol.RedirectStrategy
     */
    public ClientBuilder redirectStrategy(RedirectStrategy redirectStrategy) {
        this.redirectStrategy = redirectStrategy;
        return this;
    }

    /**
     * Header needs to be the same for all requests which go through the built CloseableHttpClient
     *
     * @param name  name of header. Can't be null
     * @param value value of header
     * @return ClientBuilder instance
     */
    public ClientBuilder addDefaultHeader(String name, String value) {
        ArgsCheck.notNull(name, "name");
        addDefaultHeader(new BasicHeader(name, value));
        return this;
    }

    /**
     * Header needs to be the same for all requests which go through the built CloseableHttpClient
     *
     * @param header header instance. Can't be null
     * @return ClientBuilder instance
     */
    public ClientBuilder addDefaultHeader(Header header) {
        ArgsCheck.notNull(header, "header");

        if (defaultHeaders == null) {
            defaultHeaders = new ArrayList<>();
        }
        defaultHeaders.add(new BasicHeader(header.getName(), header.getValue()));
        return this;
    }

    /**
     * Headers need to be the same for all requests which go through the built CloseableHttpClient
     *
     * @param headers varargs of headers. Can't be null
     * @return ClientBuilder instance
     */
    public ClientBuilder addDefaultHeaders(Header... headers) {
        ArgsCheck.notNull(headers, "headers");
        Arrays.stream(headers).forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Headers need to be the same for all requests which go through the built CloseableHttpClient
     *
     * @param headers collections of headers
     * @return ClientBuilder instance
     */
    public ClientBuilder addDefaultHeaders(Collection<? extends Header> headers) {
        ArgsCheck.notNull(headers, "headers");

        headers.forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Sets content type to header
     *
     * @param contentType content type of request header
     * @return ClientBuilder instance
     */
    public ClientBuilder addContentType(ContentType contentType) {
        addDefaultHeader(CONTENT_TYPE, contentType.toString());
        return this;
    }

    /**
     * Added proxy host. By default is null.
     * If has proxy instance method {@link #useDefaultProxy()} will be ignored
     *
     * @param proxy {@link org.apache.hc.core5.http.HttpHost} instance to proxy
     * @return ClientBuilder instance
     */
    public ClientBuilder proxy(HttpHost proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Added proxy by proxyUri. By default is null.
     * If has proxy instance method {@link #useDefaultProxy()} will be ignored.
     *
     * @param proxyUri {@link java.net.URI} instance to proxy
     * @return ClientBuilder instance
     */
    public ClientBuilder proxy(URI proxyUri) {
        ArgsCheck.notNull(proxyUri, "proxyUri");
        if (proxyUri.getUserInfo() != null) {
            // userinfo in the URI (e.g. http://user:pass@proxy.corp) would silently be discarded by
            // HttpHost — the user almost certainly means "authenticate to the proxy with these
            // credentials," but Apache HC5's proxy auth path is HttpClientContext + a
            // CredentialsProvider, not URI userinfo. Fail loudly instead of silently dropping it.
            throw new IllegalArgumentException(
                    "Proxy URI must not contain userinfo (username:password). For authenticated " +
                            "proxies, supply credentials via Apache HC5's BasicCredentialsProvider on the " +
                            "HttpClientContext. Got: '" + proxyUri.getRawUserInfo() + "@" + proxyUri.getHost() + "'");
        }
        return proxy(
                new HttpHost(
                        proxyUri.getScheme(),
                        proxyUri.getHost(),
                        proxyUri.getPort()
                )
        );
    }

    /**
     * @param host host of proxy
     * @param port port of proxy
     * @return ClientBuilder instance
     */
    public ClientBuilder proxy(String host, int port) {
        return proxy(new HttpHost(host, port));
    }

    /**
     * Instruct HttpClient to use the standard JRE proxy selector to obtain proxy.
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder useDefaultProxy() {
        useDefaultProxy = true;
        return this;
    }

    /**
     * Sets {@link javax.net.ssl.SSLContext}
     *
     * @param sslContext SSLContext instance
     * @return ClientBuilder instance
     */
    public ClientBuilder sslContext(SSLContext sslContext) {
        initializeClientTlsStrategyBuilder();

        clientTlsStrategyBuilder.setSslContext(sslContext);
        return this;
    }

    /**
     * Sets {@link javax.net.ssl.HostnameVerifier}
     *
     * @param hostnameVerifier HostnameVerifier instance
     * @return ClientBuilder instance
     */
    public ClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        initializeClientTlsStrategyBuilder();

        clientTlsStrategyBuilder.setHostnameVerifier(hostnameVerifier);
        return this;
    }

    /**
     * Sets {@link org.apache.hc.client5.http.ssl.HostnameVerificationPolicy}
     *
     * @param hostnameVerificationPolicy HostnameVerificationPolicy instance
     * @return ClientBuilder instance
     */
    public ClientBuilder hostnameVerificationPolicy(HostnameVerificationPolicy hostnameVerificationPolicy) {
        initializeClientTlsStrategyBuilder();

        clientTlsStrategyBuilder.setHostVerificationPolicy(hostnameVerificationPolicy);
        return this;
    }

    /**
     * Restricts the TLS protocol versions the client will negotiate.
     * <p>
     * Useful when you want to enforce TLS 1.2+ or TLS 1.3 only — for example to comply with a
     * security baseline that forbids legacy protocols. Without calling this method, the JVM's
     * configured TLS versions are used.
     * <pre>{@code
     *     ClientBuilder.create()
     *             .setTlsVersions("TLSv1.3", "TLSv1.2")
     *             .build();
     * }</pre>
     *
     * @param tlsVersions one or more TLS protocol identifiers (e.g. {@code "TLSv1.3"},
     *                    {@code "TLSv1.2"}); must not be {@code null}.
     * @return ClientBuilder instance
     */
    public ClientBuilder setTlsVersions(String... tlsVersions) {
        ArgsCheck.notNull(tlsVersions, "tlsVersions");
        initializeClientTlsStrategyBuilder();
        clientTlsStrategyBuilder.setTlsVersions(tlsVersions);
        return this;
    }

    /**
     * Restricts the TLS cipher suites the client will offer / accept.
     * <p>
     * Use to opt out of weak / deprecated suites (legacy RC4, DES, 3DES, NULL ciphers) when
     * talking to servers that still advertise them. Pass cipher suite names exactly as the JVM
     * recognises them (see {@link javax.net.ssl.SSLContext#getDefaultSSLParameters()
     * SSLParameters.getCipherSuites()}). Without calling this method, the JVM defaults are used.
     *
     * @param cipherSuites one or more cipher suite names; must not be {@code null}.
     * @return ClientBuilder instance
     */
    public ClientBuilder setCipherSuites(String... cipherSuites) {
        ArgsCheck.notNull(cipherSuites, "cipherSuites");
        initializeClientTlsStrategyBuilder();
        clientTlsStrategyBuilder.setCiphers(cipherSuites);
        return this;
    }

    /**
     * Caps the number of HTTP/1.1 headers the client will accept in a single message. Bounds
     * memory consumption when talking to a hostile or buggy server that emits an unbounded
     * header list — {@link HttpRequestBuilder#setMaxResponseBodySizeBytes complementing the body-size cap} which
     * only protects the body, not the head.
     * <p>
     * Default: Apache HC5's built-in default (currently {@code -1}, unlimited). Pass a
     * non-negative value to enable; common production caps are {@code 64} or {@code 100}.
     *
     * @param maxHeaderCount maximum number of header fields per message; negative means unbounded
     * @return ClientBuilder instance
     * @see Http1Config.Builder#setMaxHeaderCount(int)
     */
    @Beta
    public ClientBuilder setMaxHeaderCount(int maxHeaderCount) {
        this.maxResponseHeaderCount = maxHeaderCount;
        return this;
    }

    /**
     * Caps the maximum length of a single HTTP/1.1 line — used as the upper bound for the status
     * line and any individual header. Pairs with {@link #setMaxHeaderCount} to bound the worst
     * case "malicious server fills RAM via headers" attack: total head size ≤
     * {@code maxHeaderCount × maxLineLength}.
     * <p>
     * Default: Apache HC5's built-in default (currently {@code -1}, unlimited). Production caps
     * around {@code 8192} (8 KiB) are typical; many reverse proxies default to {@code 4096}.
     *
     * @param maxLineLength maximum bytes per line; negative means unbounded
     * @return ClientBuilder instance
     * @see Http1Config.Builder#setMaxLineLength(int)
     */
    @Beta
    public ClientBuilder setMaxLineLength(int maxLineLength) {
        this.maxResponseLineLength = maxLineLength;
        return this;
    }

    /**
     * By default, the {@link HttpClientBuilder#disableCookieManagement} called.
     * This method will prevent the call.
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder enableCookieManagement() {
        cookieManagementEnabled = true;
        return this;
    }

    /**
     * By default, the underlying {@link HttpClientBuilder#disableAutomaticRetries} is called and
     * Apache HC5's built-in retry-on-IOException logic is suppressed. Calling this method opts
     * back into Apache's default retry behavior.
     * <p>
     * Note: this is the underlying-client retry mechanism. For richer policy (status-code
     * predicates, per-attempt header rewriting, exponential backoff, idempotency gating) prefer
     * the higher-level {@link RetryContext} API exposed via
     * {@link HttpRequest#retryableTarget(java.net.URI, RetryContext)}.
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder enableAutomaticRetries() {
        automaticRetriesEnabled = true;
        return this;
    }

    /**
     * INSECURE: trust any TLS certificate (disables certificate validation).
     * <p>
     * Use only for testing or in controlled environments.
     *
     * @return ClientBuilder instance
     * @throws HttpRequestBuildException when can't build ssl.
     */
    public ClientBuilder trustAllCertificates() {
        SSLContext sslContext;

        LOGGER.warn("Configuring HttpClient to trust all TLS certificates. This is INSECURE and should only be used in controlled environments.");

        try {
            sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();

        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new HttpRequestBuildException(e);
        }

        return sslContext(sslContext);
    }

    /**
     * INSECURE: accept any hostname during TLS handshake (disables hostname verification).
     * <p>
     * Use only for testing or in controlled environments.
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder trustAllHosts() {
        return hostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .hostnameVerificationPolicy(HostnameVerificationPolicy.CLIENT);
    }

    /**
     * Build CloseableHttpClient
     *
     * @return {@link  org.apache.hc.client5.http.impl.classic.CloseableHttpClient} instance by build parameters
     */
    public CloseableHttpClient build() {
        return buildWithResources().getClient();
    }

    /**
     * Builds a {@link CloseableHttpClient} and exposes associated resources for proper cleanup.
     * <p>
     * This is useful when you want to close both the client and its connection manager explicitly.
     *
     * <pre>
     * try (ClientBuilder.HttpClientWithResources res = ClientBuilder.create().buildWithResources()) {
     *     CloseableHttpClient client = res.getClient();
     * }
     * </pre>
     * Note that the connection manager is closed only if it is not marked as shared.
     * The close method of the HttpClientWithResources will call only close for underlined Client, not Connection Manager.
     *
     * @return wrapper that closes client and connection manager
     */
    @Beta
    HttpClientWithResourcesWrapper buildWithResources() {
        // Snapshot the configured timeouts/setters and apply customizers to a FRESH builder per
        // build(). Otherwise calling build() twice would re-apply the customizers to the same
        // persistent Builder field, compounding any non-idempotent state changes (e.g. a
        // customizer that does b.setMaxRedirects(b.getMaxRedirects() + 1) would increment
        // unboundedly, and a customizer that re-registers an interceptor would duplicate it).
        RequestConfig.Builder freshRequestConfigBuilder = RequestConfig.copy(defaultRequestConfigBuilder.build());
        if (defaultRequestConfigBuilderCustomizers != null) {
            defaultRequestConfigBuilderCustomizers.forEach(customizer -> customizer.accept(freshRequestConfigBuilder));
        }
        RequestConfig requestConfig = freshRequestConfigBuilder.build();

        ConnectionConfig.Builder freshConnectionConfigBuilder = ConnectionConfig.copy(defaultConnectionConfigBuilder.build());
        if (defaultConnectionConfigBuilderCustomizers != null) {
            defaultConnectionConfigBuilderCustomizers.forEach(customizer -> customizer.accept(freshConnectionConfigBuilder));
        }
        ConnectionConfig connectionConfig = freshConnectionConfigBuilder.build();

        PoolingHttpClientConnectionManagerBuilder cmBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(hostPoolConfig.getDefaultMaxPoolSizePerRoute())
                .setMaxConnTotal(hostPoolConfig.getMaxPoolSize())
                .setDefaultConnectionConfig(connectionConfig);

        if (clientTlsStrategyBuilder != null) {
            cmBuilder.setTlsSocketStrategy(clientTlsStrategyBuilder.buildClassic());
        }

        // Wire HTTP/1.1 head-size limits if either knob was set. Apache HC5 plumbs Http1Config
        // through a ManagedHttpClientConnectionFactory (the connection manager itself doesn't
        // accept Http1Config directly).
        if (maxResponseHeaderCount >= 0 || maxResponseLineLength >= 0) {
            Http1Config.Builder http1Builder = Http1Config.custom();
            if (maxResponseHeaderCount >= 0) {
                http1Builder.setMaxHeaderCount(maxResponseHeaderCount);
            }
            if (maxResponseLineLength >= 0) {
                http1Builder.setMaxLineLength(maxResponseLineLength);
            }
            cmBuilder.setConnectionFactory(
                    ManagedHttpClientConnectionFactory.builder()
                            .http1Config(http1Builder.build())
                            .build());
        }

        if (defaultConnectionManagerBuilderCustomizers != null) {
            defaultConnectionManagerBuilderCustomizers
                    .forEach(defaultConnectionManagerBuilderCustomizer ->
                            defaultConnectionManagerBuilderCustomizer.accept(cmBuilder)
                    );
        }

        PoolingHttpClientConnectionManager connectionManager = cmBuilder
                .build();


        hostPoolConfig.getHttpHostToMaxPoolSize().forEach((httpHost, maxPerRoute) -> {
            HttpRoute httpRoute = new HttpRoute(httpHost);
            connectionManager.setMaxPerRoute(httpRoute, maxPerRoute);
        });

        HttpRoutePlanner routePlanner = null;

        if (proxy != null) {
            routePlanner = new DefaultProxyRoutePlanner(proxy);
        } else if (useDefaultProxy) {
            routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());
        }

        HttpClientBuilder clientBuilder =
                HttpClientBuilder.create()
                        .setDefaultRequestConfig(requestConfig)
                        .setConnectionManager(connectionManager);

        if (!cookieManagementEnabled) {
            clientBuilder.disableCookieManagement();
        }

        if (!automaticRetriesEnabled) {
            clientBuilder.disableAutomaticRetries();
        }

        if (routePlanner != null) {
            clientBuilder.setRoutePlanner(routePlanner);
        }

        if (defaultHeaders != null && !defaultHeaders.isEmpty()) {
            clientBuilder.setDefaultHeaders(defaultHeaders);
        }

        if (redirectStrategy == null) {
            clientBuilder.disableRedirectHandling();
        } else {
            clientBuilder.setRedirectStrategy(redirectStrategy);
        }

        if (httpClientBuilderCustomizers != null) {
            httpClientBuilderCustomizers.forEach(httpClientBuilderConsumer -> httpClientBuilderConsumer.accept(clientBuilder));
        }


        return new HttpClientWithResourcesWrapper(
                clientBuilder.build(),
                connectionManager
        );
    }

    private void initializeClientTlsStrategyBuilder() {
        if (clientTlsStrategyBuilder == null) {
            clientTlsStrategyBuilder = ClientTlsStrategyBuilder.create();
        }
    }

    @Beta
    static class HttpClientWithResourcesWrapper implements AutoCloseable {
        private final CloseableHttpClient client;
        private final HttpClientConnectionManager connectionManager;

        HttpClientWithResourcesWrapper(CloseableHttpClient client, HttpClientConnectionManager connectionManager) {
            this.client = client;
            this.connectionManager = connectionManager;
        }

        public CloseableHttpClient getClient() {
            return client;
        }

        public HttpClientConnectionManager getConnectionManager() {
            return connectionManager;
        }

        @Override
        public void close() throws IOException {
            //closes also underlined connection manager if connection manager not marked as shared
            client.close();
        }
    }
}
