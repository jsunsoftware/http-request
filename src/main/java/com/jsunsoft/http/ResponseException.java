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

import com.jsunsoft.http.annotations.Beta;

import java.net.URI;
import java.util.StringJoiner;

import static com.jsunsoft.http.BasicConnectionFailureType.UNDEFINED;

/**
 * Signals an HTTP response processing error
 */
public class ResponseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Response status code. */
    private final int statusCode;
    /** Original status code if mapped or transformed; otherwise {@code -1}. */
    private final int originalStatusCode;
    /** Request URI associated with the failure. */
    private final URI uri;
    /** Connection failure classification, if available. */
    private final ConnectionFailureType connectionFailureType;

    /**
     * Creates an exception with a status code and message.
     *
     * @param statusCode response status code
     * @param message error message
     * @param uri request URI
     */
    public ResponseException(int statusCode, String message, URI uri) {
        this(statusCode, statusCode, message, uri, null);
    }

    /**
     * Creates an exception with a status code and cause.
     *
     * @param statusCode response status code
     * @param uri request URI
     * @param cause root cause
     */
    public ResponseException(int statusCode, URI uri, Throwable cause) {
        this(statusCode, -1, null, uri, cause);
    }

    /**
     * Creates an exception with a status code, message, and cause.
     *
     * @param statusCode response status code
     * @param msg error message
     * @param uri request URI
     * @param cause root cause
     */
    public ResponseException(int statusCode, String msg, URI uri, Throwable cause) {
        this(statusCode, -1, msg, uri, cause);
    }

    /**
     * Creates an exception with status and original status codes.
     *
     * @param statusCode response status code
     * @param originalStatusCode original status code
     * @param msg error message
     * @param uri request URI
     */
    public ResponseException(int statusCode, int originalStatusCode, String msg, URI uri) {
        this(statusCode, originalStatusCode, msg, uri, null);
    }

    /**
     * Creates an exception with status codes, message, and cause.
     *
     * @param statusCode response status code
     * @param originalStatusCode original status code
     * @param msg error message
     * @param uri request URI
     * @param cause root cause
     */
    public ResponseException(int statusCode, int originalStatusCode, String msg, URI uri, Throwable cause) {
        super(msg, cause);
        this.statusCode = statusCode;
        this.originalStatusCode = originalStatusCode;
        this.uri = uri;
        connectionFailureType = UNDEFINED;
    }

    ResponseException(int statusCode, String msg, URI uri, ConnectionFailureType connectionFailureType, Throwable cause) {
        this(statusCode, -1, msg, uri, connectionFailureType, cause);
    }

    ResponseException(int statusCode, int originalStatusCode, String msg, URI uri, ConnectionFailureType connectionFailureType, Throwable cause) {
        super(msg, cause);
        this.statusCode = statusCode;
        this.originalStatusCode = originalStatusCode;
        this.uri = uri;
        this.connectionFailureType = connectionFailureType;
    }

    /**
     * @return response status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @return original status code if available
     */
    @Beta
    public int getOriginalStatusCode() {
        return originalStatusCode;
    }

    /**
     * @return request URI
     */
    public URI getURI() {
        return uri;
    }

    ConnectionFailureType getConnectionFailureType() {
        return connectionFailureType;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", super.toString() + " [", "]")
                .add("statusCode=" + statusCode)
                .add("originalStatusCode=" + originalStatusCode)
                .add("uri=" + uri)
                .add("connectionFailureType=" + connectionFailureType)
                .toString();
    }
}
