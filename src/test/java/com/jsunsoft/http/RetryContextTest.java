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

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetryContextTest {

    @Test
    void httpMethodIsIdempotentFollowsRfc9110() {
        // RFC 9110 §9.2.2
        assertTrue(HttpMethod.GET.isIdempotent());
        assertTrue(HttpMethod.HEAD.isIdempotent());
        assertTrue(HttpMethod.OPTIONS.isIdempotent());
        assertTrue(HttpMethod.PUT.isIdempotent());
        assertTrue(HttpMethod.DELETE.isIdempotent());
        assertTrue(HttpMethod.TRACE.isIdempotent());
        assertFalse(HttpMethod.POST.isIdempotent());
        assertFalse(HttpMethod.PATCH.isIdempotent());
    }

    @Test
    void defaultMustBeRetriedRetriesIdempotent503Only() {
        // A minimal RetryContext that relies on the interface default everywhere except retryCount.
        RetryContext plain = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 1;
            }
        };

        assertTrue(plain.mustBeRetried(attemptWithStatus(HttpMethod.GET, 503)),
                "GET + 503 must be retried by the default policy (idempotent)");
        assertTrue(plain.mustBeRetried(attemptWithStatus(HttpMethod.PUT, 503)),
                "PUT + 503 must be retried by the default policy (idempotent)");
        assertFalse(plain.mustBeRetried(attemptWithStatus(HttpMethod.POST, 503)),
                "POST + 503 must not be retried by the default policy (idempotency gate)");
        assertFalse(plain.mustBeRetried(attemptWithStatus(HttpMethod.PATCH, 503)),
                "PATCH + 503 must not be retried by the default policy (idempotency gate)");
        // Default is 503-only, not all 5xx — matches the pre-3.5 behavior for the idempotent path.
        assertFalse(plain.mustBeRetried(attemptWithStatus(HttpMethod.GET, 500)),
                "GET + 500 must not be retried by the default policy (503-only)");
        assertFalse(plain.mustBeRetried(attemptWithStatus(HttpMethod.GET, 200)),
                "GET + 200 must not be retried by the default policy");
    }

    @Test
    void defaultGetRetryDelayHonorsRetryAfter() {
        RetryContext plain = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 1;
            }
        };

        BasicClassicHttpResponse raw = new BasicClassicHttpResponse(503);
        raw.setHeader(new BasicHeader(HttpHeaders.RETRY_AFTER, "7"));
        Response response = new BasicResponse(raw, ResponseBodyReaderConfig.create().build(), URI.create("http://x/"));
        RetryAttempt attempt = new BasicRetryAttempt(response, HttpMethod.GET, URI.create("http://x/"), 1, null);

        assertEquals(Duration.ofSeconds(7), plain.getRetryDelay(attempt));
    }

    @Test
    void defaultGetRetryDelayFallsBackTo5SecondsWhenNoHeader() {
        RetryContext plain = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 1;
            }
        };

        BasicClassicHttpResponse raw = new BasicClassicHttpResponse(503);
        Response response = new BasicResponse(raw, ResponseBodyReaderConfig.create().build(), URI.create("http://x/"));
        RetryAttempt attempt = new BasicRetryAttempt(response, HttpMethod.GET, URI.create("http://x/"), 1, null);

        assertEquals(Duration.ofSeconds(5), plain.getRetryDelay(attempt));
    }

    @Test
    void onIdempotent5xxRetriesIdempotentButNotPost() {
        RetryContext ctx = RetryContext.onIdempotent5xx(3, Duration.ofMillis(50));

        assertEquals(3, ctx.getRetryCount());
        assertTrue(ctx.mustBeRetried(attemptWithStatus(HttpMethod.GET, 500)));
        assertTrue(ctx.mustBeRetried(attemptWithStatus(HttpMethod.DELETE, 503)));
        assertTrue(ctx.mustBeRetried(attemptWithStatus(HttpMethod.PUT, 504)));
        assertFalse(ctx.mustBeRetried(attemptWithStatus(HttpMethod.POST, 503)));
        assertFalse(ctx.mustBeRetried(attemptWithStatus(HttpMethod.PATCH, 503)));
        // Not 5xx:
        assertFalse(ctx.mustBeRetried(attemptWithStatus(HttpMethod.GET, 404)));
        assertFalse(ctx.mustBeRetried(attemptWithStatus(HttpMethod.GET, 200)));
    }

    @Test
    void onAnyMethod5xxRetriesNonIdempotentToo() {
        RetryContext ctx = RetryContext.onAnyMethod5xx(2, Duration.ofMillis(50));

        assertTrue(ctx.mustBeRetried(attemptWithStatus(HttpMethod.POST, 500)));
        assertTrue(ctx.mustBeRetried(attemptWithStatus(HttpMethod.PATCH, 503)));
        assertFalse(ctx.mustBeRetried(attemptWithStatus(HttpMethod.POST, 200)));
    }

    @Test
    void helperHonorsRetryAfterHeaderOverConfiguredDelay() {
        RetryContext ctx = RetryContext.onIdempotent5xx(1, Duration.ofSeconds(10));

        BasicClassicHttpResponse raw = new BasicClassicHttpResponse(503);
        raw.setHeader(new BasicHeader(HttpHeaders.RETRY_AFTER, "2"));
        Response response = new BasicResponse(raw, ResponseBodyReaderConfig.create().build(), URI.create("http://x/"));
        RetryAttempt attempt = new BasicRetryAttempt(response, HttpMethod.GET, URI.create("http://x/"), 1, null);

        assertEquals(Duration.ofSeconds(2), ctx.getRetryDelay(attempt));
    }

    @Test
    void helperFallsBackToConfiguredDelayWhenRetryAfterMissing() {
        RetryContext ctx = RetryContext.onIdempotent5xx(1, Duration.ofMillis(250));

        BasicClassicHttpResponse raw = new BasicClassicHttpResponse(503);
        Response response = new BasicResponse(raw, ResponseBodyReaderConfig.create().build(), URI.create("http://x/"));
        RetryAttempt attempt = new BasicRetryAttempt(response, HttpMethod.GET, URI.create("http://x/"), 1, null);

        assertEquals(Duration.ofMillis(250), ctx.getRetryDelay(attempt));
    }

    @Test
    void helperFallsBackWhenRetryAfterIsUnparseable() {
        RetryContext ctx = RetryContext.onIdempotent5xx(1, Duration.ofMillis(100));

        BasicClassicHttpResponse raw = new BasicClassicHttpResponse(503);
        raw.setHeader(new BasicHeader(HttpHeaders.RETRY_AFTER, "Wed, 21 Oct 2026 07:28:00 GMT")); // HTTP-date form — not parsed
        Response response = new BasicResponse(raw, ResponseBodyReaderConfig.create().build(), URI.create("http://x/"));
        RetryAttempt attempt = new BasicRetryAttempt(response, HttpMethod.GET, URI.create("http://x/"), 1, null);

        assertEquals(Duration.ofMillis(100), ctx.getRetryDelay(attempt));
    }

    @Test
    void helpersRejectBadArguments() {
        assertThrows(IllegalArgumentException.class, () -> RetryContext.onIdempotent5xx(-1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> RetryContext.onIdempotent5xx(1, Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class, () -> RetryContext.onIdempotent5xx(1, null));
        assertThrows(IllegalArgumentException.class, () -> RetryContext.onAnyMethod5xx(-1, Duration.ofSeconds(1)));
    }

    @Test
    void basicRetryAttemptRejectsInvalidState() {
        URI uri = URI.create("http://x/");
        assertThrows(IllegalArgumentException.class,
                () -> new BasicRetryAttempt(null, HttpMethod.GET, uri, 1, null),
                "both response and error null must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new BasicRetryAttempt(mockResponse(200), HttpMethod.GET, uri, 1, new RuntimeException()),
                "both response and error non-null must be rejected");
        assertThrows(IllegalArgumentException.class,
                () -> new BasicRetryAttempt(mockResponse(200), HttpMethod.GET, uri, 0, null),
                "attemptNumber < 1 must be rejected");
    }

    // ------------------------------------------------------------------------------------------

    private static RetryAttempt attemptWithStatus(HttpMethod method, int status) {
        return new BasicRetryAttempt(mockResponse(status), method, URI.create("http://x/"), 1, null);
    }

    private static Response mockResponse(int status) {
        BasicClassicHttpResponse raw = new BasicClassicHttpResponse(status);
        return new BasicResponse(raw, ResponseBodyReaderConfig.create().build(), URI.create("http://x/"));
    }
}
