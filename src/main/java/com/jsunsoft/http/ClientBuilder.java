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


import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContexts;

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

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

public class ClientBuilder {
    private RedirectStrategy redirectStrategy;

    private RequestConfig.Builder defaultRequestConfigBuilder = RequestConfig.custom()
            .setSocketTimeout(30000)
            .setConnectTimeout(5000)
            .setConnectionRequestTimeout(30000);
    private Collection<Consumer<HttpClientBuilder>> httpClientBuilderCustomizers;
    private Collection<Consumer<RequestConfig.Builder>> defaultRequestConfigBuilderCustomizers;
    private Collection<Header> defaultHeaders;
    private HttpHost proxy;
    private boolean useDefaultProxy;
    private SSLContext sslContext;
    private HostnameVerifier hostnameVerifier;
    private HostPoolConfig hostPoolConfig = HostPoolConfig.create();

    ClientBuilder() {

    }

    /**
     * @param connectTimeout see documentation of {@link RequestConfig.Builder#setConnectTimeout(int)}
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder setConnectTimeout(int connectTimeout) {
        defaultRequestConfigBuilder.setConnectTimeout(connectTimeout);
        return this;
    }

    /**
     * @param socketTimeOut see documentation of {@link RequestConfig.Builder#setSocketTimeout(int)}
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder socketTimeOut(int socketTimeOut) {
        defaultRequestConfigBuilder.setSocketTimeout(socketTimeOut);
        return this;
    }

    /**
     * @param connectionRequestTimeout see documentation of {@link RequestConfig.Builder#setConnectionRequestTimeout(int)}
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder connectionRequestTimeout(int connectionRequestTimeout) {
        defaultRequestConfigBuilder.setConnectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    public ClientBuilder addRequestConfigCustomizer(Consumer<RequestConfig.Builder> defaultRequestConfigBuilderConsumer) {
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
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder addHttpClientCustomizer(Consumer<HttpClientBuilder> httpClientCustomizer) {
        if (httpClientBuilderCustomizers == null) {
            httpClientBuilderCustomizers = new LinkedHashSet<>();
        }
        httpClientBuilderCustomizers.add(httpClientCustomizer);
        return this;
    }

    /**
     * @param maxPoolSize see documentation of {@link HostPoolConfig#maxPoolSize(int)}
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder maxPoolSize(int maxPoolSize) {
        this.hostPoolConfig.maxPoolSize(maxPoolSize);
        return this;
    }

    /**
     * @param defaultMaxPoolSizePerRoute see documentation of {@link HostPoolConfig#setMaxPoolSizePerRoute(int)}
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder setDefaultMaxPoolSizePerRoute(int defaultMaxPoolSizePerRoute) {
        this.hostPoolConfig.setMaxPoolSizePerRoute(defaultMaxPoolSizePerRoute);
        return this;
    }

    public ClientBuilder setMaxPoolSizePerRoute(HttpHost httpHost, int maxRoutePoolSize) {
        hostPoolConfig.setMaxPoolSizePerRoute(httpHost, maxRoutePoolSize);
        return this;
    }

    /**
     * By default, only GET requests resulting in a redirect are automatically followed.
     * If a POST requests is answered with either HTTP 301 Moved Permanently or with 302 Found – the redirect is not automatically followed.
     * <p>
     * If the 301 status code is received in response to a request other than GET or HEAD,
     * the user agent MUST NOT automatically redirect the request unless it can be confirmed by the user,
     * since this might change the conditions under which the request was issued.
     * <p>By default disabled.
     *
     * @return HttpRequestBuilder instance
     * @see LaxRedirectStrategy
     */

    public ClientBuilder enableLaxRedirectStrategy() {
        this.redirectStrategy = LaxRedirectStrategy.INSTANCE;
        return this;
    }

    /**
     * By default, only GET requests resulting in a redirect are automatically followed.
     * If a POST requests is answered with either HTTP 301 Moved Permanently or with 302 Found – the redirect is not automatically followed.
     * <p>
     * If the 301 status code is received in response to a request other than GET or HEAD,
     * the user agent MUST NOT automatically redirect the request unless it can be confirmed by the user,
     * since this might change the conditions under which the request was issued.
     * <p>By default disabled.
     *
     * @return HttpRequestBuilder instance
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
     * @return HttpRequestBuilder instance
     * @see RedirectStrategy
     */
    public ClientBuilder redirectStrategy(RedirectStrategy redirectStrategy) {
        this.redirectStrategy = DefaultRedirectStrategy.INSTANCE;
        return this;
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param name  name of header. Can't be null
     * @param value value of header
     * @return HttpRequestBuilder instance
     */
    ClientBuilder addDefaultHeader(String name, String value) {
        ArgsCheck.notNull(name, "name");
        addDefaultHeader(new BasicHeader(name, value));
        return this;
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param header header instance
     * @return HttpRequestBuilder instance
     */
    ClientBuilder addDefaultHeader(Header header) {
        if (defaultHeaders == null) {
            defaultHeaders = new ArrayList<>();
        }
        defaultHeaders.add(new BasicHeader(header.getName(), header.getValue()));
        return this;
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param headers varargs of headers
     * @return HttpRequestBuilder instance
     */
    ClientBuilder addDefaultHeaders(Header... headers) {
        Arrays.stream(headers).forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param headers collections of headers
     * @return HttpRequestBuilder instance
     */
    ClientBuilder addDefaultHeaders(Collection<? extends Header> headers) {
        headers.forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Sets content type to header
     *
     * @param contentType content type of request header
     * @return HttpRequestBuilder instance
     */
    ClientBuilder addContentType(ContentType contentType) {
        addDefaultHeader(CONTENT_TYPE, contentType.toString());
        return this;
    }

    /**
     * Added proxy host. By default is null.
     * If has proxy instance method {@link ClientBuilder#useDefaultProxy()} will be ignored
     *
     * @param proxy {@link HttpHost} instance to proxy
     * @return HttpRequestBuilder instance
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
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder proxy(URI proxyUri) {
        return proxy(new HttpHost(proxyUri.getHost(), proxyUri.getPort(), proxyUri.getScheme()));
    }

    /**
     * @param host host of proxy
     * @param port port of proxy
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder proxy(String host, int port) {
        return proxy(new HttpHost(host, port));
    }


    /**
     * Instruct HttpClient to use the standard JRE proxy selector to obtain proxy.
     *
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder useDefaultProxy() {
        useDefaultProxy = true;
        return this;
    }


    /**
     * Sets {@link SSLContext}
     *
     * @param sslContext SSLContext instance
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Sets {@link HostnameVerifier}
     *
     * @param hostnameVerifier HostnameVerifier instance
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    /**
     * Accept all certificates
     *
     * @return HttpRequestBuilder instance
     * @throws HttpRequestBuildException when can't build ssl.
     */
    public ClientBuilder trustAllCertificates() {
        try {
            sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, (certificate, authType) -> true).build();
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new HttpRequestBuildException(e);
        }
        return this;
    }

    /**
     * Accept all hosts
     *
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder trustAllHosts() {
        hostnameVerifier = NoopHostnameVerifier.INSTANCE;
        return this;
    }

    /**
     * Basic Authentication - sending the Authorization header.
     *
     * @param username username
     * @param password password
     * @return HttpRequestBuilder instance
     */
    public ClientBuilder basicAuth(String username, String password) {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes());
        String authHeader = "Basic " + new String(encodedAuth);
        return addDefaultHeader(AUTHORIZATION, authHeader);
    }

    /**
     * Build Http request
     *
     * @return {@link HttpRequest} instance by build parameters
     */
    public CloseableHttpClient build() {
        if (defaultRequestConfigBuilderCustomizers != null) {
            defaultRequestConfigBuilderCustomizers.forEach(defaultRequestConfigBuilderConsumer -> defaultRequestConfigBuilderConsumer.accept(defaultRequestConfigBuilder));
        }

        RequestConfig requestConfig = defaultRequestConfigBuilder.build();

        Registry<ConnectionSocketFactory> socketFactoryRegistry = null;
        HttpClientBuilder clientBuilder = HttpClientBuilder.create();

        SSLConnectionSocketFactory sslSocketFactory = null;

        if (sslContext != null) {
            clientBuilder.setSSLContext(sslContext);
            sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
        }

        if (hostnameVerifier != null) {
            clientBuilder.setSSLHostnameVerifier(hostnameVerifier);
            if (sslContext == null) {
                sslSocketFactory = new SSLConnectionSocketFactory(SSLContexts.createDefault(), hostnameVerifier);
            }
        }

        if (sslSocketFactory != null) {
            socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslSocketFactory)
                    .build();
        }

        PoolingHttpClientConnectionManager connectionManager = socketFactoryRegistry == null ?
                new PoolingHttpClientConnectionManager() : new PoolingHttpClientConnectionManager(socketFactoryRegistry);


        connectionManager.setDefaultMaxPerRoute(hostPoolConfig.getMaxPoolSize());
        hostPoolConfig.getHttpHostToMaxPoolSize().forEach((httpHost, maxPerRoute) -> {
            HttpRoute httpRoute = new HttpRoute(httpHost);
            connectionManager.setMaxPerRoute(httpRoute, maxPerRoute);

        });
        connectionManager.setMaxTotal(hostPoolConfig.getMaxPoolSize());

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

    public static ClientBuilder create() {
        return new ClientBuilder();
    }
}
