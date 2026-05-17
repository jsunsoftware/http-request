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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

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
                // The size cap is enforced one layer down by BoundedHttpEntity wrapping the
                // entity stream in commons-io's BoundedInputStream — that throws
                // InvalidContentLengthException as soon as the byte cap is exceeded. Passing
                // an additional `maxLen` here would only truncate the resulting String at a
                // CHARACTER boundary while letting the byte cap pass silently for the read
                // pattern EntityUtils.toString uses.
                //
                // The default charset is taken from the readable-context (UTF-8 by default,
                // configurable via HttpRequestBuilder#setDefaultResponseCharset). It is used
                // only when the response's Content-Type header carries no explicit charset
                // parameter — Apache HC5 would otherwise silently fall back to ISO-8859-1.
                result = EntityUtils.toString(bodyReaderContext.getHttpEntity(), bodyReaderContext.getDefaultResponseCharset());
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
            // See StringReader#read for why no maxLen is passed: BoundedHttpEntity already
            // wraps the stream in BoundedInputStream and throws InvalidContentLengthException
            // when the byte cap is exceeded.
            return EntityUtils.toByteArray(bodyReaderContext.getHttpEntity());
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
        T result;
        try {
            result = objectMapper.readValue(inputStreamToDeserialize, javaType);
        } catch (JacksonIOException e) {
            // Jackson 3 wraps stream-side IO failures in an unchecked JacksonIOException, including
            // our own InvalidContentLengthException (extends ResponseBodyReaderException extends
            // IOException). Recover the original IOException from anywhere in the cause chain so
            // the downstream handler in BasicWebTarget routes by runtime type — an
            // InvalidContentLengthException naturally takes the ResponseBodyReaderException branch
            // and is reported as 502 BAD_GATEWAY, while a plain transport IOException is reported
            // as 503 SERVICE_UNAVAILABLE. ExceptionUtils handles cycle detection and the (rare)
            // double-wrapped case for us.
            IOException unwrapped = ExceptionUtils.throwableOfType(e, IOException.class);
            if (unwrapped != null) {
                throw unwrapped;
            }
            throw new IOException("Deserialization stream error: " + e.getMessage(), e);
        } catch (JacksonException e) {
            throw new ResponseBodyReaderException("Deserialization failed: " + e.getMessage(), e);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Deserialization to type: [{}] completed in {}", type, HttpRequestUtils.humanTime(startTime));
        }

        return result;
    }
}
