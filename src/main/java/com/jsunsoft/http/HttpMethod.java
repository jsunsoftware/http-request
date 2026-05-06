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

package com.jsunsoft.http;

/**
 * HTTP methods supported by this client.
 */
public enum HttpMethod {
    /** HTTP GET method. */
    GET,
    /** HTTP PUT method. */
    PUT,
    /** HTTP POST method. */
    POST,
    /** HTTP HEAD method. */
    HEAD,
    /** HTTP DELETE method. */
    DELETE,
    /** HTTP OPTIONS method. */
    OPTIONS,
    /** HTTP PATCH method. */
    PATCH,
    /** HTTP TRACE method. */
    TRACE;

    /**
     * Whether this method is defined as idempotent by RFC 9110 §9.2.2.
     * <p>
     * Idempotent methods ({@link #GET}, {@link #HEAD}, {@link #OPTIONS}, {@link #PUT},
     * {@link #DELETE}, {@link #TRACE}) can be safely retried on transient failures without
     * changing observable server state beyond the first successful attempt. {@link #POST} and
     * {@link #PATCH} are not idempotent — retrying them may create duplicate resources or
     * re-apply partial changes unless the API is designed around idempotency keys.
     * <p>
     * <b>RFC idempotency vs real-world side effects.</b> RFC 9110 idempotency speaks only
     * about the request's effect on the resource the URI identifies — not about
     * <em>side effects</em> the server may apply on every call (audit-log entries, metric
     * counters, outbound notifications, billing events). A {@code PUT /resource/42} that
     * internally appends to an audit log or increments a "modified-count" counter is
     * RFC-idempotent (the resource state ends up the same) but produces duplicate side
     * effects when retried. If your endpoint has that shape, do not enable a retry policy
     * that uses {@code isIdempotent()} as the gate — write a custom {@link RetryContext}
     * that excludes the affected method, or do not enable retries for that target at all.
     *
     * @return {@code true} if the method is idempotent per RFC 9110, otherwise {@code false}.
     */
    public boolean isIdempotent() {
        switch (this) {
            case GET:
            case HEAD:
            case OPTIONS:
            case PUT:
            case DELETE:
            case TRACE:
                return true;
            case POST:
            case PATCH:
            default:
                return false;
        }
    }
}
