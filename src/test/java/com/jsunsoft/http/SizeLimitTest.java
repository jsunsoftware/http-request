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
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class SizeLimitTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private CloseableHttpClient client;
    private HttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        client = new ClientBuilder().build();
        httpRequest = HttpRequestBuilder.create(client)
                .setMaxResponseBodySizeBytes(1024)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    private String httpUri(String path) {
        return server.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    @Test
    void throwsExceptionWhenStringResponseExceedsLimit() {
        String oversizedBody = "a".repeat(2048);
        server.stubFor(get(urlEqualTo("/large-string"))
                .willReturn(aResponse().withStatus(200).withBody(oversizedBody)));

        ResponseException exception = assertThrows(ResponseException.class, () ->
                httpRequest.target(httpUri("/large-string")).get(String.class).orElseThrow()
        );

        // Verify that the cause of the exception is InvalidContentLengthException
        assertNotNull(exception.getCause(), "Exception cause should not be null");
        assertTrue(exception.getCause() instanceof InvalidContentLengthException,
                "Expected cause to be InvalidContentLengthException, but was: " + exception.getCause().getClass().getName());
    }

    @Test
    void bodyExactlyAtTheLimitIsAccepted() {
        // Boundary case: a body of EXACTLY maxBytes must succeed — the user explicitly said this
        // size is acceptable. Regression guard for the `setMaxCount(maxBytes + 1)` offset in
        // BoundedHttpEntity#getContent — without the +1, a body of size maxBytes would trip the
        // BoundedInputStream cap on the next read after consuming the last byte and throw, even
        // though it fits the contract.
        String body = "x".repeat(1024); // exactly the configured limit
        server.stubFor(get(urlEqualTo("/exact"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        String result = httpRequest.target(httpUri("/exact")).get(String.class).orElseThrow();
        assertEquals(body, result);
    }

    @Test
    void bodyOneOverLimitThrows() {
        // Mirror of the above: maxBytes + 1 must trip the cap. Together these two tests pin the
        // boundary semantics so the +1 offset can't drift in either direction.
        String body = "x".repeat(1025); // one byte over the 1024 limit
        server.stubFor(get(urlEqualTo("/over-by-one"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        ResponseException exception = assertThrows(ResponseException.class, () ->
                httpRequest.target(httpUri("/over-by-one")).get(String.class).orElseThrow());

        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof InvalidContentLengthException,
                "Expected InvalidContentLengthException, got: " + exception.getCause().getClass().getName());
    }

    @Test
    void throwsExceptionWhenJsonResponseExceedsLimit() {
        String oversizedJson = "{\"data\":\"" + "a".repeat(2048) + "\"}";
        server.stubFor(get(urlEqualTo("/large-json"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(oversizedJson)));

        ResponseException exception = assertThrows(ResponseException.class, () ->
                httpRequest.target(httpUri("/large-json")).get(DummyData.class).orElseThrow()
        );

        // Verify that the cause of the exception is InvalidContentLengthException
        assertNotNull(exception.getCause(), "Exception cause should not be null");
        assertTrue(exception.getCause() instanceof InvalidContentLengthException,
                "Expected cause to be InvalidContentLengthException, but was: " + exception.getCause().getClass().getName());
    }

    static class DummyData {
        public String data;
    }
}
