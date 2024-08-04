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

import com.jsunsoft.http.annotations.Beta;

import java.net.URI;

/**
 * HttpRequest is the main entry point to the API used to build and execute client requests.
 * <p>
 * HttpRequest objects are immutable they can be shared.
 */
public interface HttpRequest {


    /**
     * Build a new web resource target.
     *
     * @param uri web resource URI. Must not be {@code null}.
     *
     * @return web resource target bound to the provided URI.
     *
     * @throws NullPointerException in case the supplied argument is {@code null}.
     */
    WebTarget target(URI uri);

    /**
     * Build a new web resource target.
     *
     * @param uri The string to be parsed into a URI
     *
     * @return Target instance
     *
     * @throws NullPointerException     If {@code str} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */
    WebTarget target(String uri);

    /**
     * @param uri          web resource URI. Must not be {@code null}.
     * @param retryContext retryContext. Must not be {@code null}.
     *
     * @return Retryable WebTarget instance
     *
     * @throws NullPointerException in case the supplied argument is {@code null}.
     */
    @Beta
    WebTarget retryableTarget(URI uri, RetryContext retryContext);

    /**
     * @param uri          The string to be parsed into a URI
     * @param retryContext retryContext. Must not be {@code null}.
     *
     * @return Retryable WebTarget instance
     *
     * @throws NullPointerException     If {@code str} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */
    @Beta
    WebTarget retryableTarget(String uri, RetryContext retryContext);


    /**
     * @param uri web resource URI. Must not be {@code null}.
     *
     * @return Immutable WebTarget instance. Can be shared between threads.
     *
     * @throws NullPointerException in case the supplied argument is {@code null}.
     */
    WebTarget immutableTarget(URI uri);

    /**
     * @param uri The string to be parsed into a URI
     *
     * @return Immutable WebTarget instance. Can be shared between threads.
     *
     * @throws NullPointerException     If {@code str} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */
    WebTarget immutableTarget(String uri);
}
