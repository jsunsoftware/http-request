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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import static org.apache.hc.core5.http.ContentType.*;

class ResponseBodyReaders {
    private static final ResponseBodyReader<String> STRING_READER = new StringReader();

    private static final ResponseBodyReader<String> WHEN_NON_SUCCESS_STRING_READER = new StringReader() {
        @Override
        public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
            return super.isReadable(bodyReadableContext) && bodyReadableContext.isNonSuccess();
        }
    };

    private static final ResponseBodyReader<String> WHEN_SUCCESS_STRING_READER = new StringReader() {
        @Override
        public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
            return super.isReadable(bodyReadableContext) && bodyReadableContext.isSuccess();
        }
    };

    private static final ResponseBodyReader<byte[]> BYTE_READER = new ByteReader();

    private ResponseBodyReaders() {
    }

    static ResponseBodyReader<String> stringReader() {
        return STRING_READER;
    }

    static ResponseBodyReader<String> whenNonSuccessStringReader() {
        return WHEN_NON_SUCCESS_STRING_READER;
    }

    static ResponseBodyReader<String> whenSuccessStringReader() {
        return WHEN_SUCCESS_STRING_READER;
    }

    static ResponseBodyReader<byte[]> byteReader() {
        return BYTE_READER;
    }

    static <T> ResponseBodyReader<T> jsonReader(ObjectMapper objectMapper) {
        return new JsonReader<>(objectMapper);
    }

    static <T> ResponseBodyReader<T> xmlReader(ObjectMapper objectMapper) {
        return new XmlReader<>(objectMapper);
    }

    private static class StringReader implements ResponseBodyReader<String> {
        private static final Logger LOGGER = LoggerFactory.getLogger(StringReader.class);

        @Override
        public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
            return bodyReadableContext.getType() == String.class && bodyReadableContext.hasEntity();
        }

        @Override
        public String read(ResponseBodyReaderContext<String> bodyReaderContext) throws IOException {

            long startTime = System.currentTimeMillis();

            ContentType contentType = bodyReaderContext.getContentType();

            LOGGER.trace("Content type is: {}", contentType);

            String result;
            try {
                long maxBytes = bodyReaderContext.getMaxResponseBodySizeBytes();
                if (maxBytes > 0) {
                    int maxLen = maxBytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxBytes;
                    result = EntityUtils.toString(bodyReaderContext.getHttpEntity(), maxLen);
                } else {
                    result = EntityUtils.toString(bodyReaderContext.getHttpEntity());
                }
            } catch (ParseException e) {
                throw new ResponseBodyReaderException(e);
            }

            if (result == null || result.isEmpty()) {
                LOGGER.warn("No content to read. Content length is: {}", bodyReaderContext.getContentLength());
                return null;
            }

            LOGGER.trace("Content is: {}", result);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Executed response body as string. Time: {}, length of response body: {}", HttpRequestUtils.humanTime(startTime), result.length());
            }

            return result;
        }
    }

    private static final class ByteReader implements ResponseBodyReader<byte[]> {

        @Override
        public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
            return bodyReadableContext.getType() == byte[].class && bodyReadableContext.hasEntity();
        }

        @Override
        public byte[] read(ResponseBodyReaderContext<byte[]> bodyReaderContext) throws IOException, ResponseBodyReaderException {
            long maxBytes = bodyReaderContext.getMaxResponseBodySizeBytes();
            if (maxBytes > 0) {
                int maxLen = maxBytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxBytes;
                return EntityUtils.toByteArray(bodyReaderContext.getHttpEntity(), maxLen);
            } else {
                return EntityUtils.toByteArray(bodyReaderContext.getHttpEntity());
            }
        }
    }

    private static final class JsonReader<T> implements ResponseBodyReader<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(JsonReader.class);

        private final ObjectMapper objectMapper;

        JsonReader(ObjectMapper objectMapper) {
            this.objectMapper = ArgsCheck.notNull(objectMapper, "objectMapper");
        }

        @Override
        public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
            return bodyReadableContext.hasEntity() && APPLICATION_JSON.isSameMimeType(bodyReadableContext.getContentType());
        }

        @Override
        public T read(ResponseBodyReaderContext<T> bodyReaderContext) throws IOException {
            InputStream content = bodyReaderContext.getContent();

            if (content == null) {
                LOGGER.warn("No content to read. Content length is: {}", bodyReaderContext.getContentLength());
                return null;
            }

            return ResponseBodyReaders.deserialize(content, bodyReaderContext.getGenericType(), objectMapper, LOGGER);
        }
    }

    private static final class XmlReader<T> implements ResponseBodyReader<T> {
        private static final Logger LOGGER = LoggerFactory.getLogger(XmlReader.class);

        private final ObjectMapper objectMapper;

        XmlReader(ObjectMapper objectMapper) {
            this.objectMapper = ArgsCheck.notNull(objectMapper, "objectMapper");
        }

        @Override
        public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
            ContentType contentType = bodyReadableContext.getContentType();
            return bodyReadableContext.hasEntity() && (APPLICATION_XML.isSameMimeType(contentType) || TEXT_XML.isSameMimeType(contentType));
        }

        @Override
        public T read(ResponseBodyReaderContext<T> bodyReaderContext) throws IOException {
            InputStream content = bodyReaderContext.getContent();

            if (content == null) {
                LOGGER.warn("No content to read. Content length is: {}", bodyReaderContext.getContentLength());
                return null;
            }

            return ResponseBodyReaders.deserialize(content, bodyReaderContext.getGenericType(), objectMapper, LOGGER);
        }
    }

    private static <T> T deserialize(InputStream inputStreamToDeserialize, Type type, ObjectMapper objectMapper, Logger logger) throws IOException {
        ArgsCheck.notNull(inputStreamToDeserialize, "inputStreamToDeserialize");
        ArgsCheck.notNull(type, "type");

        logger.debug("Starting deserialization to type: [{}]", type);

        long startTime = System.currentTimeMillis();
        JavaType javaType = objectMapper.constructType(type);
        T result = objectMapper.readValue(inputStreamToDeserialize, javaType);

        if (logger.isDebugEnabled()) {
            logger.debug("Deserialization to type: [{}] completed in {}", type, HttpRequestUtils.humanTime(startTime));
        }

        return result;
    }
}
