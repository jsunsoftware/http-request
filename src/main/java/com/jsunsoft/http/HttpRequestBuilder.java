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

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.Args;

import java.util.*;

import static org.apache.http.HttpHeaders.CONTENT_TYPE;

public class HttpRequestBuilder {
    private final CloseableHttpClient closeableHttpClient;

    private List<NameValuePair> defaultRequestParameters;
    private Collection<Header> defaultHeaders;

    private HttpRequestBuilder(CloseableHttpClient closeableHttpClient) {
        this.closeableHttpClient = closeableHttpClient;
    }

    public static HttpRequestBuilder create(CloseableHttpClient closeableHttpClient) {
        return new HttpRequestBuilder(closeableHttpClient);
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param name  name of header. Can't be null
     * @param value value of header
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultHeader(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultHeader(new BasicHeader(name, value));
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param header header instance
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultHeader(Header header) {
        if (defaultHeaders == null) {
            defaultHeaders = new ArrayList<>();
        }
        defaultHeaders.add(header);
        return this;
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param headers varargs of headers
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultHeaders(Header... headers) {
        Arrays.stream(headers).forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Header needs to be the same for all requests
     *
     * @param headers collections of headers
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultHeaders(Collection<? extends Header> headers) {
        headers.forEach(this::addDefaultHeader);
        return this;
    }

    /**
     * Sets content type to header
     *
     * @param contentType content type of request header
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addContentType(ContentType contentType) {
        return addDefaultHeader(CONTENT_TYPE, contentType.toString());
    }

    /**
     * Parameter needs to be add  for all requests.
     *
     * @param name  key
     * @param value value
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultRequestParameter(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return addDefaultRequestParameter(new BasicNameValuePair(name, value));
    }

    /**
     * Parameters needs to be add  for all requests.
     *
     * @param nameValues nameValues
     * @return HttpRequestBuilder instance
     */
    public HttpRequestBuilder addDefaultRequestParameter(NameValuePair... nameValues) {
        int nameValuesLength = ArgsCheck.notNull(nameValues, "nameValues").length;
        Args.check(nameValuesLength != 0, "Length of parameter can't be ZERO");

        Arrays.stream(nameValues).forEach(this::addDefaultRequestParameter);
        return this;
    }


    /**
     * Parameters needs to be add  for all requests.
     *
     * @param nameValuePair nameValuePair
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
     * Parameters needs to be add  for all requests.
     *
     * @param defaultParameters defaultParameters
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
     * Parameters needs to be add  for all requests.
     *
     * @param defaultRequestParameters defaultRequestParameters
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

    public HttpRequest build() {
        if (defaultHeaders == null) {
            defaultHeaders = Collections.emptyList();
        }

        if (defaultRequestParameters == null) {
            defaultRequestParameters = Collections.emptyList();
        }
        return new BasicHttpRequest(closeableHttpClient, defaultHeaders, defaultRequestParameters);
    }
}
