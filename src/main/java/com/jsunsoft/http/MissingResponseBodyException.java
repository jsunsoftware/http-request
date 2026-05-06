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

import java.net.URI;

/**
 * Signals expected response body is missed.
 */
@SuppressWarnings("deprecation")
public class MissingResponseBodyException extends UnexpectedResponseException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with the given status code and message.
     *
     * @param statusCode response status code
     * @param message error detail message
     * @param uri request URI
     */
    public MissingResponseBodyException(int statusCode, String message, URI uri) {
        super(statusCode, message, uri);
    }

    /**
     * Creates an exception with status and original status codes.
     *
     * @param statusCode response status code
     * @param originalStatusCode original status code
     * @param message error detail message
     * @param uri request URI
     */
    public MissingResponseBodyException(int statusCode, int originalStatusCode, String message, URI uri) {
        super(statusCode, originalStatusCode, message, uri);
    }

    /**
     * Creates an exception with a default message.
     *
     * @param statusCode response status code
     * @param uri request URI
     */
    public MissingResponseBodyException(int statusCode, URI uri) {
        this(statusCode, "Response body is missing.", uri);
    }

    /**
     * Creates an exception with a default message and original status code.
     *
     * @param statusCode response status code
     * @param originalStatusCode original status code
     * @param uri request URI
     */
    public MissingResponseBodyException(int statusCode, int originalStatusCode, URI uri) {
        this(statusCode, originalStatusCode, "Response body is missing.", uri);
    }
}
