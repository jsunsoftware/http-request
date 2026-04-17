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

import java.net.URI;

final class BasicRetryAttempt implements RetryAttempt {
    private final Response response;
    private final HttpMethod method;
    private final URI uri;
    private final int attemptNumber;
    private final Throwable error;

    BasicRetryAttempt(Response response, HttpMethod method, URI uri, int attemptNumber, Throwable error) {
        this.response = response;
        this.method = ArgsCheck.notNull(method, "method");
        this.uri = ArgsCheck.notNull(uri, "uri");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1, got " + attemptNumber);
        }
        if ((response == null) == (error == null)) {
            throw new IllegalArgumentException("Exactly one of response or error must be non-null");
        }
        this.attemptNumber = attemptNumber;
        this.error = error;
    }

    @Override
    public Response getResponse() {
        return response;
    }

    @Override
    public HttpMethod getMethod() {
        return method;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public int getAttemptNumber() {
        return attemptNumber;
    }

    @Override
    public Throwable getError() {
        return error;
    }
}
