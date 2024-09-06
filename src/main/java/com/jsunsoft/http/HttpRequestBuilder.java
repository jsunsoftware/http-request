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

import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * Creates a new instance of HttpRequestBuilder.
     *
     * @param closeableHttpClient the HTTP client to use
     * @return a new instance of HttpRequestBuilder
     */
    public static HttpRequestBuilder create(CloseableHttpClient closeableHttpClient) {
        return new HttpRequestBuilder(closeableHttpClient);
    }

    /**
     * Adds a default header to be included in all requests.
     *
     * @param name  the name of the header
     * @param value the value of the header
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultHeader(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultHeader(new BasicHeader(name, value));
    }

    /**
     * Adds a default header to be included in all requests.
     *
     * @param header the header to add
     * @return the current instance of HttpRequestBuilder
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
     * Adds multiple default headers to be included in all requests.
     *
     * @param headers the headers to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultHeaders(Header... headers) {
        ArgsCheck.notNull(headers, "headers");

        Arrays.stream(headers).forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Adds multiple default headers to be included in all requests.
     *
     * @param headers the headers to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultHeaders(Collection<? extends Header> headers) {
        ArgsCheck.notNull(headers, "headers");

        headers.forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Sets the content type header for all requests.
     *
     * @param contentType the content type to set
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addContentType(ContentType contentType) {
        return addDefaultHeader(CONTENT_TYPE, contentType.toString());
    }

    /**
     * Adds a default request parameter to be included in all requests.
     *
     * @param name  the name of the parameter
     * @param value the value of the parameter
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultRequestParameter(new BasicNameValuePair(name, value));
    }

    /**
     * Adds multiple default request parameters to be included in all requests.
     *
     * @param nameValues the parameters to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(NameValuePair... nameValues) {
        int nameValuesLength = ArgsCheck.notNull(nameValues, "nameValues").length;
        Args.check(nameValuesLength != 0, "Length of parameter can't be ZERO");

        Arrays.stream(nameValues).forEach(this::addDefaultRequestParameter);
        return this;
    }

    /**
     * Adds a default request parameter to be included in all requests.
     *
     * @param nameValuePair the parameter to add
     * @return the current instance of HttpRequestBuilder
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
     * Adds multiple default request parameters to be included in all requests.
     *
     * @param defaultParameters the parameters to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(Map<String, String> defaultParameters) {
        ArgsCheck.notNull(defaultParameters, "defaultParameters");

        defaultParameters.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                .forEach(this::addDefaultRequestParameter);

        return this;
    }

    /**
     * Adds multiple default request parameters to be included in all requests.
     *
     * @param defaultRequestParameters the parameters to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultRequestParameter(Collection<? extends NameValuePair> defaultRequestParameters) {
        ArgsCheck.notNull(defaultRequestParameters, "defaultRequestParameters");

        if (this.defaultRequestParameters == null) {
            this.defaultRequestParameters = new ArrayList<>();
        }
        defaultRequestParameters.forEach(this::addDefaultRequestParameter);

        return this;
    }

    /**
     * Adds a response body reader.
     *
     * @param responseBodyReader the response body reader to add
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addBodyReader(ResponseBodyReader<?> responseBodyReader) {
        responseBodyReaderConfigBuilder.addResponseBodyReader(responseBodyReader);
        return this;
    }

    /**
     * Sets the default response body reader.
     *
     * @param defaultResponseBodyReader the default response body reader to set
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultResponseBodyReader(ResponseBodyReader<?> defaultResponseBodyReader) {
        responseBodyReaderConfigBuilder.setDefaultResponseBodyReader(defaultResponseBodyReader);
        return this;
    }

    /**
     * Enables the default body reader.
     *
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder enableDefaultBodyReader() {
        responseBodyReaderConfigBuilder.setUseDefaultBodyReader(true);
        return this;
    }

    /**
     * Disables the default body reader.
     *
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder disableDefaultBodyReader() {
        responseBodyReaderConfigBuilder.setUseDefaultBodyReader(false);
        return this;
    }

    /**
     * Adds a date deserialization pattern for the default deserializer.
     *
     * @param dateType the date type
     * @param pattern  the pattern to use for deserialization
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder addDefaultDateDeserializationPattern(Class<?> dateType, String pattern) {
        responseBodyReaderConfigBuilder.addDateDeserializationPattern(dateType, pattern);
        return this;
    }

    /**
     * Sets the default JSON mapper for response body deserialization.
     *
     * @param defaultJsonMapper the JSON mapper to set
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultJsonMapper(ObjectMapper defaultJsonMapper) {
        requestBodySerializeConfigBuilder.setDefaultJsonMapper(defaultJsonMapper);
        responseBodyReaderConfigBuilder.setDefaultJsonMapper(defaultJsonMapper);
        return this;
    }

    /**
     * Sets the default XML mapper for response body deserialization.
     *
     * @param defaultXmlMapper the XML mapper to set
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder setDefaultXmlMapper(ObjectMapper defaultXmlMapper) {
        requestBodySerializeConfigBuilder.setDefaultXmlMapper(defaultXmlMapper);
        responseBodyReaderConfigBuilder.setDefaultXmlMapper(defaultXmlMapper);
        return this;
    }

    /**
     * Adds basic authentication to the request.
     *
     * @param username the username
     * @param password the password
     * @return the current instance of HttpRequestBuilder
     */
    public HttpRequestBuilder basicAuth(String username, String password) {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
        return addDefaultHeader(AUTHORIZATION, authHeader);
    }

    /**
     * Builds the HttpRequest instance.
     *
     * @return the HttpRequest instance
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
