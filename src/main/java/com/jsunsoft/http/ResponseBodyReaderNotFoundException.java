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
 * Thrown when no {@link ResponseBodyReader} can handle the response body.
 */
@SuppressWarnings("serial")
public class ResponseBodyReaderNotFoundException extends ResponseBodyReaderException {
    /**
     * Creates a new exception with a message and cause.
     *
     * @param message error message
     * @param cause root cause
     */
    public ResponseBodyReaderNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception with a cause.
     *
     * @param cause root cause
     */
    public ResponseBodyReaderNotFoundException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new exception with a message.
     *
     * @param message error message
     */
    public ResponseBodyReaderNotFoundException(String message) {
        super(message);
    }
}
