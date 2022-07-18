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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.APPLICATION_XML;

class DefaultResponseBodyReader<T> implements ResponseBodyReader<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResponseBodyReader.class);

    private final ObjectMapper jsonSerializer;
    private final ObjectMapper xmlSerializer;

    private final DateDeserializeContext dateDeserializeContext;

    DefaultResponseBodyReader(DateDeserializeContext dateDeserializeContext) {
        this.dateDeserializeContext = dateDeserializeContext;
        jsonSerializer = defaultInit(new ObjectMapper());
        xmlSerializer = defaultInit(new XmlMapper());
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
        } else {
            ContentType contentType = bodyReaderContext.getContentType();
            String mimeType = contentType == null ? null : contentType.getMimeType();

            if (APPLICATION_JSON.getMimeType().equals(mimeType)) {
                result = deserialize(bodyReaderContext, jsonSerializer);
            } else if (APPLICATION_XML.getMimeType().equals(mimeType)) {
                result = deserialize(bodyReaderContext, xmlSerializer);
            } else {
                throw new InvalidMimeTypeException(mimeType, "DefaultDeserializer doesn't supported mimeType " + mimeType + " for converting response content to: " + bodyReaderContext.getType());
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


    private ObjectMapper defaultInit(ObjectMapper objectMapper) {

        dateDeserializeContext.getDateTypeToPattern()
                .forEach((type, pattern) ->
                        objectMapper.configOverride(type)
                                .setFormat(
                                        JsonFormat.Value.forPattern(pattern)
                                )
                );

        objectMapper.setSerializationInclusion(NON_NULL)
                .disable(FAIL_ON_EMPTY_BEANS)
                .disable(FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .registerModules(new JodaModule(),
                        new ParameterNamesModule(JsonCreator.Mode.PROPERTIES),
                        new Jdk8Module(), new JavaTimeModule()
                );
        return objectMapper;
    }
}
