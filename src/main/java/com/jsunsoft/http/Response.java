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
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.net.URI;

/**
 * Live, closable HTTP response handle returned by the lazy {@code WebTarget.request(...)} family
 * (those that return {@code Response} rather than {@code ResponseHandler}).
 * <p>
 * A {@code Response} owns the underlying {@link ClassicHttpResponse} and the streaming body. The
 * caller is responsible for closing it — always wrap in try-with-resources:
 * <pre>{@code
 *     try (Response response = httpRequest.target(uri).get()) {
 *         int code = response.getCode();
 *         User user = response.readEntity(User.class);
 *     } // close() drains and releases the connection
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #readEntity(Class)} / {@link #readEntity(TypeReference)} — one-shot deserialization;
 *       the underlying body stream is non-repeatable, so a second call sees an exhausted stream
 *       and surfaces a {@link ResponseBodyProcessingException}. Read into a buffer first if you
 *       need both typed and raw views.</li>
 *   <li>{@link #close()} — drains any unread body content (bounded by
 *       {@code setMaxResponseBodySizeBytes} when configured) so the connection is eligible for
 *       pool reuse, then closes the underlying response. Always called via try-with-resources.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 *
 * {@code Response} is <b>not</b> thread-safe. The body stream is shared with the underlying
 * Apache HC5 connection and concurrent reads will interleave bytes. Read it on one thread, close
 * it on the same (or after that thread is done).
 *
 * <h2>Heads-up: extends {@link ClassicHttpResponse}</h2>
 *
 * For backward compatibility this interface inherits from Apache HC5's {@code ClassicHttpResponse},
 * which exposes ~30 methods (header iteration, version setters, locale, status mutators, etc.).
 * In day-to-day usage you'll only need {@link #getCode()}, {@link #getHeaders()},
 * {@link #readEntity(Class) readEntity}, {@link #close() close}, and {@link #getURI()}; the
 * inherited methods are listed for compatibility but not part of the library's stable API.
 */
public interface Response extends ClassicHttpResponse {

    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * <b>One-shot semantic.</b> The underlying response body is a streaming, non-repeatable
     * {@code InputStream}. Calling {@code readEntity} more than once on the same {@code Response}
     * is unsupported — the second call sees an exhausted stream and will typically surface a
     * {@link ResponseBodyProcessingException} (or, for a few reader / type combinations, return
     * {@code null} or an empty value). If you need both a typed and a string view of the body,
     * read the body once into a buffer (e.g. {@code String}) and re-parse from there.
     * <p>
     * Note: this method will surface any unchecked exception thrown by the resolved
     * {@link ResponseBodyReader}.
     *
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     * @return Response entity
     * @throws ResponseBodyProcessingException when body processing failed.
     */
    <T> T readEntity(Class<T> responseType);

    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * See {@link #readEntity(Class)} for the one-shot semantic — the body stream is
     * non-repeatable and may only be consumed once per {@code Response} instance.
     *
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     * @return Response entity
     * @throws ResponseBodyProcessingException when body processing failed.
     */
    <T> T readEntity(TypeReference<T> responseType);

    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * Note: method will throw any unchecked exception which will occurred in specified {@link ResponseBodyReader}.
     *
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     * @return Response entity
     * @throws IOException                 If the stream could not be created or error occurs reading the input stream.
     * @throws ResponseBodyReaderException If Cannot deserialize content
     */
    @Beta
    <T> T readEntityChecked(Class<T> responseType) throws IOException;

    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * Note: method will throw any unchecked exception which will occurred in specified {@link ResponseBodyReader}.
     * </p>
     *
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     * @return Response entity
     * @throws IOException                 If the stream could not be created or error occurs reading the input stream.
     * @throws ResponseBodyReaderException If Cannot deserialize content
     */
    @Beta
    <T> T readEntityChecked(TypeReference<T> responseType) throws IOException;

    /**
     * @return the request URI
     */
    URI getURI();

    /**
     * @return {@code true} if the response has a non-null entity
     */
    default boolean hasEntity() {
        return getEntity() != null;
    }

    /**
     * @return Content type of response
     */
    default ContentType getContentType() {

        return HttpRequestUtils.getContentTypeFromHttpEntity(getEntity());
    }

    /**
     * @return the status code.
     * @see #getCode()
     * @deprecated use getCode instead
     */
    @Deprecated
    default int getStatusCode() {
        return getCode();
    }

    /**
     * @return Returns <b>true</b> if status code contains [200, 300) else <b>false</b>
     */
    default boolean isSuccess() {
        return HttpRequestUtils.isSuccess(getCode());
    }

    /**
     * @return Returns <b>true</b> if status code isn't contains [200, 300) else <b>false</b>
     */
    default boolean isNonSuccess() {
        return !isSuccess();
    }

}
