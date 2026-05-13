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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Bundled {@link RetryContext} implementation backing {@link RetryContext#onIdempotent5xx(int, Duration)}
 * and {@link RetryContext#onAnyMethod5xx(int, Duration)}.
 */
final class BasicRetryContext implements RetryContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicRetryContext.class);

    private final int maxRetries;
    private final Duration delay;
    private final boolean idempotentOnly;

    static RetryContext fixedDelay(int maxRetries, Duration delay, boolean idempotentOnly) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, got " + maxRetries);
        }
        ArgsCheck.notNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must be >= 0, got " + delay);
        }
        return new BasicRetryContext(maxRetries, delay, idempotentOnly);
    }

    private BasicRetryContext(int maxRetries, Duration delay, boolean idempotentOnly) {
        this.maxRetries = maxRetries;
        this.delay = delay;
        this.idempotentOnly = idempotentOnly;
    }

    @Override
    public int getRetryCount() {
        return maxRetries;
    }

    @Override
    public boolean mustBeRetried(RetryAttempt attempt) {
        Response response = attempt.getResponse();
        if (response == null) {
            return false;
        }
        if (idempotentOnly && !attempt.getMethod().isIdempotent()) {
            return false;
        }
        int code = response.getCode();
        return code >= 500 && code < 600;
    }

    @Override
    public Duration getRetryDelay(RetryAttempt attempt) {
        Response response = attempt.getResponse();
        if (response != null) {
            Duration fromHeader = parseRetryAfterSeconds(response);
            if (fromHeader != null) {
                return fromHeader;
            }
        }
        return delay;
    }

    private static Duration parseRetryAfterSeconds(Response response) {
        Header retryAfter = response.getFirstHeader(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return null;
        }

        String rawValue = retryAfter.getValue();

        if (rawValue == null) {
            return null;
        }

        try {
            long seconds = Long.parseLong(rawValue.trim());
            if (seconds < 0) {
                return null;
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            // RFC 7231 also allows an HTTP-date here; not parsed — fall back to the configured delay.
            LOGGER.debug("Ignoring unparseable {} header value: {}", HttpHeaders.RETRY_AFTER, rawValue);
            return null;
        }
    }
}
