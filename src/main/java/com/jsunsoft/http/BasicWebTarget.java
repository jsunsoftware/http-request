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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.jsunsoft.http.BasicConnectionFailureType.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hc.core5.http.HttpStatus.*;

class BasicWebTarget implements WebTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicWebTarget.class);

    private final CloseableHttpClient closeableHttpClient;
    private final URIBuilder uriBuilder;
    private final HttpUriRequestBuilder httpUriRequestBuilder;
    private final ResponseBodyReaderConfig responseBodyReaderConfig;
    private final RequestBodySerializeConfig requestBodySerializeConfig;

    BasicWebTarget(CloseableHttpClient closeableHttpClient, URI uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig, RequestBodySerializeConfig requestBodySerializeConfig) {
        this(closeableHttpClient, new URIBuilder(uri), defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig);
    }

    BasicWebTarget(CloseableHttpClient closeableHttpClient, String uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig, RequestBodySerializeConfig requestBodySerializeConfig) throws URISyntaxException {
        this(closeableHttpClient, new URIBuilder(uri), defaultHeaders, defaultRequestParameters, responseBodyReaderConfig, requestBodySerializeConfig);
    }

    private BasicWebTarget(CloseableHttpClient closeableHttpClient, URIBuilder uriBuilder, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig, RequestBodySerializeConfig requestBodySerializeConfig) {
        this.closeableHttpClient = closeableHttpClient;
        this.uriBuilder = uriBuilder;
        this.responseBodyReaderConfig = responseBodyReaderConfig;
        this.requestBodySerializeConfig = requestBodySerializeConfig;
        this.httpUriRequestBuilder = new HttpUriRequestBuilder();

        defaultHeaders.forEach(httpUriRequestBuilder::addHeader);
        defaultRequestParameters.forEach(httpUriRequestBuilder::addParameter);
    }

    BasicWebTarget(CloseableHttpClient closeableHttpClient, URIBuilder uriBuilder, HttpUriRequestBuilder httpUriRequestBuilder, ResponseBodyReaderConfig responseBodyReaderConfig, RequestBodySerializeConfig requestBodySerializeConfig) {
        this.closeableHttpClient = closeableHttpClient;
        this.uriBuilder = uriBuilder;
        this.httpUriRequestBuilder = httpUriRequestBuilder;
        this.responseBodyReaderConfig = responseBodyReaderConfig;
        this.requestBodySerializeConfig = requestBodySerializeConfig;
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
        this.requestBodySerializeConfig = source.getRequestBodySerializeConfig();
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
    public Response request(HttpMethod method, HttpContext context) {
        ArgsCheck.notNull(method, "method");

        ClassicHttpRequest request = resolveRequest(method);

        URI uri = resolveRequestURI(request);

        LOGGER.trace("Executing request: {}", httpUriRequestBuilder);

        try {
            HttpHost httpHost = resolveHttpHost(request);

            return new BasicResponse(closeableHttpClient.executeOpen(httpHost, request, context), responseBodyReaderConfig, uri);
        } catch (ConnectionRequestTimeoutException e) {
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Connection pool is empty", uri, CONNECTION_POOL_IS_EMPTY, e);
        } catch (ConnectTimeoutException e) {
            throw new ResponseException(SC_GATEWAY_TIMEOUT, "Unable to establish a connection within the given period of time", uri, CONNECT_TIMEOUT, e);
        } catch (SocketTimeoutException | NoHttpResponseException e) {
            //todo support retry when NoHttpResponseException
            throw new ResponseException(SC_GATEWAY_TIMEOUT, "Server didn't respond with specified time", uri, RESPONSE_TIMEOUT, e);
        } catch (HttpHostConnectException e) {
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Failed to connect to server. Potential reasons: The target server may be down, unreachable, or there are network connectivity issues", uri, SERVICE_UNREACHABLE, e);
        } catch (ClientProtocolException e) {
            throw new RequestException("Error in the HTTP protocol. URI: [" + uri + "]", e);
        } catch (IOException e) {
            throw new ResponseException(SC_SERVICE_UNAVAILABLE, "Connection was aborted", uri, IO, e);
        }
    }

    private ClassicHttpRequest resolveRequest(HttpMethod method) {

        return httpUriRequestBuilder.setMethod(method.name()).setUri(getURI()).build();
    }

    private HttpHost resolveHttpHost(ClassicHttpRequest request) throws ClientProtocolException {
        try {
            return RoutingSupport.determineHost(request);
        } catch (final HttpException ex) {
            throw new ClientProtocolException(ex);
        }
    }

    private URI resolveRequestURI(ClassicHttpRequest request) {
        try {
            return request.getUri();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URI syntax is incorrect. URI: [" + getURIString() + "].", e);
        }
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, TypeReference<T> typeReference) {
        CustomArgsCheck.checkIsCorrectTypeForDeserialization(typeReference.getRawType());

        long startTime = System.currentTimeMillis();

        ResponseHandler<T> result;

        try (Response response = request(method)) {

            int statusCode = response.getCode();
            HttpEntity httpEntity = response.getEntity();

            LOGGER.info("Response code from uri: [{}] is {}", response.getURI(), statusCode);

            boolean hasBody = HttpRequestUtils.hasBody(statusCode);

            T content = null;
            String failedMessage = null;
            if (hasBody && httpEntity == null) {
                failedMessage = "Response entity is null";
                LOGGER.debug("{} .Uri: [{}]. Status code: {}", failedMessage, response.getURI(), statusCode);
                statusCode = SC_BAD_GATEWAY;
            } else {
                try {
                    if (!HttpRequestUtils.isVoidType(typeReference.getRawType()) && hasBody && HttpRequestUtils.isSuccess(statusCode)) {

                        content = response.readEntityChecked(typeReference);

                        LOGGER.trace("Result of Uri: [{}] is {}", response.getURI(), content);
                    } else if (HttpRequestUtils.isNonSuccess(statusCode)) {

                        failedMessage = response.readEntityChecked(String.class);
                        String logMsg = "Unexpected Response. Url: [" + response.getURI() + "] Status code: " + statusCode + ", Error message: " + failedMessage;
                        if (statusCode == SC_BAD_REQUEST) {
                            LOGGER.warn(logMsg);
                        } else {
                            LOGGER.debug(logMsg);
                        }
                    }
                } catch (ResponseBodyReaderException e) {
                    failedMessage = "Response deserialization failed. Cannot deserialize response to: [" + typeReference + "]. Reason: " + throwableDeepMessages(e);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(failedMessage + ". Uri: [" + response.getURI() + "]. Status code: " + statusCode, e);
                    }
                    statusCode = SC_BAD_GATEWAY;
                } catch (IOException e) {
                    failedMessage = "Get content from response failed: " + e;
                    LOGGER.debug("Stream could not be created. Uri: [" + response.getURI() + "]. Status code: " + statusCode, e);
                    statusCode = SC_SERVICE_UNAVAILABLE;
                }
            }

            HeaderGroup headerGroup = new HeaderGroup();
            headerGroup.setHeaders(response.getHeaders());

            ContentType responseContentType = HttpRequestUtils.getContentTypeFromHttpEntity(httpEntity);
            EntityUtils.consumeQuietly(httpEntity);
            result = new BasicResponseHandler<>(content, statusCode, headerGroup, failedMessage, typeReference.getType(), responseContentType, response.getURI(), startTime);

        } catch (ResponseException e) {

            result = new BasicResponseHandler<>(null, e.getStatusCode(), e, typeReference.getType(), null, e.getURI(), e.getConnectionFailureType(), startTime);
            LOGGER.debug("Request failed.", e);
        } catch (IOException e) {

            LOGGER.error("", e);

            result = new BasicResponseHandler<>(null, SC_INTERNAL_SERVER_ERROR, "Failed to close response resource: " + e, e, typeReference.getType(), null, getURI(), IO, startTime);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Executing of uri: [{}] completed. Time: {}", result.getURI(), HttpRequestUtils.humanTime(startTime));
        }
        LOGGER.trace("Executed result: {}", result);
        return result;
    }

    @Override
    public ResponseHandler<?> rawRequest(HttpMethod method, Object body) {
        return rawRequest(method, parsePayloadBody(body));
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
    public WebTarget setCharset(Charset charset) {
        httpUriRequestBuilder.setCharset(charset);

        return this;
    }

    @Override
    public <T> ResponseHandler<T> request(final HttpMethod method, final String payload, Class<T> responseType) {
        ArgsCheck.notNull(method, "method");
        ArgsCheck.notNull(payload, "payload");
        ArgsCheck.notNull(payload, "responseType");

        logRequestBody(method, payload);

        return request(method, new StringEntity(payload, UTF_8), responseType);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, Object body, Class<T> responseType) {
        return request(method, parsePayloadBody(body), responseType);
    }

    @Override
    public <T> ResponseHandler<T> request(final HttpMethod method, final String payload, TypeReference<T> responseType) {
        ArgsCheck.notNull(method, "method");
        ArgsCheck.notNull(payload, "payload");
        ArgsCheck.notNull(payload, "responseType");

        logRequestBody(method, payload);

        return request(method, new StringEntity(payload, UTF_8), responseType);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, Object body, TypeReference<T> responseType) {
        return request(method, parsePayloadBody(body), responseType);
    }

    @Override
    public Response request(final HttpMethod method, final String payload) {
        ArgsCheck.notNull(method, "method");
        ArgsCheck.notNull(payload, "payload");

        logRequestBody(method, payload);

        return request(method, new StringEntity(payload, UTF_8));
    }

    @Override
    public Response request(HttpMethod method, Object body) {
        return request(method, parsePayloadBody(body));
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

    RequestBodySerializeConfig getRequestBodySerializeConfig() {
        return requestBodySerializeConfig;
    }

    private String parsePayloadBody(Object body) {

        ArgsCheck.notNull(body, "body");

        Header contentTypeHeader = httpUriRequestBuilder.getFirstHeader(HttpHeaders.CONTENT_TYPE);

        ContentType contentType = contentTypeHeader != null ? ContentType.parse(contentTypeHeader.getValue()) : null;

        LOGGER.trace("Serializing body based on content type: [{}] body object: {}", contentType, body);

        ObjectMapper mapper = resolveObjectMapper(contentType);

        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RequestException("Serialization of request body failed.", e);
        }
    }

    private ObjectMapper resolveObjectMapper(ContentType contentType) {
        ObjectMapper mapper;

        if (ContentType.APPLICATION_JSON.isSameMimeType(contentType)) {

            mapper = requestBodySerializeConfig.getDefaultJsonMapper();

        } else if (ContentType.APPLICATION_XML.isSameMimeType(contentType) || ContentType.TEXT_XML.isSameMimeType(contentType)) {
            mapper = requestBodySerializeConfig.getDefaultXmlMapper();
        } else {
            throw new RequestException("Serializer is not found. Now supported only JSON and XML serialization depends on [" + HttpHeaders.CONTENT_TYPE + "]. Founded first content type header is: " + contentType);
        }
        return mapper;
    }

    private void logRequestBody(HttpMethod method, final String payload) {
        LOGGER.atDebug()
                .addArgument(this::getURIString)
                .addArgument(method)
                .addArgument(payload)
                .log("Requesting to: [{}] with HTTP method: [{}] and body: {}");
    }

    private static String throwableDeepMessages(Throwable t) {

        return ExceptionUtils.getThrowableList(t)
                .stream()
                .filter(Objects::nonNull)
                .map(Throwable::toString)
                .collect(Collectors.joining(". Cause: "));
    }
}
