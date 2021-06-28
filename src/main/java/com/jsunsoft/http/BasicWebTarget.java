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


import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

import static com.jsunsoft.http.BasicConnectionFailureType.*;
import static org.apache.http.HttpStatus.*;

class BasicWebTarget implements WebTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicWebTarget.class);


    private final CloseableHttpClient closeableHttpClient;
    private final URIBuilder uriBuilder;
    private final HttpUriRequestBuilder httpUriRequestBuilder = new HttpUriRequestBuilder();
    private final ResponseBodyReaderConfig responseBodyReaderConfig;


    BasicWebTarget(final CloseableHttpClient closeableHttpClient, final URIBuilder uriBuilder, ResponseBodyReaderConfig responseBodyReaderConfig) {
        this.closeableHttpClient = closeableHttpClient;
        this.uriBuilder = uriBuilder;
        this.responseBodyReaderConfig = responseBodyReaderConfig;
    }

    BasicWebTarget(CloseableHttpClient closeableHttpClient, URIBuilder uriBuilder, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig) {
        this(closeableHttpClient, uriBuilder, responseBodyReaderConfig);
        defaultHeaders.forEach(httpUriRequestBuilder::addHeader);
        defaultRequestParameters.forEach(httpUriRequestBuilder::addParameter);
    }

    @Override
    public WebTarget path(String path) {
        ArgsCheck.notNull(path, "path");

        uriBuilder.setPath(path);
        return this;
    }

    @Override
    public Response request(HttpMethod method, HttpEntity httpEntity) {
        ArgsCheck.notNull(method, "method");

        httpUriRequestBuilder.setEntity(httpEntity);
        return request(method);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, HttpEntity httpEntity, Class<T> responseType) {
        httpUriRequestBuilder.setEntity(httpEntity);
        return request(method, responseType);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, HttpEntity httpEntity, TypeReference<T> responseType) {
        ArgsCheck.notNull(method, "method");
        ArgsCheck.notNull(responseType, "responseType");

        httpUriRequestBuilder.setEntity(httpEntity);
        return request(method, responseType.getType());
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, Class<T> responseType) {
        ArgsCheck.notNull(method, "method");
        ArgsCheck.notNull(responseType, "responseType");

        return request(method, (Type) responseType);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, TypeReference<T> responseType) {
        ArgsCheck.notNull(method, "method");
        ArgsCheck.notNull(responseType, "responseType");

        return request(method, responseType.getType());
    }

    @Override
    public Response request(HttpMethod method) {
        ArgsCheck.notNull(method, "method");

        HttpUriRequest request = resolveRequest(method);
        try {
            return new BasicResponse(closeableHttpClient.execute(request), responseBodyReaderConfig, request.getURI());
        } catch (ConnectionPoolTimeoutException e) {
            LOGGER.debug("Connection pool is empty for request on uri: [" + request.getURI() + "]. Status code: " + SC_SERVICE_UNAVAILABLE, e);
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Connection pool is empty. " + e, request.getURI(), CONNECTION_POOL_IS_EMPTY, e);
        } catch (SocketTimeoutException | NoHttpResponseException e) {
            //todo support retry when NoHttpResponseException
            LOGGER.debug("Server on uri: [" + request.getURI() + "] is high loaded. Status code: " + SC_SERVICE_UNAVAILABLE, e);
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Remote server is high loaded. " + e, request.getURI(), REMOTE_SERVER_HIGH_LOADED, e);
        } catch (ConnectTimeoutException e) {
            LOGGER.debug("HttpRequest is unable to establish a connection with the: [" + request.getURI() + "] within the given period of time. Status code: " + SC_SERVICE_UNAVAILABLE, e);
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "HttpRequest is unable to establish a connection within the given period of time. " + e, request.getURI(), CONNECT_TIMEOUT_EXPIRED, e);

        } catch (HttpHostConnectException e) {
            LOGGER.debug("Server on uri: [" + request.getURI() + "] is down. Status code: " + SC_SERVICE_UNAVAILABLE, e);
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Server is down. " + e, request.getURI(), REMOTE_SERVER_IS_DOWN, e);
        } catch (ClientProtocolException e) {
            LOGGER.debug("URI: [" + request.getURI() + "]", e);
            throw new RequestException(e.getMessage(), e);
        } catch (IOException e) {
            LOGGER.debug("Connection was aborted for request on uri: [" + request.getURI() + "]. Status code: " + SC_SERVICE_UNAVAILABLE, e);
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Connection was aborted. " + e, request.getURI(), IO, e);
        }
    }

    private HttpUriRequest resolveRequest(HttpMethod method) {

        return httpUriRequestBuilder.setMethod(method.name()).setUri(getURI()).build();
    }

    <T> ResponseHandler<T> request(HttpMethod method, Type type) {
        CustomArgsCheck.checkIsCorrectTypeForDeserialization(type);

        long startTime = System.currentTimeMillis();

        ResponseHandler<T> result;
        StatusLine statusLine;

        try (Response response = request(method)) {
            statusLine = response.getStatusLine();
            if (statusLine == null) {
                throw new IllegalStateException("StatusLine is null.");
            }

            int responseCode = statusLine.getStatusCode();
            HttpEntity httpEntity = response.getEntity();
            LOGGER.info("Response code from uri: [{}] is {}", response.getURI(), responseCode);

            boolean hasBody = HttpRequestUtils.hasBody(responseCode);

            T content = null;
            String failedMessage = null;
            if (hasBody && httpEntity == null) {
                failedMessage = "Response entity is null";
                LOGGER.debug("{} .Uri: [{}]. Status code: {}", failedMessage, response.getURI(), responseCode);
                responseCode = SC_BAD_GATEWAY;
            } else {
                try {
                    if (!HttpRequestUtils.isVoidType(type) && hasBody && HttpRequestUtils.isSuccess(responseCode)) {

                        content = response.readEntityChecked(type);

                        LOGGER.trace("Result of Uri: [{}] is {}", response.getURI(), content);
                    } else if (HttpRequestUtils.isNonSuccess(responseCode)) {
                        //todo find reader
                        failedMessage = ResponseBodyReader.stringReader().read(new BasicResponseBodyReaderContext(response, String.class));
                        String logMsg = "Unexpected Response. Url: [" + response.getURI() + "] Status code: " + responseCode + ", Error message: " + failedMessage;
                        if (responseCode == SC_BAD_REQUEST) {
                            LOGGER.warn(logMsg);
                        } else {
                            LOGGER.debug(logMsg);
                        }
                    }
                } catch (ResponseBodyReaderException e) {
                    failedMessage = "Response deserialization failed. Cannot deserialize response to: [" + type + "]." + e;
                    LOGGER.debug(failedMessage + ". Uri: [" + response.getURI() + "]. Status code: " + responseCode, e);
                    responseCode = SC_BAD_GATEWAY;
                } catch (IOException e) {
                    failedMessage = "Get content from response failed: " + e;
                    LOGGER.debug("Stream could not be created. Uri: [" + response.getURI() + "]. Status code: " + responseCode, e);
                    responseCode = SC_SERVICE_UNAVAILABLE;
                }
            }
            ContentType responseContentType = ContentType.get(httpEntity);
            EntityUtils.consumeQuietly(httpEntity);
            result = new BasicResponseHandler<>(content, responseCode, failedMessage, type, responseContentType, response.getURI(), statusLine);

        } catch (ResponseException e) {
            result = new BasicResponseHandler<>(null, e.getStatusCode(), "Connection pool is empty. " + e, type, null, e.getURI(), e.getConnectionFailureType());
            LOGGER.debug("Connection pool is empty for request on uri: [" + e.getURI() + "]. Status code: " + result.getStatusCode(), e);
        } catch (IOException e) {
            LOGGER.warn("Resources close failed", e);
            result = new BasicResponseHandler<>(null, SC_INTERNAL_SERVER_ERROR, "Resources close failed. " + e, type, null, getURI(), IO);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Executing of uri: [{}] completed. Time: {}", result.getURI(), HttpRequestUtils.humanTime(startTime));
        }
        LOGGER.trace("Executed result: {}", result);
        return result;
    }

    private URI getURI() {
        URI uri;
        try {
            uri = uriBuilder.build().normalize();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        return uri;
    }

    @Override
    public WebTarget removeHeader(Header header) {
        ArgsCheck.notNull(header, "method");

        httpUriRequestBuilder.removeHeader(header);
        return this;
    }

    @Override
    public WebTarget removeHeaders(String name) {
        ArgsCheck.notNull(name, "name");

        httpUriRequestBuilder.removeHeaders(name);

        return this;
    }

    @Override
    public WebTarget updateHeader(Header header) {
        ArgsCheck.notNull(header, "header");

        httpUriRequestBuilder.setHeader(header);
        return this;
    }


    @Override
    public WebTarget addHeader(Header header) {
        ArgsCheck.notNull(header, "header");

        httpUriRequestBuilder.addHeader(header);
        return this;
    }

    @Override
    public WebTarget setRequestConfig(RequestConfig requestConfig) {
        httpUriRequestBuilder.setConfig(requestConfig);
        return this;
    }

    @Override
    public WebTarget addParameter(NameValuePair nameValuePair) {
        ArgsCheck.notNull(nameValuePair, "nameValuePair");

        httpUriRequestBuilder.addParameter(nameValuePair);
        return this;
    }
}
