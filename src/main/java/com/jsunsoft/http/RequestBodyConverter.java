/*
 * Copyright (c) 2025. Benik Arakelyan
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

import org.apache.hc.core5.http.HttpEntity;

/**
 * Strategy for serializing a Java object into an outgoing {@link HttpEntity}. Consulted by the
 * library when a caller passes an arbitrary {@code Object} (rather than a pre-built
 * {@code HttpEntity} or a {@code String}) to {@link WebTarget#rawRequest(HttpMethod, Object)},
 * {@link WebTarget#post(Object)}, {@link WebTarget#put(Object)}, etc. Converter instances are
 * tried in registration order; the first whose {@link #canConvert(RequestBodyConverterContext)}
 * returns {@code true} for the given context (body type + content-type header + charset) wins.
 * <p>
 * Built-in converters cover JSON ({@code application/json}) and XML ({@code application/xml})
 * via Jackson. Register a custom one via
 * {@link HttpRequestBuilder#addRequestBodyConverter(RequestBodyConverter)} for additional
 * formats (CBOR, Protobuf, form-encoded, …); user-supplied converters are tried <em>before</em>
 * the built-ins so you can also override the defaults.
 *
 * <h2>Implementation contract</h2>
 *
 * <ul>
 *   <li><b>Thread safety:</b> implementations <b>must</b> be safe for concurrent use. A single
 *       converter instance is reused across requests and threads.</li>
 *   <li><b>Selection vs. conversion:</b> {@link #canConvert} should be cheap and side-effect-free
 *       (it may be called and rejected before another converter is tried). Defer expensive
 *       work to {@link #convert}.</li>
 *   <li><b>Failure modes:</b> throw {@link RequestException} from {@code convert} when the
 *       payload can't be serialized. {@code canConvert} should never throw — return {@code false}
 *       instead.</li>
 *   <li><b>Repeatable entities preferred.</b> Return a repeatable {@link HttpEntity} (e.g.
 *       {@link org.apache.hc.core5.http.io.entity.StringEntity StringEntity},
 *       {@link org.apache.hc.core5.http.io.entity.ByteArrayEntity ByteArrayEntity}) so the
 *       request can be retried by {@link RetryableWebTarget}. Non-repeatable entities (e.g.
 *       {@link org.apache.hc.core5.http.io.entity.InputStreamEntity InputStreamEntity}) prevent
 *       retry — the library will surface an actionable error if a retry is attempted.</li>
 * </ul>
 */
public interface RequestBodyConverter {
    /**
     * @param context request body conversion context
     * @return {@code true} if this converter can convert the request body for the given context.
     */
    boolean canConvert(RequestBodyConverterContext context);

    /**
     * Converts {@link RequestBodyConverterContext#getBody()} into an {@link HttpEntity}.
     *
     * @param context request body conversion context
     * @return converted {@link HttpEntity}
     * @throws RequestException if conversion fails
     */
    HttpEntity convert(RequestBodyConverterContext context) throws RequestException;
}

