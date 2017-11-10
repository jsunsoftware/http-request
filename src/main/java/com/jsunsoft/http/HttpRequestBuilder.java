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

package com.jsunsoft.http;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
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
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.Args;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.lang.reflect.Type;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.jsunsoft.http.ArgsCheck.notNull;
import static java.util.Objects.requireNonNull;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

/**
 * Builder to create BasicHttpRequest instance
 * <p>
 * Builder objects are not immutable they can't be shared.
 *
 * @param <T> Type of expected successful response
 */

public final class HttpRequestBuilder<T> {
    private static final String TYPE_CANNOT_BE_VOID = "Type can't be void";

    private final String httpMethod;
    private final URI uri;
    private final Type type;

    private ConnectionConfig connectionConfig;
    private ResponseDeserializer<T> responseDeserializer;
    private DateDeserializeContext dateDeserializeContext;
    private RedirectStrategy redirectStrategy;
    private Collection<Header> headers;
    private Charset charset;
    private Supplier<String> cookiesSupplier;
    private HttpHost proxy;
    private boolean useDefaultProxy;
    private List<NameValuePair> defaultRequestParameters;
    private SSLContext sslContext;
    private HostnameVerifier hostnameVerifier;

    private HttpRequestBuilder(String httpMethod, String uri) {
        this(httpMethod, uri, Void.class);
    }

    private HttpRequestBuilder(String httpMethod, String uri, TypeReference<T> typeReference) {
        this(httpMethod, uri, typeReference.getType());
    }

    private HttpRequestBuilder(String httpMethod, String uri, Type type) {
        this(httpMethod, URI.create(notNull(uri, "uri")), type);
    }

    private HttpRequestBuilder(String httpMethod, URI uri) {
        this(httpMethod, uri, Void.class);
    }

    private HttpRequestBuilder(String httpMethod, URI uri, TypeReference<T> typeReference) {
        this(httpMethod, uri, typeReference.getType());
    }

    private HttpRequestBuilder(String httpMethod, URI uri, Type type) {
        this.httpMethod = notNull(httpMethod, "httpMethod");
        this.uri = notNull(uri, "uri").normalize();
        this.type = notNull(type, "type");
    }

    /**
     * @param responseDeserializer deserializer of response. By default {@link DefaultResponseDeserializer}
     * @return HttpRequestBuilder instance
     * @see ResponseDeserializer
     */
    public HttpRequestBuilder<T> responseDeserializer(ResponseDeserializer<T> responseDeserializer) {
        this.responseDeserializer = notNull(responseDeserializer, "responseDeserializer");
        return this;
    }

    /**
     * @param responseDeserializerSupplier supplier for resolve response deserializer. By default response deserializer is {@link DefaultResponseDeserializer}
     * @return HttpRequestBuilder instance
     * @see ResponseDeserializer
     */
    public HttpRequestBuilder<T> responseDeserializerS(Supplier<ResponseDeserializer<T>> responseDeserializerSupplier) {
        ArgsCheck.notNull(responseDeserializerSupplier, "responseDeserializerFunction");
        this.responseDeserializer = requireNonNull(responseDeserializerSupplier.get(), "get of responseDeserializerSupplier can't return null");
        return this;
    }

    /**
     * @param responseDeserializerFunction function for resolve response deserializer. By default response deserializer is {@link DefaultResponseDeserializer}
     * @return HttpRequestBuilder instance
     * @see ResponseDeserializer
     */
    public HttpRequestBuilder<T> responseDeserializerF(Function<Type, ResponseDeserializer<T>> responseDeserializerFunction) {
        ArgsCheck.notNull(responseDeserializerFunction, "responseDeserializerFunction");
        this.responseDeserializer = requireNonNull(responseDeserializerFunction.apply(type), "apply of responseDeserializerFunction can't return null");
        return this;
    }

    /**
     * Default date patterns to deserialize: for date is 'dd/MM/yyyy', for time is 'HH:mm:ss', for date time is 'dd/MM/yyyy HH:mm:ss'.
     * <p>
     * You can specify your patterns by add {@link DateDeserializeContext} instance
     *
     * @param dateDeserializeContext context to resolve date pattern to deserialize
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> dateDeserializeContext(DateDeserializeContext dateDeserializeContext) {
        this.dateDeserializeContext = dateDeserializeContext;
        return this;
    }

    /**
     * If method is called and {@code requestSupplier} non null enabled cookies including to request. By default disabled.
     *
     * @param cookiesSupplier supplier instance to cookies.
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> includeCookies(Supplier<String> cookiesSupplier) {
        this.cookiesSupplier = cookiesSupplier;
        return this;
    }

    /**
     * Disable cookies including in request. By default disabled.
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> excludeCookies() {
        cookiesSupplier = null;
        return this;
    }

    /**
     * @param connectTimeout see documentation of {@link ConnectionConfig#connectTimeout(int)}
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> connectTimeout(int connectTimeout) {
        initConnectionConfig();
        connectionConfig.connectTimeout(connectTimeout);
        return this;
    }

    /**
     * @param socketTimeOut see documentation of {@link ConnectionConfig#socketTimeOut(int)}
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> socketTimeOut(int socketTimeOut) {
        initConnectionConfig();
        connectionConfig.socketTimeOut(socketTimeOut);
        return this;
    }

    /**
     * @param connectionRequestTimeout see documentation of {@link ConnectionConfig#connectionRequestTimeout(int)}
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> connectionRequestTimeout(int connectionRequestTimeout) {
        initConnectionConfig();
        connectionConfig.connectionRequestTimeout(connectionRequestTimeout);
        return this;
    }

    /**
     * @param maxPoolSize see documentation of {@link ConnectionConfig#maxPoolSize(int)}
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> maxPoolSize(int maxPoolSize) {
        initConnectionConfig();
        connectionConfig.maxPoolSize(maxPoolSize);
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

    public HttpRequestBuilder<T> enableLaxRedirectStrategy() {
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
    public HttpRequestBuilder<T> enableDefaultRedirectStrategy() {
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
    public HttpRequestBuilder<T> redirectStrategy(RedirectStrategy redirectStrategy) {
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
    public HttpRequestBuilder<T> addDefaultHeader(String name, String value) {
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
    public HttpRequestBuilder<T> addDefaultHeader(Header header) {
        if (headers == null) {
            headers = new ArrayList<>();
        }
        headers.add(new BasicHeader(header.getName(), header.getValue()));
        return this;
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param headers varargs of headers
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> addDefaultHeaders(Header... headers) {
        Arrays.stream(headers).forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param headers collections of headers
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> addDefaultHeaders(Collection<? extends Header> headers) {
        headers.forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Sets content type to header
     *
     * @param contentType content type of request header
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> addContentType(ContentType contentType) {
        addDefaultHeader(CONTENT_TYPE, contentType.toString());
        return this;
    }

    /**
     * Configured connection parameters by instance connectionConfig
     * <p>
     * Note: this instance redefines parameters socketTimeOut, connectTimeout, connectionRequestTimeout and maxPoolPerRoute.
     *
     * @param connectionConfig instance to configure
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> connectionConfig(ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
        return this;
    }

    /**
     * Added proxy host. By default is null.
     * If has proxy instance method {@link HttpRequestBuilder#useDefaultProxy()} will be ignored
     *
     * @param proxy {@link HttpHost} instance to proxy
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> proxy(HttpHost proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Added proxy by proxyUri. By default is null.
     * If has proxy instance method {@link HttpRequestBuilder#useDefaultProxy()} will be ignored.
     *
     * @param proxyUri {@link URI} instance to proxy
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> proxy(URI proxyUri) {
        return proxy(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()));
    }

    /**
     * @param host host of proxy
     * @param port port of proxy
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> proxy(String host, int port) {
        return proxy(new HttpHost(host, port));
    }


    /**
     * Instruct HttpClient to use the standard JRE proxy selector to obtain proxy.
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> useDefaultProxy() {
        useDefaultProxy = true;
        return this;
    }

    /**
     * Parameter needs to be add  for all requests.
     *
     * @param name  key
     * @param value value
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> addDefaultRequestParameter(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultRequestParameter(new BasicNameValuePair(name, value));
    }

    /**
     * Parameters needs to be add  for all requests.
     *
     * @param nameValues nameValues
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> addDefaultRequestParameter(NameValuePair... nameValues) {
        int nameValuesLength = ArgsCheck.notNull(nameValues, "nameValues").length;
        Args.check(nameValuesLength != 0, "Length of parameter can't be ZERO");

        return addDefaultRequestParameter(Arrays.asList(nameValues));
    }

    /**
     * Parameters needs to be add  for all requests.
     *
     * @param defaultParameters defaultParameters
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> addDefaultRequestParameter(Map<String, String> defaultParameters) {
        ArgsCheck.notNull(defaultParameters, "defaultParameters");

        Collection<NameValuePair> parameters = defaultParameters.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return addDefaultRequestParameter(parameters);
    }

    /**
     * Parameters needs to be add  for all requests.
     *
     * @param defaultRequestParameters defaultRequestParameters
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> addDefaultRequestParameter(Collection<? extends NameValuePair> defaultRequestParameters) {
        ArgsCheck.notNull(defaultRequestParameters, "defaultRequestParameters");

        if (this.defaultRequestParameters == null) {
            this.defaultRequestParameters = new ArrayList<>();
        }
        Collection<NameValuePair> nameValuePairs = defaultRequestParameters.stream()
                .map(nvp -> new BasicNameValuePair(nvp.getName(), nvp.getValue()))
                .collect(Collectors.toList());
        this.defaultRequestParameters.addAll(nameValuePairs);

        return this;
    }

    /**
     * Sets {@link SSLContext}
     *
     * @param sslContext SSLContext instance
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    /**
     * Sets {@link HostnameVerifier}
     *
     * @param hostnameVerifier HostnameVerifier instance
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder<T> hostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    /**
     * Accept all certificates
     *
     * @return HttpRequestBuilder instance
     * @throws HttpRequestBuildException when can't build ssl.
     */
    public HttpRequestBuilder<T> trustAllCertificates() {
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
    public HttpRequestBuilder<T> trustAllHosts() {
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
    public HttpRequestBuilder<T> basicAuth(String username, String password) {
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
    public HttpRequest<T> build() {
        initConnectionConfig();
        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(connectionConfig.getSocketTimeOut())
                .setConnectTimeout(connectionConfig.getConnectTimeout())
                .setConnectionRequestTimeout(connectionConfig.getConnectionRequestTimeout())
                .build();

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

        HttpHost httpHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        HttpRoute httpRoute = new HttpRoute(httpHost);

        connectionManager.setDefaultMaxPerRoute(connectionConfig.getMaxPoolSize());
        connectionManager.setMaxPerRoute(httpRoute, connectionConfig.getMaxPoolSize());
        connectionManager.setMaxTotal(connectionConfig.getMaxPoolSize());

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

        if (headers != null && !headers.isEmpty()) {
            clientBuilder.setDefaultHeaders(headers);
        }

        if (redirectStrategy == null) {
            clientBuilder.disableRedirectHandling();
        } else {
            clientBuilder.setRedirectStrategy(redirectStrategy);
        }


        CloseableHttpClient closeableHttpClient = clientBuilder.build();

        if (responseDeserializer == null) {
            responseDeserializer = new DefaultResponseDeserializer<>(
                    type,
                    dateDeserializeContext == null ? BasicDateDeserializeContext.DEFAULT : dateDeserializeContext
            );
        }
        return new BasicHttpRequest<>(
                httpMethod,
                uri,
                type,
                closeableHttpClient,
                responseDeserializer,
                charset,
                cookiesSupplier,
                defaultRequestParameters == null ? Collections.emptyList() : defaultRequestParameters,
                connectionManager
        );
    }

    private void initConnectionConfig() {
        if (connectionConfig == null) {
            connectionConfig = ConnectionConfig.create();
        }
    }

    /**
     * Created Builder to build BasicHttpRequest to post request
     *
     * @param uri           uri to request
     * @param typeReference instance to resolve successful response type if type is Generic. Example {@code new TypeReference<java.util.List<String>>(){}}
     * @param <T>           Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> createPost(String uri, TypeReference<T> typeReference) {
        Args.check(!HttpRequestUtils.isVoidType(typeReference.getType()), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(HttpPost.METHOD_NAME, uri, typeReference);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param uri           uri to request
     * @param typeReference instance to resolve successful response type if type is Generic. Example {@code new TypeReference<java.util.List<String>>(){}}
     * @param <T>           Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> createGet(String uri, TypeReference<T> typeReference) {
        Args.check(!HttpRequestUtils.isVoidType(typeReference.getType()), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(HttpGet.METHOD_NAME, uri, typeReference);
    }

    /**
     * Created Builder to build BasicHttpRequest to post request
     *
     * @param uri  uri to request
     * @param type Class type to resolve successful response type. Example {@code String.class}
     * @param <T>  Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> createPost(String uri, Class<T> type) {
        Args.check(!HttpRequestUtils.isVoidType(type), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(HttpPost.METHOD_NAME, uri, type);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param uri  uri to request
     * @param type Class type to resolve successful response type. Example {@code String.class}
     * @param <T>  Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> createGet(String uri, Class<T> type) {
        Args.check(!HttpRequestUtils.isVoidType(type), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(HttpGet.METHOD_NAME, uri, type);
    }

    /**
     * Created Builder to build BasicHttpRequest to post request
     *
     * @param uri uri to request
     * @return Builder instance by type  {@code HttpRequestBuilder<Void>}
     */
    public static HttpRequestBuilder<?> createPost(String uri) {
        return new HttpRequestBuilder<>(HttpPost.METHOD_NAME, uri);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param uri uri to request
     * @return Builder instance by type  {@code HttpRequestBuilder<Void>}
     */
    public static HttpRequestBuilder<?> createGet(String uri) {
        return new HttpRequestBuilder<>(HttpGet.METHOD_NAME, uri);
    }


    /**
     * Created Builder to build BasicHttpRequest to post request
     *
     * @param uri           {@link URI} instance to request
     * @param typeReference instance to resolve successful response type if type is Generic. Example {@code new TypeReference<java.util.List<String>>(){}}
     * @param <T>           Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> createPost(URI uri, TypeReference<T> typeReference) {
        Args.check(!HttpRequestUtils.isVoidType(typeReference.getType()), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(HttpPost.METHOD_NAME, uri, typeReference);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param uri           {@link URI} instance to request
     * @param typeReference instance to resolve successful response type if type is Generic. Example {@code new TypeReference<java.util.List<String>>(){}}
     * @param <T>           Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> createGet(URI uri, TypeReference<T> typeReference) {
        Args.check(!HttpRequestUtils.isVoidType(typeReference.getType()), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(HttpGet.METHOD_NAME, uri, typeReference);
    }

    /**
     * Created Builder to build BasicHttpRequest to post request
     *
     * @param uri  {@link URI} instance to request
     * @param type Class type to resolve successful response type. Example {@code String.class}
     * @param <T>  Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> createPost(URI uri, Class<T> type) {
        Args.check(!HttpRequestUtils.isVoidType(type), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(HttpPost.METHOD_NAME, uri, type);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param uri  {@link URI} instance to request
     * @param type Class type to resolve successful response type. Example {@code String.class}
     * @param <T>  Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> createGet(URI uri, Class<T> type) {
        Args.check(!HttpRequestUtils.isVoidType(type), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(HttpGet.METHOD_NAME, uri, type);
    }

    /**
     * Created Builder to build BasicHttpRequest to post request
     *
     * @param uri {@link URI} instance to request
     * @return Builder instance by type  {@code HttpRequestBuilder<Void>}
     */
    public static HttpRequestBuilder<?> createPost(URI uri) {
        return new HttpRequestBuilder<>(HttpPost.METHOD_NAME, uri);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param uri {@link URI} instance to request
     * @return Builder instance by type  {@code HttpRequestBuilder<Void>}
     */
    public static HttpRequestBuilder<?> createGet(URI uri) {
        return new HttpRequestBuilder<>(HttpGet.METHOD_NAME, uri);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param httpMethod HTTP Method
     * @param uri        uri {@link URI} instance to request
     * @return Builder instance by type  {@code HttpRequestBuilder<Void>}
     */
    public static HttpRequestBuilder<?> create(HttpMethod httpMethod, URI uri) {
        return new HttpRequestBuilder<>(httpMethod.name(), uri);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param httpMethod HTTP Method
     * @param uri        {@link URI} instance to request
     * @param type       Class type to resolve successful response type. Example {@code String.class}
     * @param <T>        Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> create(HttpMethod httpMethod, URI uri, Class<T> type) {
        Args.check(!HttpRequestUtils.isVoidType(type), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(httpMethod.name(), uri, type);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param httpMethod    HTTP Method
     * @param uri           {@link URI} instance to request
     * @param typeReference instance to resolve successful response type if type is Generic. Example {@code new TypeReference<java.util.List<String>>(){}}
     * @param <T>           Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> create(HttpMethod httpMethod, URI uri, TypeReference<T> typeReference) {
        Args.check(!HttpRequestUtils.isVoidType(typeReference.getType()), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(httpMethod.name(), uri, typeReference);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param httpMethod HTTP Method
     * @param uri        uri to request
     * @return Builder instance by type  {@code HttpRequestBuilder<Void>}
     */
    public static HttpRequestBuilder<?> create(HttpMethod httpMethod, String uri) {
        return new HttpRequestBuilder<>(httpMethod.name(), uri);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param httpMethod HTTP Method
     * @param uri        uri to request
     * @param type       Class type to resolve successful response type. Example {@code String.class}
     * @param <T>        Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> create(HttpMethod httpMethod, String uri, Class<T> type) {
        Args.check(!HttpRequestUtils.isVoidType(type), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(httpMethod.name(), uri, type);
    }

    /**
     * Created Builder to build BasicHttpRequest to get request
     *
     * @param httpMethod    HTTP Method
     * @param uri           uri to request
     * @param typeReference instance to resolve successful response type if type is Generic. Example {@code new TypeReference<java.util.List<String>>(){}}
     * @param <T>           Type of expected successful response
     * @return Builder instance
     */
    public static <T> HttpRequestBuilder<T> create(HttpMethod httpMethod, String uri, TypeReference<T> typeReference) {
        Args.check(!HttpRequestUtils.isVoidType(typeReference.getType()), TYPE_CANNOT_BE_VOID);
        return new HttpRequestBuilder<>(httpMethod.name(), uri, typeReference);
    }
}
