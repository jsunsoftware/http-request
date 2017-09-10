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

@SuppressWarnings("serial")
public class ResponseException extends RuntimeException {

    private final int statusCode;
    private final String uri;

    public ResponseException(int statusCode, String message, String uri) {
        this(statusCode, message, uri, null);
    }

    public ResponseException(int statusCode, String uri, Throwable cause) {
        this(statusCode, null, uri, cause);
    }

    public ResponseException(int statusCode, String msg, String uri, Throwable cause) {
        super(msg, cause);
        this.statusCode = statusCode;
        this.uri = uri;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return "ResponseException{" +
                "statusCode=" + statusCode +
                ", uri='" + uri + '\'' +
                '}';
    }
}
