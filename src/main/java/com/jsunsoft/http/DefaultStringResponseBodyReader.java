/*
 * Copyright (c) 2021. Benik Arakelyan
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_8;

class DefaultStringResponseBodyReader implements ResponseBodyReader<String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultStringResponseBodyReader.class);

    @Override
    public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
        return bodyReadableContext.getType() == String.class && bodyReadableContext.hasEntity();
    }

    /**
     * @param bodyReaderContext the response context.
     * @return content as {@link String}
     * @throws IOException                   if the stream could not be created or
     *                                       if the first byte cannot be read for any reason other than the end of the file,
     *                                       if the input stream has been closed, or if some other I/O
     * @throws InvalidContentLengthException If content length exceeds {@link Integer#MAX_VALUE Integer.MAX_VALUE}
     * @throws UnsupportedEncodingException  If the named charset is not supported
     */
    @Override
    public String read(ResponseBodyReaderContext<String> bodyReaderContext) throws IOException {

        InputStream inputStream = bodyReaderContext.getContent();

        if (inputStream == null) {
            LOGGER.warn("Content is null");
            return null;
        }

        long startTime = System.currentTimeMillis();

        ByteArrayOutputStream outputStream = IOUtils.toByteArrayOutputStream(inputStream, bodyReaderContext.getContentLength());

        ContentType contentType = bodyReaderContext.getContentType();
        Charset charset = contentType == null || contentType.getCharset() == null ? UTF_8 : contentType.getCharset();

        LOGGER.trace("Content type is: {}", contentType);

        String result = outputStream.toString(charset.name());

        LOGGER.trace("Content is: {}", result);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Executed response body as string. Time: {}, length of response body: {}", HttpRequestUtils.humanTime(startTime), result.length());
        }

        return result;
    }
}
