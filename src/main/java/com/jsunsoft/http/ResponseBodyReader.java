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

import java.io.IOException;

/**
 * Implementation of this interface must provided deserialization of response body to type {@code T}
 *
 * @param <T> Type of deserialized instance
 */
@FunctionalInterface
public interface ResponseBodyReader<T> {
    /**
     * Method receives httpEntity of the response then deserialized to type {@code T}
     *
     * @param bodyReaderContext the response context.
     * @return Deserialized content
     * @throws IOException                   If the stream could not be created or error occurs reading the input stream.
     * @throws UnsupportedOperationException If entity content cannot be represented as {@link java.io.InputStream}.
     * @throws ResponseDeserializeException  If Cannot deserialize content
     */
    T deserialize(ResponseBodyReaderContext bodyReaderContext) throws IOException, ResponseDeserializeException;

    /**
     * @param bodyReaderContext the response context.
     * @return Error text from response
     * @throws IOException if an error occurs reading the input stream
     */
    default String deserializeFailure(ResponseBodyReaderContext bodyReaderContext) throws IOException {
        return bodyReaderContext.getContentAsString();
    }
}
