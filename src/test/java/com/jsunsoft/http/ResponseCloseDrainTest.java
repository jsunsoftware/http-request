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
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the close-drain contract when a response body-size cap is configured: closing a
 * {@link Response} with a small unread remainder must drain it so the pooled connection stays
 * reusable, while a remainder that is over the cap, over the (configurable) drain-size
 * allowance, or not drained within the (configurable, hard) drain timeout is not read further —
 * the connection is discarded instead. Also pins the sentinel semantics of
 * {@code setMaxResponseDrainSizeBytes} ({@code 0} = never drain, negative = unlimited) and the
 * build-time validation tying the drain options to the response body size cap. Reuse is
 * observed via the pool's available-connection count.
 */
class ResponseCloseDrainTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private String uri(String path) {
        return wm.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    private static long available(ClientBuilder.HttpClientWithResourcesWrapper resources) {
        return ((PoolingHttpClientConnectionManager) resources.getConnectionManager()).getTotalStats().getAvailable();
    }

    @Test
    void close_smallUnreadRemainder_returnsConnectionToPool() throws IOException {
        wm.stubFor(get(urlEqualTo("/small"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(200))));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024)
                    .build();

            try (Response response = httpRequest.target(uri("/small")).get()) {
                assertEquals(200, response.getCode());
                // body deliberately unread — a 200-byte remainder is far under the cap
            }

            assertEquals(1, available(resources),
                    "a small unread remainder must be drained on close so the connection returns to the pool");
        }
    }

    @Test
    void close_fullyReadBody_reusesConnection() throws IOException {
        wm.stubFor(get(urlEqualTo("/read"))
                .willReturn(aResponse().withStatus(200).withBody("hello")));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024)
                    .build();

            try (Response response = httpRequest.target(uri("/read")).get()) {
                assertEquals("hello", response.readEntity(String.class));
            }

            assertEquals(1, available(resources), "a fully-read body must keep the connection reusable");
        }
    }

    @Test
    void close_declaredLengthBeyondCap_discardsConnection_withoutThrowing() throws IOException {
        wm.stubFor(get(urlEqualTo("/over-cap"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(100 * 1024))));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024)
                    .build();

            assertDoesNotThrow(() -> {
                try (Response response = httpRequest.target(uri("/over-cap")).get()) {
                    assertEquals(200, response.getCode());
                }
            }, "close() must never surface the cap exception");

            assertEquals(0, available(resources),
                    "an over-cap remainder must not be drained — the connection is discarded");
        }
    }

    @Test
    void close_slowDribblingRemainder_givesUpAtDeadline_andDiscards() throws IOException {
        // 16 KiB (well under both the 1 MB cap and the 64 KiB byte allowance) dribbled over ~3 s:
        // a byte-only drain would sit through the whole dribble to salvage the connection; the
        // time bound must abandon it at the ~100 ms deadline — on a slow link draining costs
        // more than a reconnect.
        wm.stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("x".repeat(16 * 1024))
                        .withChunkedDribbleDelay(20, 3000)));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024 * 1024)
                    .build();

            Response response = httpRequest.target(uri("/slow")).get();
            assertEquals(200, response.getCode());

            long closeStart = System.nanoTime();
            response.close();
            long closeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closeStart);

            assertEquals(0, available(resources),
                    "a slowly-dribbling remainder must be abandoned at the drain deadline and the connection discarded");
            assertTrue(closeMillis < 1500,
                    "close() must give up draining a slow body at the deadline instead of waiting out the dribble, took " + closeMillis + " ms");
        }
    }

    @Test
    void drainSizeZero_neverDrains_discardsUnreadRemainder() throws IOException {
        // Sentinel 0 = never drain: even a tiny remainder the default allowance would salvage
        // must lead to a discard.
        wm.stubFor(get(urlEqualTo("/no-drain"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(200))));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024 * 1024)
                    .setMaxResponseDrainSizeBytes(0)
                    .build();

            try (Response response = httpRequest.target(uri("/no-drain")).get()) {
                assertEquals(200, response.getCode());
            }

            assertEquals(0, available(resources),
                    "drain size 0 must disable draining entirely — unread remainder means discard");
        }
    }

    @Test
    void drainSizeUnlimited_salvagesRemainderBeyondDefaultAllowance() throws IOException {
        // Sentinel negative = unlimited bytes: a 200 KiB remainder (over the 64 KiB default,
        // instant on loopback) is drained and the connection reused. Only the response-size cap
        // and the drain timeout still bound the read.
        wm.stubFor(get(urlEqualTo("/unlimited"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(200 * 1024))));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024 * 1024)
                    .setMaxResponseDrainSizeBytes(-1)
                    .build();

            try (Response response = httpRequest.target(uri("/unlimited")).get()) {
                assertEquals(200, response.getCode());
            }

            assertEquals(1, available(resources),
                    "unlimited drain size must salvage a fast remainder beyond the default allowance");
        }
    }

    @Test
    void remainderExactlyAtDrainAllowance_isSalvaged() throws IOException {
        // Boundary: a remainder of exactly the configured allowance must drain to EOF and keep
        // the connection — the final EOF probe distinguishes "exactly at the bound" from "over".
        wm.stubFor(get(urlEqualTo("/exact-allowance"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(4096))));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024 * 1024)
                    .setMaxResponseDrainSizeBytes(4096)
                    .build();

            try (Response response = httpRequest.target(uri("/exact-allowance")).get()) {
                assertEquals(200, response.getCode());
            }

            assertEquals(1, available(resources),
                    "a remainder of exactly the drain allowance must be salvaged, not discarded");
        }
    }

    @Test
    void customDrainTimeout_salvagesSlowRemainder() throws IOException {
        // A generous timeout lets a slowly-arriving small remainder be drained where the 100 ms
        // default gives up.
        wm.stubFor(get(urlEqualTo("/slow-salvage"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("x".repeat(8 * 1024))
                        .withChunkedDribbleDelay(5, 1000)));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024 * 1024)
                    .setResponseDrainTimeout(Duration.ofSeconds(5))
                    .build();

            try (Response response = httpRequest.target(uri("/slow-salvage")).get()) {
                assertEquals(200, response.getCode());
            }

            assertEquals(1, available(resources),
                    "a generous drain timeout must salvage a slowly-arriving remainder");
        }
    }

    @Test
    void extremeDrainTimeout_doesNotOverflow_closeStillDrainsAndReuses() throws IOException {
        // Duration.toNanos() throws ArithmeticException beyond ~292 years — an extreme (but
        // setter-valid) timeout must saturate, not blow up close().
        wm.stubFor(get(urlEqualTo("/huge-timeout"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(200))));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024 * 1024)
                    .setResponseDrainTimeout(Duration.ofSeconds(Long.MAX_VALUE))
                    .build();

            assertDoesNotThrow(() -> {
                try (Response response = httpRequest.target(uri("/huge-timeout")).get()) {
                    assertEquals(200, response.getCode());
                }
            }, "an extreme drain timeout must not overflow inside close()");

            assertEquals(1, available(resources), "the small remainder must still be drained and the connection reused");
        }
    }

    @Test
    void doubleClose_isIdempotent_andCheap() throws IOException {
        wm.stubFor(get(urlEqualTo("/double-close"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(200))));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024 * 1024)
                    .build();

            Response response = httpRequest.target(uri("/double-close")).get();
            assertEquals(200, response.getCode());
            response.close();
            assertDoesNotThrow(response::close, "a second close() must be a safe no-op");

            assertEquals(1, available(resources), "double close must not disturb the reused connection");
        }
    }

    @Test
    void drainOptions_requireResponseBodySizeCap() {
        try (CloseableHttpClient client = new ClientBuilder().build()) {
            assertThrows(IllegalArgumentException.class,
                    () -> HttpRequestBuilder.create(client).setMaxResponseDrainSizeBytes(1024).build(),
                    "drain size without a response body size cap must be rejected at build()");
            assertThrows(IllegalArgumentException.class,
                    () -> HttpRequestBuilder.create(client).setResponseDrainTimeout(Duration.ofSeconds(1)).build(),
                    "drain timeout without a response body size cap must be rejected at build()");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void setResponseDrainTimeout_rejectsNullAndNonPositive() {
        try (CloseableHttpClient client = new ClientBuilder().build()) {
            HttpRequestBuilder builder = HttpRequestBuilder.create(client);
            assertThrows(NullPointerException.class, () -> builder.setResponseDrainTimeout(null));
            assertThrows(IllegalArgumentException.class, () -> builder.setResponseDrainTimeout(Duration.ZERO));
            assertThrows(IllegalArgumentException.class, () -> builder.setResponseDrainTimeout(Duration.ofMillis(-5)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void close_remainderBeyondDrainAllowance_discardsConnection() throws IOException {
        // Under the cap (1 MB) but far over the fixed close-drain allowance (64 KiB): draining it
        // would cost more than a reconnect, so close() must stop and discard the connection.
        wm.stubFor(get(urlEqualTo("/over-allowance"))
                .willReturn(aResponse().withStatus(200).withBody("x".repeat(200 * 1024))));

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient())
                    .setMaxResponseBodySizeBytes(1024 * 1024)
                    .build();

            assertDoesNotThrow(() -> {
                try (Response response = httpRequest.target(uri("/over-allowance")).get()) {
                    assertEquals(200, response.getCode());
                }
            });

            assertEquals(0, available(resources),
                    "a remainder beyond the drain allowance must not be read further — the connection is discarded");
        }
    }
}
