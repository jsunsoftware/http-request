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
