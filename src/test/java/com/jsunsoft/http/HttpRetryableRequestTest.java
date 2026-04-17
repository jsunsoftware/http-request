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

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class HttpRetryableRequestTest {

    @RegisterExtension
    static WireMockExtension wireMockRule = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private final RetryContext retryContext = new RetryContext() {
        @Override
        public int getRetryCount() {
            return 2;
        }

        @Override
        public Duration getRetryDelay(RetryAttempt attempt) {
            return Duration.ZERO;
        }

        @Override
        public boolean mustBeRetried(RetryAttempt attempt) {
            return attempt.getResponse() != null && attempt.getResponse().getCode() == 401;
        }

        @Override
        public WebTarget beforeRetry(RetryAttempt attempt, WebTarget webTarget) {
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

        wireMockRule.stubFor(get(urlEqualTo("/always-401"))
                .willReturn(aResponse().withStatus(401)));
    }

    @Test
    void changeHeaderOnRetry() {
        assertEquals(
                200,
                httpRequest.retryableTarget(wireMockRule.getRuntimeInfo().getHttpBaseUrl() + "/header", retryContext)
                        .addHeader(HttpHeaders.AUTHORIZATION, "old header")
                        .rawGet()
                        .getCode()
        );
    }

    @Test
    void incorrectTestChangeHeaderOnRetry() {
        assertEquals(
                400,
                httpRequest.retryableTarget(wireMockRule.getRuntimeInfo().getHttpBaseUrl() + "/header", retryContext)
                        .addHeader(HttpHeaders.AUTHORIZATION, "not retryable")
                        .rawGet()
                        .getCode()
        );
    }

    @Test
    void mustStopAfterRetryCount_whenAlwaysRetryable() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            int code = httpRequest.retryableTarget(wireMockRule.getRuntimeInfo().getHttpBaseUrl() + "/always-401", retryContext)
                    .addHeader(HttpHeaders.AUTHORIZATION, "old header")
                    .rawGet()
                    .getCode();

            assertEquals(401, code);
        });

        // initial call + 2 retries
        wireMockRule.verify(3, getRequestedFor(urlEqualTo("/always-401")));
    }

    @Test
    void postIsNotRetriedByDefault_withOnIdempotent5xxPolicy() {
        wireMockRule.stubFor(post(urlEqualTo("/orders"))
                .willReturn(aResponse().withStatus(503)));

        int code = httpRequest.retryableTarget(
                        wireMockRule.getRuntimeInfo().getHttpBaseUrl() + "/orders",
                        RetryContext.onIdempotent5xx(3, Duration.ofMillis(1)))
                .rawRequest(HttpMethod.POST, "{}")
                .getCode();

        assertEquals(503, code);
        // Only the original POST must have been sent — retries on non-idempotent methods are
        // suppressed by the safe-default policy.
        wireMockRule.verify(1, postRequestedFor(urlEqualTo("/orders")));
    }

    @Test
    void getIsRetriedOn503_withOnIdempotent5xxPolicy() {
        wireMockRule.stubFor(get(urlEqualTo("/read"))
                .willReturn(aResponse().withStatus(503)));

        int code = httpRequest.retryableTarget(
                        wireMockRule.getRuntimeInfo().getHttpBaseUrl() + "/read",
                        RetryContext.onIdempotent5xx(2, Duration.ofMillis(1)))
                .rawGet()
                .getCode();

        assertEquals(503, code);
        // initial GET + 2 retries
        wireMockRule.verify(3, getRequestedFor(urlEqualTo("/read")));
    }

    @Test
    void postWithRepeatableBodyIsRetried_whenOnAnyMethod5xxIsConfigured() {
        // StringEntity (used by rawRequest(POST, "{}")) is repeatable, so the request copy path
        // can safely replay it on each retry attempt.
        wireMockRule.stubFor(post(urlEqualTo("/idempotent-post"))
                .willReturn(aResponse().withStatus(503)));

        int code = httpRequest.retryableTarget(
                        wireMockRule.getRuntimeInfo().getHttpBaseUrl() + "/idempotent-post",
                        RetryContext.onAnyMethod5xx(2, Duration.ofMillis(1)))
                .rawRequest(HttpMethod.POST, "{}")
                .getCode();

        assertEquals(503, code);
        // initial POST + 2 retries — caller explicitly opted into non-idempotent retries, and the
        // body is repeatable so the transport path can replay it.
        wireMockRule.verify(3, postRequestedFor(urlEqualTo("/idempotent-post")));
    }

    @Test
    void nonRepeatableBodyFailsWithActionableErrorOnRetry() {
        // InputStreamEntity is not repeatable — the request cannot be replayed. We expect an
        // IllegalStateException that names the cause (non-repeatable entity) and points users
        // at the fix (use a repeatable entity type).
        wireMockRule.stubFor(post(urlEqualTo("/stream-post"))
                .willReturn(aResponse().withStatus(503)));

        org.apache.hc.core5.http.HttpEntity streaming = new org.apache.hc.core5.http.io.entity.InputStreamEntity(
                new java.io.ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                org.apache.hc.core5.http.ContentType.APPLICATION_JSON);

        IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> httpRequest.retryableTarget(
                                wireMockRule.getRuntimeInfo().getHttpBaseUrl() + "/stream-post",
                                RetryContext.onAnyMethod5xx(2, Duration.ofMillis(1)))
                        .rawRequest(HttpMethod.POST, streaming));

        org.junit.jupiter.api.Assertions.assertTrue(
                thrown.getMessage().contains("non-repeatable"),
                "Error message should name the cause: " + thrown.getMessage());
    }

    @Test
    void defaultRetryContextDoesNotRetryPostOn503() {
        // The default mustBeRetried(RetryAttempt) is idempotency-gated — a caller who supplies a
        // RetryContext with only getRetryCount() overridden must not see POST retried on a 503.
        wireMockRule.stubFor(post(urlEqualTo("/default-post"))
                .willReturn(aResponse().withStatus(503)));

        RetryContext minimal = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 2;
            }

            @Override
            public Duration getRetryDelay(RetryAttempt attempt) {
                return Duration.ZERO;
            }
        };

        int code = httpRequest.retryableTarget(
                        wireMockRule.getRuntimeInfo().getHttpBaseUrl() + "/default-post", minimal)
                .rawRequest(HttpMethod.POST, "{}")
                .getCode();

        assertEquals(503, code);
        wireMockRule.verify(1, postRequestedFor(urlEqualTo("/default-post")));
    }
}
