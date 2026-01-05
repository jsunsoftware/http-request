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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRequestBasicTest {

    @RegisterExtension
    static WireMockExtension wireMockRule = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private static final HttpRequest HTTP_REQUEST =
            HttpRequestBuilder.create(new ClientBuilder().build()).build();

    @Test
    void largeResponseTest() {
        String responseBody = "a".repeat(20000);
        wireMockRule.stubFor(post(urlEqualTo("/anything"))
                .willReturn(aResponse().withStatus(200).withBody(responseBody)));

        ResponseHandler<String> rh = HTTP_REQUEST.target(wireMockRule.getRuntimeInfo().getHttpBaseUrl())
                .path("anything")
                .post("a".repeat(50000), String.class);

        assertEquals(HttpStatus.SC_OK, rh.getCode());
        assertTrue(rh.orElse("").length() > 16348);
    }
}
