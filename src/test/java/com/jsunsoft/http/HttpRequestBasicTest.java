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

import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestBasicTest {

    private static final HttpRequest HTTP_REQUEST_TO_GET_RESPONSE_CODE =
            HttpRequestBuilder.create(new ClientBuilder().build()).build();

    private static final HttpRequest HTTP_REQUEST_TO_GET_LARGE_RESPONSE =
            HttpRequestBuilder.create(new ClientBuilder().build()).build();

    @Test
    void getResponseCode() {
        assertEquals(HttpStatus.SC_OK, HTTP_REQUEST_TO_GET_RESPONSE_CODE.target("https://en.wikipedia.org/")
                .path("wiki/List_of_least_concern_birds")
                .get()
                .getCode());
    }

    @Test
    void largeResponseTest() {
        ResponseHandler<String> responseHandler = HTTP_REQUEST_TO_GET_LARGE_RESPONSE.target("https://en.wikipedia.org/")
                .path("wiki/List_of_least_concern_birds")
                .get(String.class);
        assertTrue(responseHandler.orElse("").length() > 16348);
    }

    @Test
    void getResponseCodeImmutable() {
        assertEquals(HttpStatus.SC_OK, HTTP_REQUEST_TO_GET_RESPONSE_CODE.immutableTarget("https://en.wikipedia.org/")
                .path("wiki/List_of_least_concern_birds")
                .get()
                .getCode());
    }

    @Test
    void largeResponseTestImmutable() {
        ResponseHandler<String> responseHandler = HTTP_REQUEST_TO_GET_LARGE_RESPONSE.immutableTarget("https://en.wikipedia.org/")
                .path("wiki/List_of_least_concern_birds")
                .get(String.class);
        assertTrue(responseHandler.orElse("").length() > 16348);
    }
}
