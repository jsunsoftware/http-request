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

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.Args;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collection;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * HttpRequest objects are immutable they can be shared.
 *
 * @param <T> Type of expected successful response
 */
public interface HttpRequest<T> {
    /**
     * Sends request with body. (Without params). The MIME type default
     * same with MIME type of {@link org.apache.http.entity.StringEntity#StringEntity(String, String)} and the charset defaults to UTF-8.
     *
     * @param payload value of body
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a<b>503</b>.
     * If failed deserialization of response body status code is a <b>502</b>
     * @throws NullPointerException when param payload is null
     */
    ResponseHandler<T> executeWithBody(String payload);

    /**
     * Sends request by queryString of request. {@code httpServletRequest.getQueryString()}
     *
     * @param queryString queryString
     * @param charset     charset
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a<b>503</b>.
     * If failed deserialization of response body status code is a <b>502</b>
     * @throws UnsupportedCharsetException Unchecked exception thrown when no support is available
     *                                     for a requested charset.
     * @throws NullPointerException        when one of arguments is null
     */
    ResponseHandler<T> executeWithQuery(String queryString, Charset charset);

    /**
     * Sends request
     *
     * @param params parameters to send
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a <b>503</b>.
     * If failed deserialization of response body status code is a <b>502</b>
     * @throws NullPointerException when param params is null
     */
    ResponseHandler<T> execute(NameValuePair... params);

    HttpMethod getHttpMethod();

    URI getUri();

    /**
     * Sends request by queryString of request. {@code httpServletRequest.getQueryString()}. Default charset is "UTF-8".
     *
     * @param queryString queryString
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a<b>503</b>.
     * If failed deserialization of response body status code is a <b>502</b>
     * @throws UnsupportedCharsetException Unchecked exception thrown when no support is available
     *                                     for a requested charset.
     * @see HttpRequest#executeWithQuery(String, Charset)
     */
    default ResponseHandler<T> executeWithQuery(String queryString) {
        return executeWithQuery(queryString, UTF_8);
    }


    /**
     * @param queryString       queryString
     * @param characterEncoding characterEncoding
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a<b>503</b>.
     * If failed deserialization of response body status code is a <b>502</b>
     * @see HttpRequest#executeWithQuery(String, Charset)
     */
    default ResponseHandler<T> executeWithQuery(String queryString, String characterEncoding) {
        return executeWithQuery(queryString, Charset.forName(characterEncoding));
    }

    /**
     * Sends request with one parameter (name: value)
     *
     * @param name  parameter key
     * @param value parameter value
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a <b>503</b>.
     * If failed deserialization of response body <b>502</b>
     * @throws NullPointerException when param name is null
     */
    default ResponseHandler<T> execute(String name, String value) {
        ArgsCheck.notNull(name, "name");
        return execute(new BasicNameValuePair(name, value));
    }

    /**
     * Sends request.
     *
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a <b>503</b>.
     * If failed deserialization of response body <b>502</b>
     */
    default ResponseHandler<T> execute() {
        return execute(Constants.EMPTY_NAME_VALUE_PAIRS);
    }

    /**
     * Sends request as [nameValues[0]: nameValues[1], nameValues[2]: nameValues[3], ... e.t.c.] <br> So
     * name1 = nameValues[0], value1 = nameValues[1]; name2 = nameValues[1], value2 = nameValues[2] ... e.t.c.
     *
     * @param nameValues array of nameValue
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a <b>503</b>.
     * If failed deserialization of response body <b>502</b>
     * @throws IllegalArgumentException When length of parameter nameValues is odd or ZERO.
     * @throws NullPointerException     when param nameValues is null
     */
    default ResponseHandler<T> execute(String... nameValues) {
        int nameValuesLength = ArgsCheck.notNull(nameValues, "nameValues").length;
        Args.check(nameValuesLength != 0, "Length of parameter can't be ZERO");
        Args.check(nameValuesLength % 2 == 0, "Length of nameValues can't be odd");

        int end = nameValuesLength - 2;
        NameValuePair[] nameValuePairs = new NameValuePair[nameValuesLength / 2];

        int k = 0;
        for (int i = 0; i <= end; i += 2) {
            nameValuePairs[k++] = new BasicNameValuePair(nameValues[i], nameValues[i + 1]);
        }

        return execute(nameValuePairs);
    }

    /**
     * Sends request
     *
     * @param params parameters to send
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a<b>503</b>.
     * If failed deserialization of response body status code is a <b>502</b>
     * @throws NullPointerException when param params is null
     */
    default ResponseHandler<T> execute(Collection<? extends NameValuePair> params) {
        ArgsCheck.notNull(params, "params");
        return execute(params.toArray(Constants.EMPTY_NAME_VALUE_PAIRS));
    }

    /**
     * Sends request
     *
     * @param params parameters to send
     * @return Instance of {@link ResponseHandler}. If connection failure status code is a<b>503</b>.
     * If failed deserialization of response body status code is a <b>502</b>
     * @throws NullPointerException when param params is null
     */
    default ResponseHandler<T> execute(Map<String, String> params) {
        ArgsCheck.notNull(params, "params");
        return execute(params.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                .toArray(NameValuePair[]::new));
    }

    /**
     * @param uri the new uri to request. Should not be null.
     * @return Returns a copy of this BasicHttpRequest instance with the specified uri of uri.
     */
    BasicHttpRequest<T> path(URI uri);
}
