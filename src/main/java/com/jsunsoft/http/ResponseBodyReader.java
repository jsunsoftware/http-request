/*
 * Copyright (c) 2021. Benik Arakelyan
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

import java.io.IOException;

/**
 * Strategy for deserializing an HTTP response body into a Java object of type {@code T}.
 * Reader instances are consulted by the library in registration order: the first reader whose
 * {@link #isReadable(ResponseBodyReadableContext)} returns {@code true} for the given context
 * (target type + content-type + status) wins, and its {@link #read(ResponseBodyReaderContext)}
 * method is invoked to produce the value.
 * <p>
 * Built-in readers cover {@code String}, {@code byte[]}, JSON ({@code application/json}), and
 * XML ({@code application/xml} / {@code text/xml}) — register a custom one via
 * {@link HttpRequestBuilder#addBodyReader(ResponseBodyReader)} when you need something else
 * (e.g. CBOR, Protobuf, MessagePack). User-supplied readers are tried <em>before</em> the
 * built-ins, so you can also override the defaults.
 *
 * <h2>Implementation contract</h2>
 *
 * <ul>
 *   <li><b>Thread safety:</b> implementations <b>must</b> be safe for concurrent use. A single
 *       reader instance is reused across requests and threads — store no per-request state in
 *       fields. Use the {@link ResponseBodyReaderContext} parameter for everything you need.</li>
 *   <li><b>Stream consumption:</b> the {@link ResponseBodyReaderContext#getContent() content
 *       stream} is one-shot. Read it once; do not stash it for later.</li>
 *   <li><b>Resource ownership:</b> do <em>not</em> close the content stream from inside
 *       {@link #read}. The library closes it when the surrounding {@link Response} is closed.</li>
 *   <li><b>Failure modes:</b> throw {@link ResponseBodyReaderException} for deserialization
 *       failures (the library will wrap it in {@link ResponseBodyProcessingException} for the
 *       lazy-API and surface it as a {@code 502 Bad Gateway} for the eager API). Throw plain
 *       {@link IOException} for I/O errors reading the stream.</li>
 * </ul>
 *
 * @param <T> the type produced by this reader
 */
public interface ResponseBodyReader<T> {

    /**
     * @return returns reader which will always read response stream to string. Reader's result will be null if no content.
     * @deprecated used by default. Will be removed in future releases.
     */
    @Deprecated
    static ResponseBodyReader<String> stringReader() {
        return ResponseBodyReaders.stringReader();
    }

    /**
     * @return returns reader which will always read response stream to string only when status code is not success. Reader's result will be null if no content.
     * @deprecated used by default. Will be removed in future releases.
     */
    @Deprecated
    static ResponseBodyReader<String> whenNonSuccessStringReader() {
        return ResponseBodyReaders.whenNonSuccessStringReader();
    }

    /**
     * @return returns reader which will read response stream to string only when status code is success. Reader's result will be null if no content.
     * @deprecated used by default. Will be removed in future releases.
     */
    @Deprecated
    static ResponseBodyReader<String> whenSuccessStringReader() {
        return ResponseBodyReaders.whenSuccessStringReader();
    }

    /**
     * Method checks if the response body is readable by this reader.
     *
     * @param bodyReadableContext the response context.
     * @return true if the response body is readable by this reader, false otherwise.
     */
    boolean isReadable(ResponseBodyReadableContext bodyReadableContext);

    /**
     * Method receives httpEntity of the response then deserialized to type {@code T}
     *
     * @param bodyReaderContext the response context.
     * @return Deserialized content
     * @throws IOException                   If the stream could not be created or error occurs reading the input stream.
     * @throws UnsupportedOperationException If entity content cannot be represented as {@link java.io.InputStream}.
     * @throws ResponseBodyReaderException   If Cannot deserialize content
     */
    T read(ResponseBodyReaderContext<T> bodyReaderContext) throws IOException, ResponseBodyReaderException;
}
