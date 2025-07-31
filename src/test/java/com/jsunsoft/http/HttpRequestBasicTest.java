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

import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;

public class HttpRequestBasicTest {

    private static final HttpRequest HTTP_REQUEST =
            HttpRequestBuilder.create(new ClientBuilder().build()).build();

    @Test
    public void largeResponseTest() {
        ResponseHandler<String> rh = HTTP_REQUEST.target("https://httpbin.org")
                .path("anything")
                .post(repeat("a", 50000), String.class);

        Assert.assertEquals(HttpStatus.SC_OK, rh.getCode());
        Assert.assertTrue(rh.orElse("").length() > 16348);
    }

    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}
