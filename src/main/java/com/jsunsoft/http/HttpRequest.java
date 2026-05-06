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
 * Entry point for building and executing HTTP requests against a configured
 * {@link org.apache.hc.client5.http.impl.classic.CloseableHttpClient}.
 * <p>
 * Obtain an instance via {@link HttpRequestBuilder#create(org.apache.hc.client5.http.impl.classic.CloseableHttpClient)
 * HttpRequestBuilder.create(client).build()}. From there, call one of the {@code target} family
 * of methods to materialise a {@link WebTarget} bound to a URI; configure it with headers,
 * parameters, and a body; then invoke a verb method ({@code get}, {@code post}, etc.) to fire
 * the request.
 *
 * <h2>Lifecycle and reuse</h2>
 *
 * {@code HttpRequest} is intended to be built once and reused indefinitely — typically a
 * singleton (or a small set of pre-configured singletons) per process. Each call to
 * {@link #target(URI)} / {@link #immutableTarget(URI)} / {@link #retryableTarget(URI, RetryContext)}
 * returns a fresh, independent {@link WebTarget}; the {@code HttpRequest} itself is not consumed
 * by these calls.
 *
 * <h2>Thread safety</h2>
 *
 * {@code HttpRequest} is <b>immutable and thread-safe</b> after the call to
 * {@link HttpRequestBuilder#build()} returns. All instance state — default headers,
 * default parameters, response body readers, request body converters, retry/scheme/host policy —
 * is captured at build time and never mutates. Concurrent {@code target(...)} calls on the same
 * {@code HttpRequest} produce independent {@link WebTarget} instances; mutations on one do not
 * leak to another.
 * <p>
 * Note that the {@link WebTarget} returned by {@link #target(URI)} is itself <em>not</em>
 * thread-safe (it is the discoverable mutable variant) — see the per-method Javadoc for the
 * recommended single-thread usage pattern. {@link #immutableTarget(URI)} returns a thread-safe
 * variant for cross-thread sharing.
 *
 * <h2>Exception model</h2>
 *
 * Methods that return {@link Response} surface a live, closable HTTP response — wrap them in
 * try-with-resources. Methods that return {@link ResponseHandler} consume the response
 * internally and surface failures via the handler's status, error text, and the
 * {@link ResponseHandler#orElseThrow()} / {@link ResponseHandler#requiredGet()} family. See
 * {@link ResponseHandler} for the accessor decision matrix.
 */
public interface HttpRequest {

    /**
     * Build a new web resource target bound to the given URI.
     * <p>
     * <b>Mutability and thread-safety.</b> The returned {@link WebTarget} is <em>mutable</em>:
     * fluent calls such as {@link WebTarget#addHeader(String, String) addHeader}, {@link
     * WebTarget#addParameter(String, String) addParameter}, {@link WebTarget#path(String) path},
     * etc. mutate <em>this</em> instance and return {@code this}. The instance is therefore
     * <em>not</em> safe to share across threads. The intended usage pattern is to build, configure,
     * and fire the request from the same thread — typically all in one fluent expression:
     * <pre>{@code
     *   httpRequest.target(uri).addHeader("X-Foo", "bar").get(User.class);
     * }</pre>
     * Storing the returned target in a field that multiple threads write to leads to interleaved
     * header / parameter mutations. If you need a target you can share across threads, use
     * {@link #immutableTarget(URI)} — it allocates per fluent call but is safe.
     *
     * @param uri web resource URI. Must not be {@code null}.
     * @return web resource target bound to the provided URI.
     * @throws NullPointerException in case the supplied argument is {@code null}.
     */
    WebTarget target(URI uri);

    /**
     * Build a new web resource target. See {@link #target(URI)} for the same mutability and
     * thread-safety contract.
     *
     * @param uri The string to be parsed into a URI
     * @return Target instance
     * @throws NullPointerException     If {@code uri} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */
    WebTarget target(String uri);

    /**
     * Build a new retryable web resource target.
     *
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
     * Build a new retryable web resource target.
     *
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
     * Build a new immutable web resource target bound to the given URI.
     * <p>
     * Each fluent call (e.g. {@link WebTarget#addHeader(String, String) addHeader},
     * {@link WebTarget#path(String) path}) returns a <em>new</em> {@link WebTarget} instance with
     * the change applied; the original is not mutated. This makes the target safe to share across
     * threads and reuse for many requests at the cost of one allocation per fluent step.
     * <p>
     * Choose this variant when you want to configure a target once (e.g. with auth headers and a
     * base path) and reuse it from multiple threads or call sites; choose {@link #target(URI)}
     * when you build, configure, and fire the request all on a single thread.
     *
     * @param uri web resource URI. Must not be {@code null}.
     * @return Immutable WebTarget instance. Safe to share between threads.
     * @throws NullPointerException in case the supplied argument is {@code null}.
     */
    WebTarget immutableTarget(URI uri);

    /**
     * Build a new immutable web resource target. See {@link #immutableTarget(URI)} for the contract.
     *
     * @param uri The string to be parsed into a URI
     * @return Immutable WebTarget instance. Safe to share between threads.
     * @throws NullPointerException     If {@code uri} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */
    WebTarget immutableTarget(String uri);
}
