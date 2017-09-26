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

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;

public class HttpRequestBasicTest {

    private static final HttpRequest<?> HTTP_REQUEST_TO_GET_RESPONSE_CODE =
            HttpRequestBuilder.createGet("https://www.jsunsoft.com/").build();

    private static final HttpRequest<String> HTTP_REQUEST_TO_GET_LARGE_RESPONSE =
            HttpRequestBuilder.createGet("https://en.wikipedia.org/wiki/List_of_least_concern_birds", String.class)
                    .build();

    @Test
    public void getResponseCode() {
        Assert.assertEquals(HttpStatus.SC_OK, HTTP_REQUEST_TO_GET_RESPONSE_CODE.execute().getStatusCode());
    }

    @Test
    public void largeResponseTest() {
        ResponseHandler<String> responseHandler = HTTP_REQUEST_TO_GET_LARGE_RESPONSE.execute();
        Assert.assertTrue(responseHandler.orElse("").length() > 16348);
    }
}
