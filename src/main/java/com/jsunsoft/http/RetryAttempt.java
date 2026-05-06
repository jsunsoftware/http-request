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

import com.jsunsoft.http.annotations.Beta;

import java.net.URI;

/**
 * Describes a request execution attempt passed to {@link RetryContext} to decide whether to retry,
 * how long to wait before the next attempt, and whether to reconfigure the {@link WebTarget}.
 * <p>
 * The current execution is either a {@linkplain #getResponse() completed response} (the request
 * returned a status, even a failing one) or a {@linkplain #getError() transport error} (the
 * request failed to produce a response). Exactly one of the two is non-{@code null}.
 * <p>
 * Retry policies typically combine {@link #getResponse() response status}, {@link #getMethod()
 * HTTP method} (to enforce idempotency — see {@link HttpMethod#isIdempotent()}), and
 * {@link #getAttemptNumber() attempt number} (to cap retries or implement exponential backoff).
 *
 * @since 3.5.0
 */
@Beta
public interface RetryAttempt {

    /**
     * @return the response returned by the attempt, or {@code null} if the attempt failed with
     *         an exception before producing one. Exactly one of {@code getResponse()} and
     *         {@link #getError()} is non-{@code null}.
     */
    Response getResponse();

    /**
     * @return the HTTP method of the attempted request. Never {@code null}.
     */
    HttpMethod getMethod();

    /**
     * @return the resolved target URI of the attempted request. Never {@code null}.
     */
    URI getURI();

    /**
     * @return the 1-based attempt number. {@code 1} is the original request, {@code 2} is the
     *         first retry, and so on.
     */
    int getAttemptNumber();

    /**
     * @return the exception the attempt failed with, or {@code null} if the attempt produced a
     *         response. Exactly one of {@link #getResponse()} and {@code getError()} is
     *         non-{@code null}. Reserved for future exception-based retry support; currently
     *         always {@code null} because the retry runner only acts on completed responses.
     */
    Throwable getError();
}
