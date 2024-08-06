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

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.hc.core5.util.Args;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A resource target identified by the resource URI.
 */
public interface WebTarget {

    /**
     * Append path to the URI of the current target instance.
     *
     * @param path the path.
     * @return target instance.
     * @throws NullPointerException if path is {@code null}.
     */
    WebTarget path(final String path);

    /**
     * Set path to the URI of the current target instance.
     *
     * <p>
     * Note: If path already existed this will replace it.
     *
     * @param path the path.
     * @return target instance.
     * @throws NullPointerException if path is {@code null}.
     */
    WebTarget setPath(final String path);

    /**
     * Invoke {@linkplain #request(HttpMethod, HttpContext)} with default http context
     *
     * @see #request(HttpMethod, HttpContext)
     */
    default Response request(final HttpMethod method) {
        return request(method, (HttpContext) null);
    }

    /**
     * Invoke an arbitrary method for the current request.
     *
     * @param method  the http method.
     * @param context the {@linkplain HttpContext}
     * @return the response to the request. This is always a final response, never an intermediate response with an 1xx status code.
     * Whether redirects or authentication challenges will be returned
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of a http protocol error.
     * @see HttpContext
     */
    Response request(final HttpMethod method, HttpContext context);

    /**
     * Invoke an arbitrary method for the current request.
     *
     * @param method     the http method.
     * @param httpEntity httpEntity
     * @return the response to the request. This is always a final response, never an intermediate response with an 1xx status code.
     * Whether redirects or authentication challenges will be returned
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error.
     */
    Response request(final HttpMethod method, final HttpEntity httpEntity);

    /**
     * Invoke an arbitrary method for the current request.
     *
     * @param method       the http method.
     * @param httpEntity   httpEntity
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     * @return the ResponseHandler instance to the request and pass converted response in ResponseHandler instance.
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error.
     * @see #request(HttpMethod, HttpEntity)
     * @see ResponseHandler
     */
    <T> ResponseHandler<T> request(final HttpMethod method, final HttpEntity httpEntity, Class<T> responseType);

    /**
     * Invoke an arbitrary method for the current request.
     *
     * @param method       the http method.
     * @param httpEntity   httpEntity
     * @param responseType representation of a TypeReference Java type the response entity will be converted to.
     * @param <T>          response entity type.
     * @return the ResponseHandler instance to the request and pass converted response in ResponseHandler instance.
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error.
     * @see #request(HttpMethod, HttpEntity)
     * @see ResponseHandler
     */
    <T> ResponseHandler<T> request(final HttpMethod method, final HttpEntity httpEntity, TypeReference<T> responseType);

    /**
     * Invoke an arbitrary method for the current request.
     *
     * @param method       the http method.
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     * @return the ResponseHandler instance to the request and pass converted response in ResponseHandler instance.
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error.
     * @see #request(HttpMethod)
     * @see ResponseHandler
     */
    <T> ResponseHandler<T> request(final HttpMethod method, Class<T> responseType);

    /**
     * Invoke an arbitrary method for the current request.
     *
     * @param method       the http method.
     * @param responseType epresentation of a TypeReference Java type the response entity will be converted to.
     * @param <T>          response entity type.
     * @return the ResponseHandler instance to the request and pass converted response in ResponseHandler instance.
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error.
     * @see #request(HttpMethod)
     * @see ResponseHandler
     */
    <T> ResponseHandler<T> request(final HttpMethod method, TypeReference<T> responseType);

    /**
     * Invoke an arbitrary method for the current request.
     * <p>
     * Mainly designed to use in case when response body aren't interested.
     * </p>
     * Any attempt to get content from {@code ResponseHandler} will be thrown exception
     *
     * @param method the http method.
     * @return the ResponseHandler instance to the request and pass converted response in ResponseHandler instance.
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error.
     * @see #request(HttpMethod)
     * @see ResponseHandler
     */
    default ResponseHandler<?> rawRequest(final HttpMethod method) {
        return request(method, Void.class);
    }

    /**
     * Invoke an arbitrary method for the current request.
     * <p>
     * Mainly designed to use in case when response body aren't interested.
     * </p>
     * Any attempt to get content from {@code ResponseHandler} will be thrown exception
     *
     * @param method     the http method.
     * @param httpEntity httpEntity
     * @return the ResponseHandler instance to the request and pass converted response in ResponseHandler instance.
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error.
     * @see #request(HttpMethod)
     * @see ResponseHandler
     */
    default ResponseHandler<?> rawRequest(final HttpMethod method, final HttpEntity httpEntity) {
        return request(method, httpEntity, Void.class);
    }

    /**
     * Invoke an arbitrary method for the current request.
     * <p>
     * Mainly designed to use in case when response body aren't interested.
     * </p>
     * Any attempt to get content from {@code ResponseHandler} will be thrown exception
     *
     * @param method  the http method.
     * @param payload payload
     * @return the ResponseHandler instance to the request and pass converted response in ResponseHandler instance.
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error.
     * @see #request(HttpMethod)
     * @see ResponseHandler
     */
    default ResponseHandler<?> rawRequest(final HttpMethod method, final String payload) {
        return request(method, payload, Void.class);
    }

    /**
     * Invoke an arbitrary method for the current request with serializing body depends on a Content-type.
     * <p>
     * Mainly designed to use in case when response body aren't interested.
     * </p>
     * Any attempt to get content from {@code ResponseHandler} will be thrown exception
     *
     * @param method the http method.
     * @param body   payload object
     * @return the ResponseHandler instance to the request and pass converted response in ResponseHandler instance.
     * or handled automatically depends on the implementation and configuration of this client.
     * @throws ResponseException in case of any IO problem or the connection was aborted.
     * @throws RequestException  in case of an http protocol error or body serialization failed.
     * @see #request(HttpMethod, String)
     * @see #request(HttpMethod)
     * @see ResponseHandler
     */
    ResponseHandler<?> rawRequest(final HttpMethod method, final Object body);

    /**
     * Removes the given header.
     *
     * @param header the header to remove
     * @return WebTarget instance
     */
    WebTarget removeHeader(final Header header);

    /**
     * Removes all headers with name.
     *
     * @param name the header name
     * @return WebTarget instance
     */
    WebTarget removeHeaders(final String name);

    /**
     * Replaces the first occurence of the header with the same name. If no header with
     * the same name is found the given header is added to the end of the list.
     *
     * @param header the new header that should replace the first header with the same
     *               name if present in the list.
     * @return WebTarget instance
     */
    WebTarget updateHeader(final Header header);

    /**
     * Adds the given header to the request. The order in which this header was added is preserved.
     *
     * @param header header instance. Can't be null
     * @return WebTarget instance
     */
    WebTarget addHeader(final Header header);

    WebTarget setCharset(final Charset charset);

    /**
     * The same as {@link #request(HttpMethod, HttpEntity, Class)} wrapped {@code payload} into {@link StringEntity}
     *
     * @param method       the http method.
     * @param payload      payload
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type
     * @return ResponseHandler instance
     */
    <T> ResponseHandler<T> request(final HttpMethod method, final String payload, Class<T> responseType);

    /**
     * The same as {@link #request(HttpMethod, String, Class)} with serializing body depends on a Content-type into String {@code payload}
     *
     * @param method       the http method.
     * @param body         payload
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type
     * @return ResponseHandler instance
     */
    <T> ResponseHandler<T> request(final HttpMethod method, final Object body, Class<T> responseType);

    <T> ResponseHandler<T> request(final HttpMethod method, final String payload, TypeReference<T> responseType);

    <T> ResponseHandler<T> request(final HttpMethod method, final Object body, TypeReference<T> responseType);

    Response request(final HttpMethod method, final String payload);

    Response request(final HttpMethod method, final Object body);

    /**
     * Adds the given name and value as header to the request.
     *
     * @param name  name of header. Can't be null
     * @param value value of header
     * @return WebTarget instance
     */
    default WebTarget addHeader(final String name, final String value) {
        ArgsCheck.notNull(name, "name");

        return addHeader(new BasicHeader(name, value));
    }

    /**
     * Adds the given headers to the request. The order in which this header was added is preserved.
     *
     * @param headers collections of headers
     * @return WebTarget instance
     */
    default WebTarget addHeaders(final Collection<? extends Header> headers) {
        ArgsCheck.notNull(headers, "headers");

        WebTarget result = this;

        for (Header header : headers) {
            result = result.addHeader(header);
        }

        return result;
    }

    /**
     * Replaces the first occurence of the header with the same name by the value. If no header with
     * the same name is found the given header is added to the end of the list.
     *
     * @param name  name of header. Can't be null
     * @param value value of header
     * @return WebTarget instance
     */
    default WebTarget updateHeader(final String name, final String value) {
        ArgsCheck.notNull(name, "name");

        return updateHeader(new BasicHeader(name, value));
    }

    /**
     * Sets content type to header
     *
     * @param contentType content type of request header
     * @return WebTarget instance
     */
    default WebTarget addContentType(final ContentType contentType) {
        return addHeader(HttpHeaders.CONTENT_TYPE, contentType.toString());
    }

    /**
     * Sets the {@code requestConfig} to the request
     *
     * @param requestConfig requestConfig
     * @return WebTarget instance
     * @see RequestConfig
     */
    WebTarget setRequestConfig(final RequestConfig requestConfig);

    /**
     * Added parameter into request
     *
     * @param nameValuePair nameValuePair
     * @return WebTarget instance
     */
    WebTarget addParameter(final NameValuePair nameValuePair);

    /**
     * @return URI as string
     */
    String getURIString();

    /**
     * @return The URI
     */
    URI getURI();

    /**
     * Add parameters into request
     *
     * @param parameters nameValuePairs
     * @return WebTarget instance
     */
    default WebTarget addParameters(final NameValuePair... parameters) {
        ArgsCheck.notNull(parameters, "parameters");

        WebTarget result = this;

        for (NameValuePair parameter : parameters) {
            result = result.addParameter(parameter);
        }

        return result;
    }

    /**
     * Add parameters from queryString.
     * <p>
     * For example: queryString = {@code "param1=param1&param2=param2" is the same as call}
     * <pre>{@code
     *     addParameter(param1, param1).addParameter(param2, param2);
     * }</pre>
     * Default charset is "UTF-8".
     *
     * @param queryString queryString
     * @param charset     charset
     * @return WebTarget instance
     */
    default WebTarget addParameters(final String queryString, final Charset charset) {
        ArgsCheck.notNull(queryString, "queryString");
        ArgsCheck.notNull(charset, "charset");

        return addParameters(WWWFormCodec.parse(queryString, charset));
    }

    /**
     * Add parameters from queryString.
     * <p>
     * For example: queryString = {@code "param1=param1&param2=param2" is the same as call}
     * <pre>{@code
     *     addParameter(param1, param1).addParameter(param2, param2);
     * }</pre>
     * Default charset is "UTF-8".
     *
     * @param queryString queryString
     * @return WebTarget instance
     */
    default WebTarget addParameters(final String queryString) {
        return addParameters(queryString, UTF_8);
    }

    /**
     * Add parameters into request as [nameValues[0]: nameValues[1], nameValues[2]: nameValues[3], ... e.t.c.] <br> So
     * name1 = nameValues[0], value1 = nameValues[1]; name2 = nameValues[1], value2 = nameValues[2] ... e.t.c.
     *
     * @param nameValues array of nameValue
     * @return WebTarget instance
     * @throws IllegalArgumentException When length of parameter nameValues is odd or ZERO.
     * @throws NullPointerException     when param nameValues is null
     */
    default WebTarget addParameters(final String... nameValues) {
        int nameValuesLength = ArgsCheck.notNull(nameValues, "nameValues").length;
        Args.check(nameValuesLength != 0, "Length of parameter can't be ZERO");
        Args.check(nameValuesLength % 2 == 0, "Length of nameValues can't be odd");

        WebTarget result = this;

        int end = nameValuesLength - 2;

        for (int i = 0; i <= end; i += 2) {
            result = result.addParameter(new BasicNameValuePair(nameValues[i], nameValues[i + 1]));
        }

        return result;
    }

    /**
     * Add parameters into request
     *
     * @param parameters nameValuePairs
     * @return WebTarget instance
     */
    default WebTarget addParameters(final Collection<? extends NameValuePair> parameters) {
        ArgsCheck.notNull(parameters, "parameters");

        WebTarget result = this;

        for (NameValuePair parameter : parameters) {
            result = result.addParameter(parameter);
        }

        return result;
    }

    /**
     * Add parameters into request key as request parameter name Value as request parameter value
     *
     * @param parameters parameters
     * @return WebTarget instance
     */
    default WebTarget addParameters(final Map<String, String> parameters) {
        ArgsCheck.notNull(parameters, "parameters");

        WebTarget result = this;

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            NameValuePair parameter = new BasicNameValuePair(entry.getKey(), entry.getValue());

            result = result.addParameter(parameter);
        }

        return result;
    }

    /**
     * Add parameter into request name as request parameter name value as request parameter value
     *
     * @param name  request parameter name
     * @param value request parameter value
     * @return WebTarget instance
     */
    default WebTarget addParameter(final String name, final String value) {
        ArgsCheck.notNull(name, "name");

        return addParameter(new BasicNameValuePair(name, value));
    }


    /**
     * Invoke HTTP GET method for the current request
     *
     * @return the response to the request.
     * @see #request(HttpMethod)
     */
    default Response get() {
        return request(HttpMethod.GET);
    }

    /**
     * Invoke HTTP GET method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod)
     */
    default ResponseHandler<?> rawGet() {
        return rawRequest(HttpMethod.GET);
    }


    default <T> ResponseHandler<T> get(Class<T> responseType) {
        return request(HttpMethod.GET, responseType);
    }

    default <T> ResponseHandler<T> get(TypeReference<T> responseType) {
        return request(HttpMethod.GET, responseType);
    }

    default Response put() {
        return request(HttpMethod.PUT);
    }

    default ResponseHandler<?> rawPut() {
        return rawRequest(HttpMethod.PUT);
    }

    /**
     * Invoke HTTP PUT method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, HttpEntity)
     */
    default ResponseHandler<?> rawPut(final HttpEntity httpEntity) {
        return rawRequest(HttpMethod.PUT, httpEntity);
    }

    /**
     * Invoke HTTP PUT method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, String)
     */
    default ResponseHandler<?> rawPut(final String payload) {
        return rawRequest(HttpMethod.PUT, payload);
    }

    /**
     * The same as {@link #rawPut(String)} with serializing body depends on a Content-type into String {@code payload}
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawPut(String)
     */
    ResponseHandler<?> rawPut(final Object body);

    default Response put(final HttpEntity httpEntity) {
        return request(HttpMethod.PUT, httpEntity);
    }

    default <T> ResponseHandler<T> put(final HttpEntity httpEntity, Class<T> responseType) {
        return request(HttpMethod.PUT, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> put(final HttpEntity httpEntity, TypeReference<T> responseType) {
        return request(HttpMethod.PUT, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> put(final String payload, Class<T> responseType) {
        return request(HttpMethod.PUT, payload, responseType);
    }

    <T> ResponseHandler<T> put(final Object body, Class<T> responseType);

    default <T> ResponseHandler<T> put(final String payload, TypeReference<T> responseType) {
        return request(HttpMethod.PUT, payload, responseType);
    }

    <T> ResponseHandler<T> put(final Object body, TypeReference<T> responseType);

    default Response put(final String payload) {
        return request(HttpMethod.PUT, payload);
    }

    Response put(final Object body);

    default <T> ResponseHandler<T> put(Class<T> responseType) {
        return request(HttpMethod.PUT, responseType);
    }

    default <T> ResponseHandler<T> put(TypeReference<T> responseType) {
        return request(HttpMethod.PUT, responseType);
    }

    default Response post() {
        return request(HttpMethod.POST);
    }

    default ResponseHandler<?> rawPost() {
        return rawRequest(HttpMethod.POST);
    }

    /**
     * Invoke HTTP POST method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, HttpEntity)
     */
    default ResponseHandler<?> rawPost(final HttpEntity httpEntity) {
        return rawRequest(HttpMethod.POST, httpEntity);
    }

    /**
     * Invoke HTTP POST method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, String)
     */
    default ResponseHandler<?> rawPost(final String payload) {
        return rawRequest(HttpMethod.POST, payload);
    }

    /**
     * The same as {@link #rawPost(String)} with serializing body depends on a Content-type into String {@code payload}
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawPost(String)
     */
    ResponseHandler<?> rawPost(final Object body);

    default Response post(final HttpEntity httpEntity) {
        return request(HttpMethod.POST, httpEntity);
    }

    default <T> ResponseHandler<T> post(final HttpEntity httpEntity, Class<T> responseType) {
        return request(HttpMethod.POST, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> post(final HttpEntity httpEntity, TypeReference<T> responseType) {
        return request(HttpMethod.POST, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> post(final String payload, Class<T> responseType) {
        return request(HttpMethod.POST, payload, responseType);
    }

    <T> ResponseHandler<T> post(final Object body, Class<T> responseType);

    default <T> ResponseHandler<T> post(final String payload, TypeReference<T> responseType) {
        return request(HttpMethod.POST, payload, responseType);
    }

    <T> ResponseHandler<T> post(final Object body, TypeReference<T> responseType);

    default Response post(final String payload) {
        return request(HttpMethod.POST, payload);
    }

    Response post(final Object body);

    default <T> ResponseHandler<T> post(Class<T> responseType) {
        return request(HttpMethod.POST, responseType);
    }

    default <T> ResponseHandler<T> post(TypeReference<T> responseType) {
        return request(HttpMethod.POST, responseType);
    }

    default Response head() {
        return request(HttpMethod.HEAD);
    }

    default ResponseHandler<?> rawHead() {
        return rawRequest(HttpMethod.HEAD);
    }

    default Response delete() {
        return request(HttpMethod.DELETE);
    }

    default ResponseHandler<?> rawDelete() {
        return rawRequest(HttpMethod.DELETE);
    }

    /**
     * Invoke HTTP DELETE method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, HttpEntity)
     */
    default ResponseHandler<?> rawDelete(final HttpEntity httpEntity) {
        return rawRequest(HttpMethod.DELETE, httpEntity);
    }

    /**
     * Invoke HTTP DELETE method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, String)
     */
    default ResponseHandler<?> rawDelete(final String payload) {
        return rawRequest(HttpMethod.DELETE, payload);
    }

    /**
     * The same as {@link #rawDelete(String)} with serializing body depends on a Content-type into String {@code payload}
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawDelete(String)
     */
    ResponseHandler<?> rawDelete(final Object body);

    default Response delete(final HttpEntity httpEntity) {
        return request(HttpMethod.DELETE, httpEntity);
    }

    default <T> ResponseHandler<T> delete(final HttpEntity httpEntity, Class<T> responseType) {
        return request(HttpMethod.DELETE, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> delete(final HttpEntity httpEntity, TypeReference<T> responseType) {
        return request(HttpMethod.DELETE, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> delete(final String payload, Class<T> responseType) {
        return request(HttpMethod.DELETE, payload, responseType);
    }

    <T> ResponseHandler<T> delete(final Object body, Class<T> responseType);

    default <T> ResponseHandler<T> delete(final String payload, TypeReference<T> responseType) {
        return request(HttpMethod.DELETE, payload, responseType);
    }

    <T> ResponseHandler<T> delete(final Object body, TypeReference<T> responseType);

    default Response delete(final String payload) {
        return request(HttpMethod.DELETE, payload);
    }

    Response delete(final Object body);

    default <T> ResponseHandler<T> delete(Class<T> responseType) {
        return request(HttpMethod.DELETE, responseType);
    }

    default <T> ResponseHandler<T> delete(TypeReference<T> responseType) {
        return request(HttpMethod.DELETE, responseType);
    }

    default Response options() {
        return request(HttpMethod.OPTIONS);
    }

    default ResponseHandler<?> rawOptions() {
        return rawRequest(HttpMethod.OPTIONS);
    }

    /**
     * Invoke HTTP OPTIONS method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, HttpEntity)
     */
    default ResponseHandler<?> rawOptions(final HttpEntity httpEntity) {
        return rawRequest(HttpMethod.OPTIONS, httpEntity);
    }

    /**
     * Invoke HTTP OPTIONS method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, String)
     */
    default ResponseHandler<?> rawOptions(final String payload) {
        return rawRequest(HttpMethod.OPTIONS, payload);
    }

    /**
     * The same as {@link #rawOptions(String)} with serializing body depends on a Content-type into String {@code payload}
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawOptions(String)
     */
    ResponseHandler<?> rawOptions(final Object body);

    default Response options(final HttpEntity httpEntity) {
        return request(HttpMethod.OPTIONS, httpEntity);
    }

    default <T> ResponseHandler<T> options(final HttpEntity httpEntity, Class<T> responseType) {
        return request(HttpMethod.OPTIONS, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> options(final HttpEntity httpEntity, TypeReference<T> responseType) {
        return request(HttpMethod.OPTIONS, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> options(final String payload, Class<T> responseType) {
        return request(HttpMethod.OPTIONS, payload, responseType);
    }

    <T> ResponseHandler<T> options(final Object body, Class<T> responseType);

    default <T> ResponseHandler<T> options(final String payload, TypeReference<T> responseType) {
        return request(HttpMethod.OPTIONS, payload, responseType);
    }

    <T> ResponseHandler<T> options(final Object body, TypeReference<T> responseType);

    default Response options(final String payload) {
        return request(HttpMethod.OPTIONS, payload);
    }

    Response options(final Object body);

    default <T> ResponseHandler<T> options(Class<T> responseType) {
        return request(HttpMethod.OPTIONS, responseType);
    }

    default <T> ResponseHandler<T> options(TypeReference<T> responseType) {
        return request(HttpMethod.OPTIONS, responseType);
    }

    default Response patch() {
        return request(HttpMethod.PATCH);
    }

    default ResponseHandler<?> rawPatch() {
        return rawRequest(HttpMethod.PATCH);
    }

    /**
     * Invoke HTTP PATCH method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, HttpEntity)
     */
    default ResponseHandler<?> rawPatch(final HttpEntity httpEntity) {
        return rawRequest(HttpMethod.PATCH, httpEntity);
    }

    /**
     * Invoke HTTP PATCH method for the current request
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawRequest(HttpMethod, String)
     */
    default ResponseHandler<?> rawPatch(final String payload) {
        return rawRequest(HttpMethod.PATCH, payload);
    }

    /**
     * The same as {@link #rawPatch(String)} with serializing body depends on a Content-type into String {@code payload}
     *
     * @return the ResponseHandler instance to the request.
     * @see #rawPatch(String)
     */
    ResponseHandler<?> rawPatch(final Object body);

    default Response patch(final HttpEntity httpEntity) {
        return request(HttpMethod.PATCH, httpEntity);
    }

    default <T> ResponseHandler<T> patch(final HttpEntity httpEntity, Class<T> responseType) {
        return request(HttpMethod.PATCH, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> patch(final HttpEntity httpEntity, TypeReference<T> responseType) {
        return request(HttpMethod.PATCH, httpEntity, responseType);
    }

    default <T> ResponseHandler<T> patch(final String payload, Class<T> responseType) {
        return request(HttpMethod.PATCH, payload, responseType);
    }

    <T> ResponseHandler<T> patch(final Object body, Class<T> responseType);

    default <T> ResponseHandler<T> patch(final String payload, TypeReference<T> responseType) {
        return request(HttpMethod.PATCH, payload, responseType);
    }

    <T> ResponseHandler<T> patch(final Object body, TypeReference<T> responseType);

    default Response patch(final String payload) {
        return request(HttpMethod.PATCH, payload);
    }

    Response patch(final Object body);

    default <T> ResponseHandler<T> patch(Class<T> responseType) {
        return request(HttpMethod.PATCH, responseType);
    }

    default <T> ResponseHandler<T> patch(TypeReference<T> responseType) {
        return request(HttpMethod.PATCH, responseType);
    }

    default Response trace() {
        return request(HttpMethod.TRACE);
    }

    default ResponseHandler<?> rawTrace() {
        return rawRequest(HttpMethod.TRACE);
    }

    default <T> ResponseHandler<T> trace(Class<T> responseType) {
        return request(HttpMethod.TRACE, responseType);
    }

    default <T> ResponseHandler<T> trace(TypeReference<T> responseType) {
        return request(HttpMethod.TRACE, responseType);
    }
}
