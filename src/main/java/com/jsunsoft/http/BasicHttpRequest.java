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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.NoHttpResponseException;
import org.apache.http.StatusLine;
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
import org.apache.http.util.EntityUtils;

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
import java.util.function.Supplier;

import static com.jsunsoft.http.BasicConnectionFailureType.*;
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
    private static final Log LOGGER = LogFactory.getLog(BasicHttpRequest.class);
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
    @Override
    public ResponseHandler<T> executeWithBody(String payload) {
        LOGGER.debug("Started executing with body. Uri: " + uri);

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
    @Override
    public ResponseHandler<T> executeWithQuery(String queryString, String characterEncoding) {
        ArgsCheck.notNull(queryString, "queryString");
        ArgsCheck.notNull(characterEncoding, "characterEncoding");
        try {
            queryString = URLDecoder.decode(queryString, characterEncoding);
        } catch (UnsupportedEncodingException e) {
            LOGGER.debug("Unsupported encoding [" + characterEncoding + "], for [" + queryString + ']', e);
            throw new UnsupportedCharsetException(characterEncoding);
        }
        LOGGER.trace("Query string to uri: [" + uri + "]: is: [" + queryString + ']');

        NameValuePair[] params = Arrays.stream(queryString.split("&"))
                .map(s -> s.split("=")).filter(s -> s.length == 2)
                .map(s -> new BasicNameValuePair(s[0], s[1]))
                .toArray(NameValuePair[]::new);
        return execute(params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseHandler<T> execute(NameValuePair... params) {
        LOGGER.debug("Started executing. Uri: " + uri);
        ArgsCheck.notNull(params, "params");
        RequestBuilder requestBuilder = RequestBuilder.create(httpMethod).setUri(uri).addParameters(params);
        return execute(requestBuilder);
    }

    private ResponseHandler<T> execute(RequestBuilder requestBuilder) {
        long startTime = System.currentTimeMillis();

        HttpUriRequest request = resolveRequest(requestBuilder);
        ResponseHandler<T> result;
        StatusLine statusLine;

        try (CloseableHttpResponse response = closeableHttpClient.execute(request)) {
            statusLine = response.getStatusLine();
            if (statusLine == null) {
                throw new IllegalStateException("StatusLine is null.");
            }

            int responseCode = statusLine.getStatusCode();
            HttpEntity httpEntity = response.getEntity();
            LOGGER.info("Response code from uri: [" + uri + "] is " + responseCode);

            boolean hasBody = HttpRequestUtils.hasBody(responseCode);

            T content = null;
            String failedMessage = null;
            if (hasBody && httpEntity == null) {
                failedMessage = "Response entity is null";
                LOGGER.debug(failedMessage + " .Uri: [" + uri + "]. Status code: " + responseCode);
                responseCode = SC_BAD_GATEWAY;
            } else {
                try {
                    if (!HttpRequestUtils.isVoidType(type) && hasBody && HttpRequestUtils.isSuccess(responseCode)) {
                        content = responseDeserializer.deserialize(new BasicResponseContext(response));
                        LOGGER.trace("Result of Uri: [" + uri + "] is " + content);
                    } else if (HttpRequestUtils.isNonSuccess(responseCode)) {
                        failedMessage = responseDeserializer.deserializeFailure(new BasicResponseContext(response));
                        String logMsg = "Unexpected Response. Url: [" + uri + "] Status code: " + responseCode + ", Error message: " + failedMessage;
                        if (responseCode == SC_BAD_REQUEST) {
                            LOGGER.warn(logMsg);
                        } else {
                            LOGGER.debug(logMsg);
                        }
                    }
                } catch (ResponseDeserializeException e) {
                    failedMessage = "Response deserialization failed. Cannot deserialize response to: [" + type + "]." + e;
                    LOGGER.debug(failedMessage + ". Uri: [" + uri + "]. Status code: " + responseCode, e);
                    responseCode = SC_BAD_GATEWAY;
                } catch (IOException e) {
                    failedMessage = "Get content from response failed: " + e;
                    LOGGER.debug("Stream could not be created. Uri: [" + uri + "]. Status code: " + responseCode, e);
                    responseCode = SC_SERVICE_UNAVAILABLE;
                }
            }
            ContentType responseContentType = ContentType.get(httpEntity);
            EntityUtils.consumeQuietly(httpEntity);
            result = new ResponseHandler<>(content, responseCode, failedMessage, type, responseContentType, uri, statusLine);

        } catch (ConnectionPoolTimeoutException e) {
            result = new ResponseHandler<>(null, SC_SERVICE_UNAVAILABLE, "Connection pool is empty. " + e, type, null, uri, CONNECTION_POOL_IS_EMPTY);
            LOGGER.debug("Connection pool is empty for request on uri: [" + uri + "]. Status code: " + result.getStatusCode(), e);
        } catch (SocketTimeoutException | NoHttpResponseException e) {
            //todo support retry when NoHttpResponseException
            result = new ResponseHandler<>(null, SC_SERVICE_UNAVAILABLE, "Server is high loaded. " + e, type, null, uri, REMOTE_SERVER_HIGH_LOADED);
            LOGGER.debug("Server on uri: [" + uri + "] is high loaded. Status code: " + result.getStatusCode(), e);
        } catch (ConnectTimeoutException e) {
            result = new ResponseHandler<>(null, SC_SERVICE_UNAVAILABLE, "HttpRequest is unable to establish a connection within the given period of time. " + e, type, null, uri, CONNECT_TIMEOUT_EXPIRED);
            LOGGER.debug("HttpRequest is unable to establish a connection with the: [" + uri + "] within the given period of time. Status code: " + result.getStatusCode(), e);
        } catch (HttpHostConnectException e) {
            result = new ResponseHandler<>(null, SC_SERVICE_UNAVAILABLE, "Server is down. " + e, type, null, uri, REMOTE_SERVER_IS_DOWN);
            LOGGER.debug("Server on uri: [" + uri + "] is down. Status code: " + result.getStatusCode(), e);
        } catch (IOException e) {
            result = new ResponseHandler<>(null, SC_SERVICE_UNAVAILABLE, "Connection was aborted. " + e, type, null, uri, IO);
            LOGGER.debug("Connection was aborted for request on uri: [" + uri + "]. Status code: " + result.getStatusCode(), e);
        }

        LOGGER.debug("Executing of uri: [" + uri + "] completed. Time " + HttpRequestUtils.humanTime(startTime));
        LOGGER.trace("Executed result: " + result);
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

    @Override
    public HttpMethod getHttpMethod() {
        return HttpMethod.valueOf(httpMethod);
    }

    @Override
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
    //todo
    BasicHttpRequest<T> addUriPostfix(String postfix) {
        return changeUri(URI.create(uri.toString() + postfix));
    }

    /**
     * @param newUri the new uri to request. Should not be null.
     * @return Returns a copy of this BasicHttpRequest instance with the specified uri of newUri.
     */
    //todo
    BasicHttpRequest<T> changeUri(URI newUri) {
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

    /**
     * @param newUri the new uri to request. Should not be null.
     * @return Returns a copy of this BasicHttpRequest instance with the specified uri of newUri.
     */
    //todo
    BasicHttpRequest<T> changeUri(String newUri) {
        ArgsCheck.notNull(newUri, "newUri");
        return changeUri(URI.create(newUri));
    }

    PoolingHttpClientConnectionManager getConnectionManager() {
        return connectionManager;
    }
}
