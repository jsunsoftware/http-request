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

import java.io.ByteArrayOutputStream;
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
    void writeToOnTheBoundedEntityEnforcesTheCap() throws IOException {
        // Pins the BoundedHttpEntity#writeTo override against accidental removal: a caller that
        // bypasses getContent() and spools the body straight to an OutputStream (e.g. to disk)
        // must still see InvalidContentLengthException for an oversize body. Without the override,
        // HttpEntityWrapper#writeTo would delegate straight to the wrapped entity's writeTo and
        // silently push the full payload through, bypassing the size cap.
        String body = "x".repeat(2048); // 2× the 1024 limit
        server.stubFor(get(urlEqualTo("/writeTo-overflow"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        try (Response response = httpRequest.target(httpUri("/writeTo-overflow")).get()) {
            assertEquals(200, response.getCode());
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            assertThrows(InvalidContentLengthException.class,
                    () -> response.getEntity().writeTo(sink));
            // BoundedInputStream is configured with maxCount = maxSize + 1 (see BoundedHttpEntity
            // Javadoc — the +1 keeps "exactly maxSize bytes" as a valid response and shifts the
            // throw to the byte that exceeds the cap), so the sink legitimately contains up to
            // maxSize + 1 bytes by the time we throw. What must hold: we did NOT spool the full
            // oversize body before noticing.
            assertTrue(sink.size() < body.length(),
                    "writeTo must abort before spooling the full oversize body, got " + sink.size() + " of " + body.length() + " bytes");
            assertTrue(sink.size() <= 1025,
                    "writeTo must not exceed maxSize + 1 (BoundedInputStream's trigger threshold), got " + sink.size());
        }
    }

    @Test
    void writeToOnUnderLimitBodyCompletesCleanly() throws IOException {
        // Counterpart to writeToOnTheBoundedEntityEnforcesTheCap: an under-limit body must round-trip
        // cleanly through writeTo without false positives.
        String body = "y".repeat(500);
        server.stubFor(get(urlEqualTo("/writeTo-under"))
                .willReturn(aResponse().withStatus(200).withBody(body)));

        try (Response response = httpRequest.target(httpUri("/writeTo-under")).get()) {
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            response.getEntity().writeTo(sink);
            assertEquals(body, sink.toString("UTF-8"));
        }
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
