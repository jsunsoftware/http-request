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

import org.apache.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

final class BasicResponseContext implements ResponseContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicResponseContext.class);
    private final HttpEntity httpEntity;

    BasicResponseContext(HttpEntity httpEntity) {
        this.httpEntity = ArgsCheck.notNull(httpEntity, "httpEntity");
    }

    @Override
    public InputStream getContent() throws IOException {
        return httpEntity.getContent();
    }

    @Override
    public String getContentAsString() throws IOException {
        String result = null;
        InputStream inputStream = httpEntity.getContent();

        if (inputStream != null) {
            int contentLength = contentLengthToInt(inputStream.available());
            byte[] buffer = new byte[contentLength];

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(contentLength);
            int length;


            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }

            Charset charset = getContentType().getCharset();
            if (charset == null) {
                result = outputStream.toString();
            } else {
                result = outputStream.toString(getContentType().getCharset().name());
            }

        }

        LOGGER.trace("Content type is: " + result);
        return result;
    }

    @Override
    public ContentType getContentType() {
        return ContentType.create(httpEntity);
    }

    @Override
    public long getContentLength() {
        return httpEntity.getContentLength();
    }

    private int contentLengthToInt(int defaultValue) {
        long contentLength = getContentLength();
        if (contentLength > Integer.MAX_VALUE) {
            throw new IllegalStateException("Content length is large. Content length greater than Integer.MAX_VALUE. ContentLength value: " + contentLength);
        }
        int integerContentLength = (int) contentLength;
        return integerContentLength >= 0 ? integerContentLength : defaultValue;
    }
}
