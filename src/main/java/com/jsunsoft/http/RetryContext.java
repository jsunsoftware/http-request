package com.jsunsoft.http;

/*
 * Copyright 2017 Benik Arakelyan
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


import com.jsunsoft.http.annotations.Beta;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Policy for retrying requests. Decides whether an attempt should be retried, how long to wait
 * before the next attempt, and optionally reconfigures the {@link WebTarget} between attempts.
 * <p>
 * Each decision method receives a {@link RetryAttempt} describing the attempt just completed —
 * the {@linkplain RetryAttempt#getResponse() response}, the {@linkplain RetryAttempt#getMethod()
 * HTTP method}, the {@linkplain RetryAttempt#getURI() URI}, and the 1-based
 * {@linkplain RetryAttempt#getAttemptNumber() attempt number}. Use the method and attempt number
 * to gate retries by idempotency and to implement bounded/exponential backoff.
 * <p>
 * <b>Safe defaults:</b> the built-in {@link #mustBeRetried(RetryAttempt)} only retries
 * {@linkplain HttpMethod#isIdempotent() idempotent methods} on 503 responses — this prevents
 * duplicate-write bugs when a 5xx is returned by a proxy or after partial processing on the
 * server. Use {@link #onIdempotent5xx(int, Duration)} for the common safe case, or
 * {@link #onAnyMethod5xx(int, Duration)} when your API is designed around idempotency keys.
 *
 * @since 3.5.0 — the pre-3.5.0 response-only overloads
 *        ({@code mustBeRetried(Response)}, {@code getRetryDelay(Response)},
 *        {@code beforeRetry(WebTarget)}) were removed. See {@code MIGRATION.md}.
 */
@Beta
public interface RetryContext {

    /**
     * @return the maximum number of <em>retries</em> to attempt after the initial request. Zero
     *         means "no retries." The total number of requests sent is {@code 1 + getRetryCount()}.
     */
    int getRetryCount();

    /**
     * Decides whether the given attempt should be retried.
     * <p>
     * The default is conservative: retry only when the attempt produced a response (no
     * exception-based retry yet), the request used an {@linkplain HttpMethod#isIdempotent()
     * idempotent} method, and the response status is
     * {@value org.apache.hc.core5.http.HttpStatus#SC_SERVICE_UNAVAILABLE}. The idempotency gate
     * guards the most common footgun — retrying a {@code POST} on a 5xx, which risks duplicate
     * resources when the original request was processed but the response was lost, or when the
     * 5xx was returned by a proxy in front of a backend that already committed the change.
     * Override this method to change the policy (e.g. retry POST when your backend supports
     * idempotency keys).
     *
     * @param attempt describes the attempt that just completed
     * @return {@code true} to trigger a retry, {@code false} to return the current response to
     *         the caller.
     */
    default boolean mustBeRetried(RetryAttempt attempt) {
        Response response = attempt.getResponse();
        if (response == null) {
            return false;
        }
        if (!attempt.getMethod().isIdempotent()) {
            return false;
        }
        return response.getCode() == HttpStatus.SC_SERVICE_UNAVAILABLE;
    }

    /**
     * Time to wait before the next attempt. The default honors the {@code Retry-After} response
     * header when it carries an integer number of seconds, otherwise falls back to 5 seconds.
     * RFC 7231 also allows {@code Retry-After} to carry an HTTP-date — the date form is not
     * parsed by the default; override this method if you need to honor it.
     *
     * @param attempt describes the attempt that just completed
     * @return the delay before the next attempt
     */
    default Duration getRetryDelay(RetryAttempt attempt) {
        Response response = attempt.getResponse();
        if (response != null) {
            Header retryAfter = response.getFirstHeader(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null) {
                try {
                    long seconds = Long.parseLong(retryAfter.getValue().trim());
                    if (seconds >= 0) {
                        return Duration.ofSeconds(seconds);
                    }
                } catch (NumberFormatException e) {
                    LoggerFactory.getLogger(RetryContext.class)
                            .debug("Ignoring unparseable {} header value: {}", HttpHeaders.RETRY_AFTER, retryAfter.getValue());
                }
            }
        }
        return Duration.ofSeconds(5);
    }

    /**
     * Invoked before each retry. Allows the caller to reconfigure the {@link WebTarget} — for
     * example to refresh an auth token — before the next attempt is dispatched. The default
     * returns the target unchanged.
     *
     * @param attempt   describes the attempt that just completed
     * @param webTarget the current target
     * @return the target to use for the next attempt
     */
    default WebTarget beforeRetry(RetryAttempt attempt, WebTarget webTarget) {
        return webTarget;
    }

    // ------------------------------------------------------------------------------------------
    // Factory helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Retry policy that retries {@linkplain HttpMethod#isIdempotent() idempotent} methods on any
     * 5xx response, honoring the {@code Retry-After} header when present and otherwise waiting
     * for the given fixed {@code delay}. Non-idempotent methods ({@link HttpMethod#POST POST},
     * {@link HttpMethod#PATCH PATCH}) are never retried by this policy.
     * <p>
     * Safe default for most REST clients — but "safe" here is RFC 9110 idempotency.
     * {@link HttpMethod#PUT PUT} and {@link HttpMethod#DELETE DELETE} are RFC-idempotent and so
     * <em>are</em> retried by this policy; if those endpoints in your upstream produce side
     * effects on every call (audit log entries, metric counters, notifications, billing
     * events) the retry will duplicate those side effects. Write a custom
     * {@link RetryContext} that excludes the affected method when that matters — see
     * {@link HttpMethod#isIdempotent()} for the full RFC-vs-real-world discussion.
     *
     * @param maxRetries maximum number of retries after the initial request; must be {@code >= 0}
     * @param delay      wait between attempts when no {@code Retry-After} header is present; must
     *                   be non-negative
     * @return a new {@link RetryContext} instance
     */
    static RetryContext onIdempotent5xx(int maxRetries, Duration delay) {
        return BasicRetryContext.fixedDelay(maxRetries, delay, true);
    }

    /**
     * Retry policy that retries <em>any</em> HTTP method on any 5xx response, honoring
     * {@code Retry-After} and otherwise waiting for the given fixed {@code delay}.
     * <p>
     * <b>Use with care.</b> Retrying non-idempotent methods ({@link HttpMethod#POST POST},
     * {@link HttpMethod#PATCH PATCH}) can create duplicate resources when the original request
     * was processed but the response was lost, or when the 5xx was returned by a proxy in front
     * of a backend that already committed the change. Only use this policy against APIs
     * designed to dedupe — for example via an {@code Idempotency-Key} request header.
     *
     * @param maxRetries maximum number of retries after the initial request; must be {@code >= 0}
     * @param delay      wait between attempts when no {@code Retry-After} header is present; must
     *                   be non-negative
     * @return a new {@link RetryContext} instance
     */
    static RetryContext onAnyMethod5xx(int maxRetries, Duration delay) {
        return BasicRetryContext.fixedDelay(maxRetries, delay, false);
    }

    /**
     * Wraps {@code delegate} and clamps any retry delay (whether parsed from a
     * {@code Retry-After} response header or returned by the delegate's own logic) to at most
     * {@code maxRetryAfter}. Useful as a defence against a misbehaving or hostile upstream
     * returning {@code Retry-After: 99999}, which would otherwise sleep ~1.5 days per retry —
     * multiplied by {@code maxRetries} that becomes a denial-of-service against the calling
     * thread.
     *
     * <p>Only the upper bound is enforced. The delegate's
     * {@link #getRetryCount()}, {@link #mustBeRetried(RetryAttempt)}, and
     * {@link #beforeRetry(RetryAttempt, WebTarget)} are passed through verbatim.
     *
     * <p>Without this wrapper the library is RFC-compliant — it honours whatever the server
     * says. Use this wrapper when the upstream is untrusted or when you want a predictable
     * upper bound on retry latency.
     *
     * <pre>{@code
     * RetryContext safe = RetryContext.withMaxHonoredRetryAfter(
     *         RetryContext.onIdempotent5xx(3, Duration.ofSeconds(2)),
     *         Duration.ofSeconds(60));
     * }</pre>
     *
     * @param delegate      the retry context to wrap. Must not be {@code null}.
     * @param maxRetryAfter the maximum honoured retry delay. Must not be {@code null}, must be
     *                      non-negative.
     * @return a new {@link RetryContext} that delegates every decision but caps
     * {@link #getRetryDelay(RetryAttempt)} at {@code maxRetryAfter}.
     * @since 3.5.0
     */
    static RetryContext withMaxHonoredRetryAfter(RetryContext delegate, Duration maxRetryAfter) {
        ArgsCheck.notNull(delegate, "delegate");
        ArgsCheck.notNull(maxRetryAfter, "maxRetryAfter");
        if (maxRetryAfter.isNegative()) {
            throw new IllegalArgumentException("maxRetryAfter must be >= 0, got " + maxRetryAfter);
        }
        return new RetryContext() {
            @Override
            public int getRetryCount() {
                return delegate.getRetryCount();
            }

            @Override
            public boolean mustBeRetried(RetryAttempt attempt) {
                return delegate.mustBeRetried(attempt);
            }

            @Override
            public Duration getRetryDelay(RetryAttempt attempt) {
                Duration d = delegate.getRetryDelay(attempt);
                if (d == null || d.isNegative()) {
                    return d;
                }
                return d.compareTo(maxRetryAfter) > 0 ? maxRetryAfter : d;
            }

            @Override
            public WebTarget beforeRetry(RetryAttempt attempt, WebTarget webTarget) {
                return delegate.beforeRetry(attempt, webTarget);
            }
        };
    }
}
