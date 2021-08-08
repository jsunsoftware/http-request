/*
 * Copyright (c) 2017-2021. Benik Arakelyan
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
 * Implementation of this interface must provided deserialization of response body to type {@code T}
 *
 * @param <T> Type of deserialized instance
 */
public interface ResponseBodyReader<T> {

    boolean isReadable(ResponseBodyReadableContext bodyReadableContext);

    /**
     * Method receives httpEntity of the response then deserialized to type {@code T}
     *
     * @param bodyReaderContext the response context.
     *
     * @return Deserialized content
     *
     * @throws IOException                   If the stream could not be created or error occurs reading the input stream.
     * @throws UnsupportedOperationException If entity content cannot be represented as {@link java.io.InputStream}.
     * @throws ResponseBodyReaderException   If Cannot deserialize content
     */
    T read(ResponseBodyReaderContext<T> bodyReaderContext) throws IOException, ResponseBodyReaderException;

    /**
     * @return returns reader which will always read response stream to string. Reader's result will be null if no content.
     */
    static ResponseBodyReader<String> stringReader() {
        return ResponseBodyReaders.stringReader();
    }

    /**
     * @return returns reader which will always read response stream to string only when status code is not success. Reader's result will be null if no content.
     */
    static ResponseBodyReader<String> whenNonSuccessStringReader() {
        return ResponseBodyReaders.whenNonSuccessStringReader();
    }

    /**
     * @return returns reader which will read response stream to string only when status code is success. Reader's result will be null if no content.
     */
    static ResponseBodyReader<String> whenSuccessStringReader() {
        return ResponseBodyReaders.whenSuccessStringReader();
    }
}
