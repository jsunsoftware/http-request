package com.jsunsoft.http;

/*
 * Copyright 2017 Benik Arakelyan
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


import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
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
 * Builder for {@link CloseableHttpClient}.
 * <p>
 * HttpClients are heavy-weight objects that manage the client-side communication infrastructure.
 * Initialization as well as disposal of a {@link CloseableHttpClient} instance may be a rather expensive operation.
 * It is therefore advised to construct only a small number of {@link CloseableHttpClient} instances in the application.
 */
public class ClientBuilder {
    private RedirectStrategy redirectStrategy;

    private final RequestConfig.Builder defaultRequestConfigBuilder = RequestConfig.custom()
            .setResponseTimeout(Timeout.ofSeconds(30))
            .setConnectTimeout(Timeout.ofSeconds(10))
            .setConnectionRequestTimeout(Timeout.ofSeconds(30));

    private Collection<Consumer<HttpClientBuilder>> httpClientBuilderCustomizers;
    private Collection<Consumer<RequestConfig.Builder>> defaultRequestConfigBuilderCustomizers;
    private Collection<Header> defaultHeaders;
    private HttpHost proxy;
    private boolean useDefaultProxy;

    private SSLConnectionSocketFactoryBuilder sslConnectionSocketFactoryBuilder;

    private final HostPoolConfig hostPoolConfig = HostPoolConfig.create();

    ClientBuilder() {

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
     * Note: Can be overridden by {@linkplain #addDefaultRequestConfigCustomizer}
     *
     * @param connectTimeout The Connection Timeout (http.connection.timeout) – the time to establish the connection with the remote host.
     *
     * @return ClientBuilder instance
     *
     * @see RequestConfig.Builder#setConnectTimeout(Timeout)
     */
    public ClientBuilder setConnectTimeout(Timeout connectTimeout) {
        defaultRequestConfigBuilder.setConnectTimeout(connectTimeout);
        return this;
    }

    /**
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
     *
     * @return ClientBuilder instance
     *
     * @see RequestConfig.Builder#setResponseTimeout(Timeout)
     */
    public ClientBuilder setResponseTimeout(Timeout responseTimeout) {
        defaultRequestConfigBuilder.setResponseTimeout(responseTimeout);
        return this;
    }

    /**
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
     *
     * @return ClientBuilder instance
     *
     * @see RequestConfig.Builder#setConnectionRequestTimeout
     * @see #addDefaultRequestConfigCustomizer
     */
    public ClientBuilder setConnectionRequestTimeout(Timeout connectionRequestTimeout) {
        defaultRequestConfigBuilder.setConnectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    /**
     * @see #setConnectionRequestTimeout
     */
    public ClientBuilder setConnectionRequestTimeout(int connectionRequestTimeout) {
        return setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout));
    }

    /**
     * @param defaultRequestConfigBuilderConsumer the consumer instance which provides {@link RequestConfig.Builder} to customize default request config
     *
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
     * The method takes the {@link Consumer} instance which gives the {@link HttpClientBuilder} instance to customize
     * the {@link CloseableHttpClient} before the http-request is built
     *
     * @param httpClientCustomizer consumer instance
     *
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
     * @param maxPoolSize see documentation of {@link HostPoolConfig#setMaxPoolSize(int)}
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder setMaxPoolSize(int maxPoolSize) {
        this.hostPoolConfig.setMaxPoolSize(maxPoolSize);
        return this;
    }

    /**
     * @param defaultMaxPoolSizePerRoute see documentation of {@link HostPoolConfig#setDefaultMaxPoolSizePerRoute(int)}
     *
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
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder setMaxPoolSizePerRoute(HttpHost httpHost, int maxRoutePoolSize) {
        hostPoolConfig.setMaxPoolSizePerRoute(httpHost, maxRoutePoolSize);
        return this;
    }

    /**
     * @return ClientBuilder instance
     *
     * @see DefaultRedirectStrategy
     */
    public ClientBuilder enableDefaultRedirectStrategy() {
        this.redirectStrategy = DefaultRedirectStrategy.INSTANCE;
        return this;
    }

    /**
     * <p>By default disabled.
     *
     * @param redirectStrategy RedirectStrategy instance
     *
     * @return ClientBuilder instance
     *
     * @see RedirectStrategy
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
     *
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
     *
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
     *
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
     *
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
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder addContentType(ContentType contentType) {
        addDefaultHeader(CONTENT_TYPE, contentType.toString());
        return this;
    }

    /**
     * Added proxy host. By default is null.
     * If has proxy instance method {@link ClientBuilder#useDefaultProxy()} will be ignored
     *
     * @param proxy {@link HttpHost} instance to proxy
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder proxy(HttpHost proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Added proxy by proxyUri. By default is null.
     * If has proxy instance method {@link ClientBuilder#useDefaultProxy()} will be ignored.
     *
     * @param proxyUri {@link URI} instance to proxy
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder proxy(URI proxyUri) {
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
     *
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
     * Sets {@link SSLContext}
     *
     * @param sslContext SSLContext instance
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder sslContext(SSLContext sslContext) {
        initializeSslConnectionSocketFactoryBuilder();

        sslConnectionSocketFactoryBuilder.setSslContext(sslContext);
        return this;
    }

    /**
     * Sets {@link HostnameVerifier}
     *
     * @param hostnameVerifier HostnameVerifier instance
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        initializeSslConnectionSocketFactoryBuilder();

        sslConnectionSocketFactoryBuilder.setHostnameVerifier(hostnameVerifier);
        return this;
    }

    /**
     * Accept all certificates
     *
     * @return ClientBuilder instance
     *
     * @throws HttpRequestBuildException when can't build ssl.
     */
    public ClientBuilder trustAllCertificates() {
        initializeSslConnectionSocketFactoryBuilder();

        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();

            sslConnectionSocketFactoryBuilder.setSslContext(sslContext);

        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new HttpRequestBuildException(e);
        }
        return this;
    }

    /**
     * Accept all hosts
     *
     * @return ClientBuilder instance
     */
    public ClientBuilder trustAllHosts() {
        initializeSslConnectionSocketFactoryBuilder();

        sslConnectionSocketFactoryBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        return this;
    }

    /**
     * Build CloseableHttpClient
     *
     * @return {@link CloseableHttpClient} instance by build parameters
     */
    public CloseableHttpClient build() {
        if (defaultRequestConfigBuilderCustomizers != null) {
            defaultRequestConfigBuilderCustomizers.forEach(defaultRequestConfigBuilderConsumer -> defaultRequestConfigBuilderConsumer.accept(defaultRequestConfigBuilder));
        }

        RequestConfig requestConfig = defaultRequestConfigBuilder.build();

        HttpClientBuilder clientBuilder = HttpClientBuilder.create();

        PoolingHttpClientConnectionManagerBuilder cmBuilder = PoolingHttpClientConnectionManagerBuilder.create();

        if (sslConnectionSocketFactoryBuilder != null) {
            cmBuilder.setSSLSocketFactory(sslConnectionSocketFactoryBuilder.build());
        }

        PoolingHttpClientConnectionManager connectionManager = cmBuilder
                .setMaxConnPerRoute(hostPoolConfig.getDefaultMaxPoolSizePerRoute())
                .setMaxConnTotal(hostPoolConfig.getMaxPoolSize())
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

        clientBuilder
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager)
                .disableCookieManagement()
                .disableAutomaticRetries();


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


        return clientBuilder.build();
    }

    private void initializeSslConnectionSocketFactoryBuilder() {
        if (sslConnectionSocketFactoryBuilder == null) {
            sslConnectionSocketFactoryBuilder = SSLConnectionSocketFactoryBuilder.create();
        }
    }

    public static ClientBuilder create() {
        return new ClientBuilder();
    }
}
