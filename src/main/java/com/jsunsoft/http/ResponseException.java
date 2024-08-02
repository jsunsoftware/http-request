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

import static com.jsunsoft.http.BasicConnectionFailureType.UNDEFINED;

/**
 * Signals an HTTP response processing error
 */
@SuppressWarnings("serial")
public class ResponseException extends RuntimeException {

    private final int statusCode;
    private final URI uri;
    private final ConnectionFailureType connectionFailureType;

    public ResponseException(int statusCode, String message, URI uri) {
        this(statusCode, message, uri, null);
    }

    public ResponseException(int statusCode, URI uri, Throwable cause) {
        this(statusCode, null, uri, cause);
    }

    public ResponseException(int statusCode, String msg, URI uri, Throwable cause) {
        super(msg, cause);
        this.statusCode = statusCode;
        this.uri = uri;
        connectionFailureType = UNDEFINED;
    }

    ResponseException(int statusCode, String msg, URI uri, ConnectionFailureType connectionFailureType, Throwable cause) {
        super(msg, cause);
        this.statusCode = statusCode;
        this.uri = uri;
        this.connectionFailureType = connectionFailureType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public URI getURI() {
        return uri;
    }

    ConnectionFailureType getConnectionFailureType() {
        return connectionFailureType;
    }
}
