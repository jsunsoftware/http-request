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

import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public interface ResponseContext {
    /**
     * @return content stream of the entity.
     * @throws IOException if the stream could not be created.
     */
    InputStream getContent() throws IOException;

    /**
     * @return content as {@link String}
     * @throws IOException                   if the stream could not be created or
     *                                       if the first byte cannot be read for any reason other than the end of the file,
     *                                       if the input stream has been closed, or if some other I/O
     * @throws InvalidContentLengthException If content length exceeds {@link java.lang.Integer#MAX_VALUE Integer.MAX_VALUE}
     * @throws UnsupportedEncodingException  If the named charset is not supported
     */
    String getContentAsString() throws IOException;

    /**
     * @return content type
     */
    ContentType getContentType();

    /**
     * Tells the length of the content, if known.
     *
     * @return the number of bytes of the content, or
     * a negative number if unknown. If the content length is known
     * but exceeds {@link java.lang.Long#MAX_VALUE Long.MAX_VALUE},
     * a negative number is returned.
     */
    long getContentLength();

    /**
     * @return the {@link HttpResponse}
     */
    HttpResponse getHttpResponse();
}
