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
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRetryableRequestTest {

    @RegisterExtension
    static WireMockExtension wireMockRule = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(8080))
            .build();

    private final RetryContext retryContext = new RetryContext() {
        @Override
        public int getRetryCount() {
            return 2;
        }

        @Override
        public int getRetryDelay(Response response) {
            return 0;
        }

        @Override
        public boolean mustBeRetried(Response response) {
            return response.getCode() == 401;
        }

        @Override
        public WebTarget beforeRetry(WebTarget webTarget) {
            return webTarget.updateHeader(HttpHeaders.AUTHORIZATION, "new header");
        }
    };

    private final HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
            .build();

    @BeforeEach
    public void setup() {
        wireMockRule.stubFor(get(urlEqualTo("/header"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("old header"))
                .willReturn(aResponse().withStatus(401)));

        wireMockRule.stubFor(get(urlEqualTo("/header"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("new header"))
                .willReturn(aResponse().withStatus(200)));

        wireMockRule.stubFor(get(urlEqualTo("/header"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("not retryable"))
                .willReturn(aResponse().withStatus(400)));
    }

    @Test
    void changeHeaderOnRetry() {
        assertEquals(
                200,
                httpRequest.retryableTarget("http://localhost:8080/header", retryContext)
                        .addHeader(HttpHeaders.AUTHORIZATION, "old header")
                        .rawGet()
                        .getCode()
        );
    }

    @Test
    void incorrectTestChangeHeaderOnRetry() {
        assertEquals(
                400,
                httpRequest.retryableTarget("http://localhost:8080/header", retryContext)
                        .addHeader(HttpHeaders.AUTHORIZATION, "not retryable")
                        .rawGet()
                        .getCode()
        );
    }
}
