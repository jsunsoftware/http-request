/*
 * Copyright (c) 2017-2021. Benik Arakelyan
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

import org.apache.hc.core5.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;

public class HttpRequestBasicTest {
    private static final String URI_STRING = "https://en.wikipedia.org/wiki/List_of_least_concern_birds";

    private static final HttpRequest HTTP_REQUEST_TO_GET_RESPONSE_CODE =
            HttpRequestBuilder.create(new ClientBuilder().build()).build();

    private static final HttpRequest HTTP_REQUEST_TO_GET_LARGE_RESPONSE =
            HttpRequestBuilder.create(new ClientBuilder().build()).build();

    @Test
    public void getResponseCode() {
        Assert.assertEquals(HttpStatus.SC_OK, HTTP_REQUEST_TO_GET_RESPONSE_CODE.target("https://en.wikipedia.org/")
                .appendPath("wiki/List_of_least_concern_birds")
                .get()
                .getCode());
    }

    @Test
    public void largeResponseTest() {
        ResponseHandler<String> responseHandler = HTTP_REQUEST_TO_GET_LARGE_RESPONSE.target("https://en.wikipedia.org/")
                .appendPath("wiki/List_of_least_concern_birds")
                .get(String.class);
        Assert.assertTrue(responseHandler.orElse("").length() > 16348);
    }

    @Test
    public void getResponseCodeImmutable() {
        Assert.assertEquals(HttpStatus.SC_OK, HTTP_REQUEST_TO_GET_RESPONSE_CODE.immutableTarget("https://en.wikipedia.org/")
                .appendPath("wiki/List_of_least_concern_birds")
                .get()
                .getCode());
    }

    @Test
    public void largeResponseTestImmutable() {
        ResponseHandler<String> responseHandler = HTTP_REQUEST_TO_GET_LARGE_RESPONSE.immutableTarget("https://en.wikipedia.org/")
                .appendPath("wiki/List_of_least_concern_birds")
                .get(String.class);
        Assert.assertTrue(responseHandler.orElse("").length() > 16348);
    }
}
