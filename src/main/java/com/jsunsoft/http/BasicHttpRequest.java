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

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.Args;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.*;

/**
 * This class design one instance to one <b>URI</b>
 * <p>
 * BasicHttpRequest objects are immutable they can be shared.
 *
 * @param <T> Type of expected successful response
 */
final class BasicHttpRequest<T> implements HttpRequest<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicHttpRequest.class);
    private static final org.apache.http.NameValuePair[] EMPTY_APACHE_NAME_VALUE_PAIRS = new org.apache.http.NameValuePair[0];
    private static final String COOKIE = "Cookie";

    private final String httpMethod;
    private final URI uri;
    private final Type type;
    private final ContentType contentTypeOfBody;
    private final CloseableHttpClient closeableHttpClient;
    private final ResponseDeserializer<T> responseDeserializer;
    private final Charset charset;
    private final Supplier<String> cookiesSupplier;
    private final Collection<NameValuePair> defaultRequestParameters;
    private final PoolingHttpClientConnectionManager connectionManager;

    BasicHttpRequest(String httpMethod,
                     URI uri,
                     Type type,
                     ContentType contentTypeOfBody,
                     CloseableHttpClient closeableHttpClient,
                     ResponseDeserializer<T> responseDeserializer,
                     Charset charset,
                     Supplier<String> cookiesSupplier,
                     Collection<NameValuePair> defaultRequestParameters,
                     PoolingHttpClientConnectionManager connectionManager
    ) {
        this.httpMethod = ArgsCheck.notNull(httpMethod, "httpMethod");
        this.uri = ArgsCheck.notNull(uri, "uri");
        this.type = ArgsCheck.notNull(type, "type");
        this.contentTypeOfBody = ArgsCheck.notNull(contentTypeOfBody, "contentTypeOfBody");
        this.closeableHttpClient = ArgsCheck.notNull(closeableHttpClient, "closeableHttpClient");
        this.responseDeserializer = ArgsCheck.notNull(responseDeserializer, "responseDeserializer");
        this.charset = charset;
        this.cookiesSupplier = cookiesSupplier;
        this.defaultRequestParameters = Collections.unmodifiableCollection(ArgsCheck.notNull(defaultRequestParameters, "defaultRequestParameters"));
        this.connectionManager = ArgsCheck.notNull(connectionManager, "connectionManager");
    }

    /**
     * {@inheritDoc}
     */
    public ResponseHandler<T> executeWithBody(String payload) {
        LOGGER.debug("Started executing with body. Uri: {}", uri);
        ArgsCheck.notNull(payload, "payload");
        RequestBuilder requestBuilder = RequestBuilder.create(httpMethod)
                .setUri(uri)
                .addHeader(CONTENT_TYPE, contentTypeOfBody.getMimeType())
                .setEntity(new StringEntity(payload, contentTypeOfBody));
        return execute(requestBuilder);
    }

    /**
     * {@inheritDoc}
     */
    public ResponseHandler<T> executeWithQuery(String queryString, String characterEncoding) {
        ArgsCheck.notNull(queryString, "queryString");
        ArgsCheck.notNull(characterEncoding, "characterEncoding");
        try {
            queryString = URLDecoder.decode(queryString, characterEncoding);
        } catch (UnsupportedEncodingException e) {
            LOGGER.debug("Unsupported encoding [" + characterEncoding + "], for [" + queryString + ']', e);
            throw new UnsupportedCharsetException(characterEncoding);
        }
        LOGGER.trace("Query string to uri: [{}]: is: [{}]", uri, queryString);
        NameValuePair[] params = Arrays.stream(queryString.split("&"))
                .map(s -> s.split("=")).filter(s -> s.length == 2)
                .map(s -> new NameValuePairImpl(s[0], s[1]))
                .toArray(NameValuePair[]::new);
        return execute(params);
    }

    /**
     * {@inheritDoc}
     */
    public ResponseHandler<T> execute(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return execute(new BasicNameValuePair(name, value));
    }

    /**
     * {@inheritDoc}
     */
    public ResponseHandler<T> execute() {
        return execute(EMPTY_APACHE_NAME_VALUE_PAIRS);
    }

    /**
     * {@inheritDoc}
     */
    public ResponseHandler<T> execute(String... nameValues) {
        int nameValuesLength = ArgsCheck.notNull(nameValues, "nameValues").length;
        Args.check(nameValuesLength != 0, "Length of parameter can't be ZERO");
        Args.check(nameValuesLength % 2 != 0, "Length of nameValues can't be odd");

        int end = nameValuesLength - 2;
        NameValuePair[] nameValuePairs = new NameValuePair[nameValuesLength / 2];

        int k = 0;
        for (int i = 0; i <= end; i += 2) {
            nameValuePairs[k++] = new BasicNameValuePair(nameValues[i], nameValues[i + 1]);
        }

        return execute(nameValuePairs);
    }

    /**
     * {@inheritDoc}
     */
    public ResponseHandler<T> execute(com.jsunsoft.http.NameValuePair... params) {
        ArgsCheck.notNull(params, "params");
        return execute((NameValuePair[]) Arrays.stream(params).map(nvp -> new BasicNameValuePair(nvp.getName(), nvp.getValue())).toArray());
    }

    /**
     * Sends request
     *
     * @param params parameters to send
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a <b>503</b>.
     * If failed deserialization of response body status code is a <b>502</b>
     * @throws NullPointerException when param params is null
     */
    private ResponseHandler<T> execute(NameValuePair... params) {
        LOGGER.debug("Started executing. Uri: {}", uri);
        ArgsCheck.notNull(params, "params");
        RequestBuilder requestBuilder = RequestBuilder.create(httpMethod).setUri(uri).addParameters(params);
        return execute(requestBuilder);
    }


    /**
     * {@inheritDoc}
     */
    public ResponseHandler<T> execute(Map<String, String> params) {
        ArgsCheck.notNull(params, "params");
        return execute(params.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                .toArray(NameValuePair[]::new));
    }

    private ResponseHandler<T> execute(RequestBuilder requestBuilder) {
        long startTime = System.currentTimeMillis();

        HttpUriRequest request = resolveRequest(requestBuilder);
        ResponseHandler<T> result;

        try (CloseableHttpResponse response = closeableHttpClient.execute(request)) {

            int responseCode = response.getStatusLine().getStatusCode();
            HttpEntity httpEntity = response.getEntity();
            LOGGER.info("Response code from uri: [{}] is {}", uri, responseCode);
            boolean hasBody = HttpRequestUtils.hasBody(responseCode);

            T content = null;
            String failedMessage = null;
            if (hasBody && httpEntity == null) {
                failedMessage = "Response entity is null";
                LOGGER.debug("{} .Uri: [{}]. Status code: {}", failedMessage, uri, responseCode);
                responseCode = SC_BAD_GATEWAY;
            } else {
                try {
                    if (!HttpRequestUtils.isVoidType(type) && hasBody && HttpRequestUtils.isSuccess(responseCode)) {
                        content = responseDeserializer.deserialize(new BasicResponseContext(httpEntity));
                        LOGGER.trace("Result of Uri: [{}] is {}", uri, content);
                    } else if (HttpRequestUtils.isNonSuccess(responseCode)) {
                        failedMessage = responseDeserializer.deserializeFailure(new BasicResponseContext(httpEntity));
                        String logMsg = "Unexpected Response. Url: [{}] Status code: {}, Error message: {}";
                        if (responseCode == SC_BAD_REQUEST) {
                            LOGGER.warn(logMsg, uri, responseCode, failedMessage);
                        } else {
                            LOGGER.debug(logMsg, uri, responseCode, failedMessage);
                        }
                    }
                } catch (ResponseDeserializeException e) {
                    failedMessage = "Response deserialization failed. Cannot deserialize response to: [" + type + ']';
                    LOGGER.debug(failedMessage + ". Uri: [" + uri + "]. Status code: " + responseCode, e);
                    responseCode = SC_BAD_GATEWAY;
                } catch (IOException e) {
                    failedMessage = "Get content from response failed";
                    LOGGER.debug("Stream could not be created. Uri: [" + uri + "]. Status code: " + responseCode, e);
                    responseCode = SC_SERVICE_UNAVAILABLE;
                }
            }
            com.jsunsoft.http.ContentType responseContentType = com.jsunsoft.http.ContentType.create(httpEntity);
            EntityUtils.consumeQuietly(httpEntity);

            result = new ResponseHandler<>(content, responseCode, failedMessage, type, responseContentType, uri);

        } catch (ConnectionPoolTimeoutException e) {
            result = new ResponseHandler<>(this, BasicConnectionFailureType.CONNECTION_POOL_IS_EMPTY);
            LOGGER.debug("Connection pool is empty for request on uri: [" + uri + "]. Status code: " + result.getStatusCode(), e);
        } catch (SocketTimeoutException | NoHttpResponseException e) {
            //todo support retry when NoHttpResponseException
            result = new ResponseHandler<>(this, BasicConnectionFailureType.REMOTE_SERVER_HIGH_LOADED);
            LOGGER.debug("Server on uri: [" + uri + "] is high loaded. Status code: " + result.getStatusCode(), e);
        } catch (ConnectTimeoutException e) {
            result = new ResponseHandler<>(this, BasicConnectionFailureType.CONNECT_TIMEOUT_EXPIRED);
            LOGGER.debug("HttpRequest is unable to establish a connection with the: [" + uri + "] within the given period of time. Status code: " + result.getStatusCode(), e);
        } catch (HttpHostConnectException e) {
            result = new ResponseHandler<>(this, BasicConnectionFailureType.REMOTE_SERVER_IS_DOWN);
            LOGGER.debug("Server on uri: [" + uri + "] is down. Status code: " + result.getStatusCode(), e);
        } catch (IOException e) {
            result = new ResponseHandler<>(this, BasicConnectionFailureType.IO);
            LOGGER.debug("Connection was aborted for request on uri: [" + uri + "]. Status code: " + result.getStatusCode(), e);
        }

        LOGGER.debug("Executing of uri: [{}] completed. Time {}", uri, HttpRequestUtils.humanTime(startTime));
        LOGGER.trace("Executed result: {}", result);
        return result;
    }

    private HttpUriRequest resolveRequest(RequestBuilder requestBuilder) {

        if (cookiesSupplier != null) {
            requestBuilder.addHeader(COOKIE, cookiesSupplier.get());
        }

        if (!defaultRequestParameters.isEmpty()) {
            defaultRequestParameters.forEach(requestBuilder::addParameter);
        }

        if (charset != null) {
            requestBuilder.setCharset(charset);
        }
        return requestBuilder.build();
    }

    public HttpMethod getHttpMethod() {
        return HttpMethod.valueOf(httpMethod);
    }

    public URI getUri() {
        return uri;
    }

    Type getType() {
        return type;
    }

    /**
     * @param postfix postfix to add to uri. Should not be null.
     * @return Returns a copy of this instance with the uri by added postfix.
     */
    public BasicHttpRequest<T> addUriPostfix(String postfix) {
        return changeUri(URI.create(uri.toString() + postfix));
    }

    /**
     * @param newUri the new uri to request. Should not be null.
     * @return Returns a copy of this BasicHttpRequest instance with the specified uri of newUri.
     */
    public BasicHttpRequest<T> changeUri(URI newUri) {
        ArgsCheck.notNull(newUri, "newUri");
        return new BasicHttpRequest<>(
                httpMethod,
                newUri.normalize(),
                type,
                contentTypeOfBody,
                closeableHttpClient,
                responseDeserializer,
                charset,
                cookiesSupplier,
                defaultRequestParameters,
                connectionManager
        );
    }

    PoolingHttpClientConnectionManager getConnectionManager() {
        return connectionManager;
    }
}
