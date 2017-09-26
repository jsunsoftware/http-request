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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_8;

final class BasicResponseContext implements ResponseContext {
    private static final Log LOGGER = LogFactory.getLog(BasicResponseContext.class);
    private final HttpResponse httpResponse;

    BasicResponseContext(HttpResponse httpResponse) {
        this.httpResponse = ArgsCheck.notNull(httpResponse, "httpResponse");
    }

    @Override
    public InputStream getContent() throws IOException {
        return httpResponse.getEntity().getContent();
    }

    @Override
    public String getContentAsString() throws IOException {
        long startTime = System.currentTimeMillis();

        String result = null;
        InputStream inputStream = getContent();

        if (inputStream != null) {
            int bufferInitialSize = resolveBufferInitialSize();
            byte[] buffer = new byte[bufferInitialSize];

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(buffer.length);

            int length;

            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }

            ContentType contentType = getContentType();
            Charset charset = contentType == null || contentType.getCharset() == null ? UTF_8 : contentType.getCharset();

            result = outputStream.toString(charset.name());
        }

        LOGGER.trace("Content type is: " + result);
        LOGGER.debug("Executed response body as string. Time: " + HttpRequestUtils.humanTime(startTime) + ", length of response body: " + (result == null ? 0 : result.length()));
        return result;
    }

    @Override
    public ContentType getContentType() {
        return ContentType.get(httpResponse.getEntity());
    }

    @Override
    public long getContentLength() {
        return httpResponse.getEntity().getContentLength();
    }

    @Override
    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    private int resolveBufferInitialSize() throws IOException {
        int result;
        long contentLength = getContentLength();
        if (contentLength > Integer.MAX_VALUE) {
            throw new InvalidContentLengthException(contentLength, "Content length is large. Content length greater than Integer.MAX_VALUE");
        }
        int integerContentLength = (int) contentLength;

        if (integerContentLength >= 0) {
            result = integerContentLength;
        } else {
            result = 1024;
        }

        return result;
    }
}
