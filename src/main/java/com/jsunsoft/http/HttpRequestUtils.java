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

import org.apache.http.HttpStatus;

import java.lang.reflect.Type;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

class HttpRequestUtils {

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
        return type == Void.class;
    }

    static <T> T orDefault(T value, T defaultValue) {
        return value == null ? defaultValue : value;
    }
}
