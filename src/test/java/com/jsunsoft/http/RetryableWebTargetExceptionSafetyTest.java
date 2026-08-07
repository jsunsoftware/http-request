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
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RetryContext} callbacks are user code — a callback that throws must never leak the live
 * {@link Response} (and with it a pooled connection). These tests fire a retryable request whose
 * callback throws and then assert, via the pool's leased-connection count, that the runner closed
 * the response before propagating the exception.
 */
class RetryableWebTargetExceptionSafetyTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private String uri(String path) {
        return wm.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    private static void stub503(String path) {
        wm.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(503).withBody("unavailable")));
    }

    @Test
    void throwingMustBeRetried_propagates_andDoesNotLeakConnection() throws IOException {
        stub503("/boom-must");

        RetryContext throwing = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 3;
            }

            @Override
            public boolean mustBeRetried(RetryAttempt attempt) {
                throw new IllegalStateException("boom-mustBeRetried");
            }
        };

        assertNoLeak(throwing, "/boom-must", "boom-mustBeRetried");
    }

    @Test
    void throwingGetRetryDelay_propagates_andDoesNotLeakConnection() throws IOException {
        stub503("/boom-delay");

        RetryContext throwing = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 3;
            }

            @Override
            public boolean mustBeRetried(RetryAttempt attempt) {
                return true;
            }

            @Override
            public Duration getRetryDelay(RetryAttempt attempt) {
                throw new IllegalStateException("boom-getRetryDelay");
            }
        };

        assertNoLeak(throwing, "/boom-delay", "boom-getRetryDelay");
    }

    @Test
    void throwingGetRetryCount_propagates_andDoesNotLeakConnection() throws IOException {
        stub503("/boom-count");

        RetryContext throwing = new RetryContext() {
            @Override
            public int getRetryCount() {
                throw new IllegalStateException("boom-getRetryCount");
            }
        };

        assertNoLeak(throwing, "/boom-count", "boom-getRetryCount");
    }

    @Test
    void beforeRetryReturningNull_failsWithClearMessage_andDoesNotLeakConnection() throws IOException {
        stub503("/null-target");

        RetryContext nullBeforeRetry = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 1;
            }

            @Override
            public boolean mustBeRetried(RetryAttempt attempt) {
                return true;
            }

            @Override
            public Duration getRetryDelay(RetryAttempt attempt) {
                return Duration.ZERO;
            }

            @Override
            public WebTarget beforeRetry(RetryAttempt attempt, WebTarget webTarget) {
                return null;
            }
        };

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient()).build();

            NullPointerException thrown = assertThrows(NullPointerException.class, () ->
                    httpRequest.retryableTarget(uri("/null-target"), nullBeforeRetry).rawGet());
            assertTrue(thrown.getMessage().contains("beforeRetry"),
                    "a null beforeRetry result must fail with a message naming the culprit, got: " + thrown.getMessage());

            assertEquals(0, leased(resources), "no connection may stay leased after the failure");
        }
    }

    private void assertNoLeak(RetryContext retryContext, String path, String expectedMessage) throws IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient()).build();

            IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                    httpRequest.retryableTarget(uri(path), retryContext).rawGet());
            assertEquals(expectedMessage, thrown.getMessage(), "the callback's own exception must propagate unchanged");

            assertEquals(0, leased(resources),
                    "a throwing RetryContext callback must not leak the live response's connection");
        }
    }

    private static long leased(ClientBuilder.HttpClientWithResourcesWrapper resources) {
        return ((PoolingHttpClientConnectionManager) resources.getConnectionManager()).getTotalStats().getLeased();
    }
}
