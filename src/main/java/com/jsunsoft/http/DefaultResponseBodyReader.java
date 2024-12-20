/*
 * Copyright (c) 2022. Benik Arakelyan
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import static org.apache.hc.core5.http.ContentType.*;

class DefaultResponseBodyReader<T> implements ResponseBodyReader<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResponseBodyReader.class);

    private final ObjectMapper jsonSerializer;
    private final ObjectMapper xmlSerializer;

    DefaultResponseBodyReader(ObjectMapper jsonSerializer, ObjectMapper xmlSerializer) {
        this.jsonSerializer = jsonSerializer;
        this.xmlSerializer = xmlSerializer;
    }

    @Override
    public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
        return bodyReadableContext.hasEntity();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T read(ResponseBodyReaderContext<T> bodyReaderContext) throws IOException, ResponseBodyReaderException {

        T result;

        if (bodyReaderContext.getType() == String.class) {
            result = (T) ResponseBodyReader.stringReader().read((ResponseBodyReaderContext<String>) bodyReaderContext);
        } else if (byte[].class == bodyReaderContext.getType()) {
            result = (T) IOUtils.toByteArray(bodyReaderContext.getContent(), bodyReaderContext.getContentLength());
        } else {
            ContentType contentType = bodyReaderContext.getContentType();

            if (APPLICATION_JSON.isSameMimeType(contentType)) {
                result = deserialize(bodyReaderContext, jsonSerializer);
            } else if (APPLICATION_XML.isSameMimeType(contentType) || TEXT_XML.isSameMimeType(contentType)) {
                result = deserialize(bodyReaderContext, xmlSerializer);
            } else {
                String mimeType = contentType == null ? null : contentType.getMimeType();

                throw new InvalidMimeTypeException(mimeType, "Default response body reader doesn't supported mimeType " + mimeType + " for converting response content to: " + bodyReaderContext.getType());
            }
        }
        return result;

    }

    protected T deserialize(ResponseBodyReaderContext<T> responseBodyReaderContext, ObjectMapper objectMapper) throws ResponseBodyReaderException {
        try {
            return deserialize(responseBodyReaderContext.getContent(), responseBodyReaderContext.getGenericType(), objectMapper);
        } catch (IOException e) {
            throw new ResponseBodyReaderException(e);
        }
    }

    private static <T> T deserialize(InputStream inputStreamToDeserialize, Type type, ObjectMapper objectMapper) throws IOException {
        ArgsCheck.notNull(inputStreamToDeserialize, "inputStreamToDeserialize");
        ArgsCheck.notNull(type, "type");
        long startTime = System.currentTimeMillis();
        JavaType javaType = objectMapper.constructType(type);
        T result = objectMapper.readValue(inputStreamToDeserialize, javaType);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Time of deserialization inputStream to type: [{}] is {}", type, HttpRequestUtils.humanTime(startTime));
        }

        return result;
    }
}
