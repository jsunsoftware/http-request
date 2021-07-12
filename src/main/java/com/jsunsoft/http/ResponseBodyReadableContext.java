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

import org.apache.http.StatusLine;
import org.apache.http.entity.ContentType;

import java.lang.reflect.Type;

public interface ResponseBodyReadableContext {

    /**
     * @return the content type of http entity
     */
    ContentType getContentType();

    /**
     * Tells the length of the content, if known.
     *
     * @return the number of bytes of the content, or
     * a negative number if unknown. If the content length is known
     * but exceeds {@link Long#MAX_VALUE Long.MAX_VALUE},
     * a negative number is returned.
     */
    long getContentLength();

    /**
     * @return the class of instance to be produced.
     *
     * <p>
     * E.g. if given TypeReference for converting is {@code new TypeReference<Map<String, String>>(){}}
     * <p>
     * then this will return
     * <p>
     * {@code interface java.util.Map}
     */
    Class<?> getType();

    /**
     * @return the type of instance to be produced.
     * E.g. if given TypeReference for converting is {@code new TypeReference<Map<String, String>>(){}}
     * <p>
     * then this will return
     * <p>
     * {@code java.util.Map<java.lang.String, java.lang.String>}
     */
    Type getGenericType();

    /**
     * @return the status line of response
     */
    StatusLine getStatusLine();

    /**
     * @return {@code true} if response has entity otherwise {@code false}
     */
    boolean hasEntity();

    /**
     * @return the status code
     */
    default int getStatusCode() {
        return getStatusLine().getStatusCode();
    }
}
