/*
 * Copyright (c) 2022. Benik Arakelyan
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
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    private final HttpUriRequestBuilder httpUriRequestBuilder;
    private final ResponseBodyReaderConfig responseBodyReaderConfig;

    BasicWebTarget(CloseableHttpClient closeableHttpClient, URI uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig) {
        this(closeableHttpClient, new URIBuilder(uri), defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
    }

    BasicWebTarget(CloseableHttpClient closeableHttpClient, String uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig) throws URISyntaxException {
        this(closeableHttpClient, new URIBuilder(uri), defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
    }

    private BasicWebTarget(CloseableHttpClient closeableHttpClient, URIBuilder uriBuilder, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig) {
        this.closeableHttpClient = closeableHttpClient;
        this.uriBuilder = uriBuilder;
        this.responseBodyReaderConfig = responseBodyReaderConfig;
        this.httpUriRequestBuilder = new HttpUriRequestBuilder();

        defaultHeaders.forEach(httpUriRequestBuilder::addHeader);
        defaultRequestParameters.forEach(httpUriRequestBuilder::addParameter);
    }

    BasicWebTarget(CloseableHttpClient closeableHttpClient, URIBuilder uriBuilder, HttpUriRequestBuilder httpUriRequestBuilder, ResponseBodyReaderConfig responseBodyReaderConfig) {
        this.closeableHttpClient = closeableHttpClient;
        this.uriBuilder = uriBuilder;
        this.httpUriRequestBuilder = httpUriRequestBuilder;
        this.responseBodyReaderConfig = responseBodyReaderConfig;
    }

    /**
     * Copy constructor
     *
     * @param source source WebTarget instance from which new WebTarget must be initialized
     */
    BasicWebTarget(BasicWebTarget source) {
        this.closeableHttpClient = source.getCloseableHttpClient();
        this.uriBuilder = source.getUriBuilder();
        this.responseBodyReaderConfig = source.getResponseBodyReaderConfig();
        this.httpUriRequestBuilder = source.getHttpUriRequestBuilder();
    }

    @Override
    public WebTarget path(String path) {
        ArgsCheck.notNull(path, "path");

        HttpRequestUtils.appendPath(uriBuilder, path);
        return this;
    }

    @Override
    public WebTarget setPath(String path) {
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
        return request(method, responseType);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, Class<T> responseType) {
        ArgsCheck.notNull(method, "method");
        ArgsCheck.notNull(responseType, "responseType");

        return request(method, new TypeReference<>(responseType));
    }

    @Override
    public Response request(HttpMethod method) {
        ArgsCheck.notNull(method, "method");

        HttpUriRequest request = resolveRequest(method);
        try {
            return new BasicResponse(closeableHttpClient.execute(request), responseBodyReaderConfig, request.getURI());
        } catch (ConnectionPoolTimeoutException e) {
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Connection pool is empty for request on uri: [" + request.getURI() + "]. Status code: " + SC_SERVICE_UNAVAILABLE, request.getURI(), CONNECTION_POOL_IS_EMPTY, e);
        } catch (SocketTimeoutException | NoHttpResponseException e) {
            //todo support retry when NoHttpResponseException
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Server on uri: [" + request.getURI() + "] is high loaded. Status code: " + SC_SERVICE_UNAVAILABLE, request.getURI(), REMOTE_SERVER_HIGH_LOADED, e);
        } catch (ConnectTimeoutException e) {
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "HttpRequest is unable to establish a connection with the: [" + request.getURI() + "] within the given period of time. Status code: " + SC_SERVICE_UNAVAILABLE, request.getURI(), CONNECT_TIMEOUT_EXPIRED, e);

        } catch (HttpHostConnectException e) {
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Server on uri: [" + request.getURI() + "] is down. Status code: " + SC_SERVICE_UNAVAILABLE, request.getURI(), REMOTE_SERVER_IS_DOWN, e);
        } catch (ClientProtocolException e) {
            throw new RequestException("Error in the HTTP protocol. URI: [" + request.getURI() + "]", e);
        } catch (IOException e) {
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Connection was aborted for request on uri: [" + request.getURI() + "]. Status code: " + SC_SERVICE_UNAVAILABLE, request.getURI(), IO, e);
        }
    }

    private HttpUriRequest resolveRequest(HttpMethod method) {

        return httpUriRequestBuilder.setMethod(method.name()).setUri(getURI()).build();
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, TypeReference<T> typeReference) {
        CustomArgsCheck.checkIsCorrectTypeForDeserialization(typeReference.getRawType());

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
                    if (!HttpRequestUtils.isVoidType(typeReference.getRawType()) && hasBody && HttpRequestUtils.isSuccess(responseCode)) {

                        content = response.readEntityChecked(typeReference);

                        LOGGER.trace("Result of Uri: [{}] is {}", response.getURI(), content);
                    } else if (HttpRequestUtils.isNonSuccess(responseCode)) {

                        failedMessage = response.readEntityChecked(String.class);
                        String logMsg = "Unexpected Response. Url: [" + response.getURI() + "] Status code: " + responseCode + ", Error message: " + failedMessage;
                        if (responseCode == SC_BAD_REQUEST) {
                            LOGGER.warn(logMsg);
                        } else {
                            LOGGER.debug(logMsg);
                        }
                    }
                } catch (ResponseBodyReaderException e) {
                    failedMessage = "Response deserialization failed. Cannot deserialize response to: [" + typeReference + "]." + e;
                    LOGGER.debug(failedMessage + ". Uri: [" + response.getURI() + "]. Status code: " + responseCode, e);
                    responseCode = SC_BAD_GATEWAY;
                } catch (IOException e) {
                    failedMessage = "Get content from response failed: " + e;
                    LOGGER.debug("Stream could not be created. Uri: [" + response.getURI() + "]. Status code: " + responseCode, e);
                    responseCode = SC_SERVICE_UNAVAILABLE;
                }
            }

            HeaderGroup headerGroup = new HeaderGroup();
            headerGroup.setHeaders(response.getAllHeaders());

            ContentType responseContentType = ContentType.get(httpEntity);
            EntityUtils.consumeQuietly(httpEntity);
            result = new BasicResponseHandler<>(content, responseCode, headerGroup, failedMessage, typeReference.getType(), responseContentType, response.getURI(), statusLine);

        } catch (ResponseException e) {
            result = new BasicResponseHandler<>(null, e.getStatusCode(), e.getMessage(), typeReference.getType(), null, e.getURI(), e.getConnectionFailureType());
            LOGGER.debug("Request failed.", e);
        } catch (IOException e) {
            LOGGER.warn("Resources close failed.", e);
            result = new BasicResponseHandler<>(null, SC_INTERNAL_SERVER_ERROR, "Resources close failed. " + e, typeReference.getType(), null, getURI(), IO);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Executing of uri: [{}] completed. Time: {}", result.getURI(), HttpRequestUtils.humanTime(startTime));
        }
        LOGGER.trace("Executed result: {}", result);
        return result;
    }

    @Override
    public URI getURI() {
        URI uri;
        try {
            uri = uriBuilder.build().normalize();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URI syntax is incorrect. URI: [" + getURIString() + "].", e);
        }
        return uri;
    }

    @Override
    public String getURIString() {
        return uriBuilder.toString();
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

    CloseableHttpClient getCloseableHttpClient() {
        return closeableHttpClient;
    }

    URIBuilder getUriBuilder() {
        return new URIBuilder(getURI());
    }

    HttpUriRequestBuilder getHttpUriRequestBuilder() {
        return httpUriRequestBuilder.copyBuilder();
    }

    ResponseBodyReaderConfig getResponseBodyReaderConfig() {
        return responseBodyReaderConfig;
    }
}
