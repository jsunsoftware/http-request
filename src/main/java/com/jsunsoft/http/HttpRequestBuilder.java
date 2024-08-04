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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Args;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * Http request builder
 *
 * @see HttpRequest
 */
public class HttpRequestBuilder {
    private final CloseableHttpClient closeableHttpClient;

    private List<NameValuePair> defaultRequestParameters;
    private Collection<Header> defaultHeaders;
    private final ResponseBodyReaderConfig.Builder responseBodyReaderConfigBuilder = ResponseBodyReaderConfig.create();
    private final RequestBodySerializeConfig.Builder requestBodySerializeConfigBuilder = RequestBodySerializeConfig.create();

    private HttpRequestBuilder(CloseableHttpClient closeableHttpClient) {
        this.closeableHttpClient = ArgsCheck.notNull(closeableHttpClient, "closeableHttpClient");
    }

    public static HttpRequestBuilder create(CloseableHttpClient closeableHttpClient) {
        return new HttpRequestBuilder(closeableHttpClient);
    }

    /**
     * Header needs to be the same for all requests which go through the built HttpRequest
     *
     * @param name  name of header. Can't be null
     * @param value value of header
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultHeader(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultHeader(new BasicHeader(name, value));
    }

    /**
     * Header needs to be the same for all requests which go through the built HttpRequest
     *
     * @param header header instance. Can't be null
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultHeader(Header header) {
        ArgsCheck.notNull(header, "header");

        if (defaultHeaders == null) {
            defaultHeaders = new ArrayList<>();
        }
        defaultHeaders.add(header);
        return this;
    }

    /**
     * Headers need to be the same for all requests which go through the built HttpRequest
     *
     * @param headers varargs of headers
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultHeaders(Header... headers) {
        ArgsCheck.notNull(headers, "headers");

        Arrays.stream(headers).forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Headers need to be the same for all requests which go through the built HttpRequest
     *
     * @param headers collections of headers
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultHeaders(Collection<? extends Header> headers) {
        ArgsCheck.notNull(headers, "headers");

        headers.forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Sets content type to header
     *
     * @param contentType content type of request header
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addContentType(ContentType contentType) {
        return addDefaultHeader(CONTENT_TYPE, contentType.toString());
    }

    /**
     * Parameter needs to be add  for all requests which go through the built HttpRequest
     *
     * @param name  key
     * @param value value
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultRequestParameter(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultRequestParameter(new BasicNameValuePair(name, value));
    }

    /**
     * Parameters need to be add  for all requests which go through the built HttpRequest
     *
     * @param nameValues nameValues
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultRequestParameter(NameValuePair... nameValues) {
        int nameValuesLength = ArgsCheck.notNull(nameValues, "nameValues").length;
        Args.check(nameValuesLength != 0, "Length of parameter can't be ZERO");

        Arrays.stream(nameValues).forEach(this::addDefaultRequestParameter);
        return this;
    }


    /**
     * Parameter needs to be add  for all requests which go through the built HttpRequest
     *
     * @param nameValuePair nameValuePair
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultRequestParameter(NameValuePair nameValuePair) {
        ArgsCheck.notNull(nameValuePair, "nameValuePair");
        if (this.defaultRequestParameters == null) {
            this.defaultRequestParameters = new ArrayList<>();
        }

        this.defaultRequestParameters.add(nameValuePair);
        return this;
    }

    /**
     * Parameters needs to be add  for all requests which go through the built HttpRequest
     *
     * @param defaultParameters defaultParameters
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultRequestParameter(Map<String, String> defaultParameters) {
        ArgsCheck.notNull(defaultParameters, "defaultParameters");

        defaultParameters.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                .forEach(this::addDefaultRequestParameter);

        return this;
    }

    /**
     * Parameters needs to be add  for all requests which go through the built HttpRequest
     *
     * @param defaultRequestParameters defaultRequestParameters
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultRequestParameter(Collection<? extends NameValuePair> defaultRequestParameters) {
        ArgsCheck.notNull(defaultRequestParameters, "defaultRequestParameters");

        if (this.defaultRequestParameters == null) {
            this.defaultRequestParameters = new ArrayList<>();
        }
        defaultRequestParameters.forEach(this::addDefaultRequestParameter);

        return this;
    }

    public HttpRequestBuilder addBodyReader(ResponseBodyReader<?> responseBodyReader) {

        responseBodyReaderConfigBuilder.addResponseBodyReader(responseBodyReader);

        return this;
    }

    public HttpRequestBuilder setDefaultResponseBodyReader(ResponseBodyReader<?> defaultResponseBodyReader) {
        responseBodyReaderConfigBuilder.setDefaultResponseBodyReader(defaultResponseBodyReader);

        return this;
    }

    public HttpRequestBuilder enableDefaultBodyReader() {
        responseBodyReaderConfigBuilder.setUseDefaultBodyReader(true);
        return this;
    }

    public HttpRequestBuilder disableDefaultBodyReader() {
        responseBodyReaderConfigBuilder.setUseDefaultBodyReader(false);
        return this;
    }

    /**
     * Method defines by which pattern dates must be deserialized when default deserializer used.
     * For example, you can do
     * <pre>
     *    httpRequestBuilder.addDateDeserializationPattern(LocalDateTime.class, "yyyy-MM-dd");
     * </pre>
     *
     * <p>
     * Note: if methods {@link #setDefaultJsonMapper(ObjectMapper)} or {@link #setDefaultXmlMapper(ObjectMapper)} called
     * result of this method will be ignored for that type of response.
     * </p>
     * <p>
     * Default patterns are { LocalTime - HH:mm:ss, LocalDate - dd/MM/yyyy, LocalDateTime - dd/MM/yyyy HH:mm:ss}
     * </p>
     *
     * @param dateType date type e.g {@code LocalDateTime.class}
     * @param pattern  pattern by which date with given type must be deserialized
     *
     * @return HttpRequestBuilder instance
     *
     * @see com.fasterxml.jackson.databind.ObjectMapper#configOverride(Class)
     * @see com.fasterxml.jackson.databind.cfg.MutableConfigOverride#setFormat(JsonFormat.Value)
     */
    public HttpRequestBuilder addDefaultDateDeserializationPattern(Class<?> dateType, String pattern) {
        responseBodyReaderConfigBuilder.addDateDeserializationPattern(dateType, pattern);

        return this;
    }

    /**
     * Set object mapper for default response body deserialization when response content type is {@link ContentType#APPLICATION_JSON}
     *
     * <p>
     * </p>
     * <p>
     * Note: if this method called the result of addDefaultDateDeserializationPattern will be ignored for {@link ContentType#APPLICATION_JSON}.
     * </p>
     *
     * @param defaultJsonMapper the ObjectMapper instance
     *
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder setDefaultJsonMapper(ObjectMapper defaultJsonMapper) {
        requestBodySerializeConfigBuilder.setDefaultJsonMapper(defaultJsonMapper);
        responseBodyReaderConfigBuilder.setDefaultJsonMapper(defaultJsonMapper);

        return this;
    }

    /**
     * Set object mapper for default response body deserialization when response content type is {@link ContentType#APPLICATION_XML}
     *
     * @param defaultXmlMapper Mainly the {@link XmlMapper} instance
     *
     * @return HttpRequestBuilder instance
     *
     * <p>
     * Note: if this method called the result of addDefaultDateDeserializationPattern will be ignored for {@link ContentType#APPLICATION_XML}.
     * </p>
     */
    public HttpRequestBuilder setDefaultXmlMapper(ObjectMapper defaultXmlMapper) {
        requestBodySerializeConfigBuilder.setDefaultXmlMapper(defaultXmlMapper);
        responseBodyReaderConfigBuilder.setDefaultXmlMapper(defaultXmlMapper);

        return this;
    }


    /**
     * Basic Authentication - sending the Authorization header.
     *
     * @param username username
     * @param password password
     *
     * @return ClientBuilder instance
     */
    public HttpRequestBuilder basicAuth(String username, String password) {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
        return addDefaultHeader(AUTHORIZATION, authHeader);
    }

    /**
     * Build the HttpRequest instance
     *
     * @return HttpRequest instance
     */
    public HttpRequest build() {
        if (defaultHeaders == null) {
            defaultHeaders = Collections.emptyList();
        }

        if (defaultRequestParameters == null) {
            defaultRequestParameters = Collections.emptyList();
        }

        return new BasicHttpRequest(closeableHttpClient, defaultHeaders, defaultRequestParameters, responseBodyReaderConfigBuilder.build(), requestBodySerializeConfigBuilder.build());
    }
}
