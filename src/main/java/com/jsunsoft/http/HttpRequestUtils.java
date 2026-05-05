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

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

class HttpRequestUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestUtils.class);

    private HttpRequestUtils() {
        throw new AssertionError("No com.jsunsoft.http.HttpRequestUtils instances for you!");
    }

    static String humanTime(long startTime) {
        long difference = System.currentTimeMillis() - startTime;
        long seconds = MILLISECONDS.toSeconds(difference);
        return String.format("%d sec, %d millis", seconds, difference - SECONDS.toMillis(seconds));
    }

    static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    static boolean isNonSuccess(int statusCode) {
        return !isSuccess(statusCode);
    }

    static boolean isRedirected(int statusCode) {
        return statusCode >= 300 && statusCode < 400;
    }

    static boolean hasBody(int statusCode) {
        return statusCode >= HttpStatus.SC_OK
                && statusCode != HttpStatus.SC_NO_CONTENT
                && statusCode != HttpStatus.SC_NOT_MODIFIED
                && statusCode != HttpStatus.SC_RESET_CONTENT;
    }

    static boolean hasNotBody(int statusCode) {
        return !hasBody(statusCode);
    }

    static boolean isVoidType(Type type) {
        return type == Void.class || Void.TYPE.equals(type);
    }

    static <T> T orDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }

    static URIBuilder appendPath(URIBuilder uriBuilder, String path) {
        String newPath;

        if (uriBuilder.isPathEmpty()) {
            newPath = path;
        } else {
            String currentPath = uriBuilder.getPath();

            String slash = "/";

            boolean currentEndsWithSlash = currentPath.endsWith(slash);
            boolean pathStartsWithSlash = path.startsWith(slash);

            if (currentEndsWithSlash && pathStartsWithSlash) {
                // avoid double slash
                newPath = currentPath + path.substring(1);
            } else if (!currentEndsWithSlash && !pathStartsWithSlash) {
                // ensure single slash
                newPath = currentPath + slash + path;
            } else {
                // exactly one slash already present
                newPath = currentPath + path;
            }
        }

        return uriBuilder.setPath(newPath);
    }

    static ContentType getContentTypeFromHttpEntity(HttpEntity httpEntity) {
        if (httpEntity == null) {
            return null;
        }
        String contentType = httpEntity.getContentType();
        if (contentType == null) {
            return null;
        }
        try {
            return ContentType.parse(contentType);
        } catch (IllegalArgumentException e) {
            // Most commonly UnsupportedCharsetException from headers like
            //   Content-Type: text/plain; charset=<not-installed-in-this-JVM>
            // (UnsupportedCharsetException extends IllegalArgumentException). Treating this as
            // "no parseable content-type" lets the reader chain fall through to
            // ResponseBodyReaderNotFoundException — a well-defined library error — instead of
            // letting an unchecked exception escape from a getter that's invoked from
            // isReadable() predicates and the public Response#getContentType() API.
            LOGGER.warn("Ignoring malformed Content-Type header value '{}': {}", contentType, e.getMessage());
            return null;
        }
    }
}
