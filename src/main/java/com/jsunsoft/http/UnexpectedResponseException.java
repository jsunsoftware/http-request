/*
 * Copyright (c) 2017-2025. Benik Arakelyan
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
 * Signals an unexpected HTTP response
 *
 * @deprecated Use {@link UnexpectedStatusCodeException} or {@link MissingResponseBodyException} for specific cases or {@link ResponseException} in common.
 */

@Deprecated
public class UnexpectedResponseException extends ResponseException {

    /**
     * Creates an exception with a status code and message.
     *
     * @param statusCode response status code
     * @param message error message
     * @param uri request URI
     */
    public UnexpectedResponseException(int statusCode, String message, URI uri) {
        super(statusCode, message, uri);
    }

    /**
     * Creates an exception with status and original status codes.
     *
     * @param statusCode response status code
     * @param originalStatusCode original status code
     * @param message error message
     * @param uri request URI
     */
    public UnexpectedResponseException(int statusCode, int originalStatusCode, String message, URI uri) {
        super(statusCode, originalStatusCode, message, uri);
    }
}
