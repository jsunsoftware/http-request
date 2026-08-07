/*
 * Copyright (c) 2026. Benik Arakelyan
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.hc.core5.http.HttpStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@link ResponseHandler#getOriginalCode()} contract:
 * <ul>
 *     <li>when the library synthesizes a client-side status (deserialization failure → 502),
 *         the original server code is preserved;</li>
 *     <li>when the request failed before any response was received (transport failure), it
 *         returns {@link ResponseHandler#getCode()} — never a sentinel like {@code -1}.</li>
 * </ul>
 */
class ResponseHandlerOriginalCodeTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @Test
    void transportFailure_originalCodeEqualsCode() {
        HttpRequest httpRequest = HttpRequestBuilder.create(
                new ClientBuilder().setConnectTimeout(200).build()).build();

        // Nothing listens on port 1 — connection is refused before any response exists.
        ResponseHandler<?> rh = httpRequest.target("http://127.0.0.1:1/unreachable").rawGet();

        assertEquals(SC_SERVICE_UNAVAILABLE, rh.getCode());
        assertEquals(rh.getCode(), rh.getOriginalCode(),
                "without a received response, getOriginalCode() must return getCode(), not a sentinel");
    }

    @Test
    void deserializationFailure_preservesOriginalServerCode() {
        wm.stubFor(get(urlEqualTo("/bad-json"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("this is not json")));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build()).build();

        ResponseHandler<Bean> rh = httpRequest
                .target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("bad-json")
                .get(Bean.class);

        assertEquals(SC_BAD_GATEWAY, rh.getCode(), "deserialization failure is synthesized as 502");
        assertEquals(SC_OK, rh.getOriginalCode(), "the real server code must remain visible");
    }

    @Test
    void success_originalCodeEqualsCode() {
        wm.stubFor(get(urlEqualTo("/ok"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("ok")));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build()).build();

        ResponseHandler<String> rh = httpRequest
                .target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("ok")
                .get(String.class);

        assertEquals(SC_OK, rh.getCode());
        assertEquals(SC_OK, rh.getOriginalCode());
    }

    static class Bean {
        public int count;
    }
}
