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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.entity.ContentType;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.apache.http.entity.ContentType.APPLICATION_XML;

class DefaultResponseDeserializer<T> extends AbstractResponseDeserializer<T> {
    private static final Log LOGGER = LogFactory.getLog(DefaultResponseDeserializer.class);

    private final ObjectMapper jsonSerializer;
    private final ObjectMapper xmlSerializer;

    private final DateDeserializeContext dateDeserializeContext;

    DefaultResponseDeserializer(Type type, DateDeserializeContext dateDeserializeContext) {
        super(type);
        this.dateDeserializeContext = dateDeserializeContext;
        jsonSerializer = defaultInit(new ObjectMapper());
        xmlSerializer = defaultInit(new XmlMapper());
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(ResponseContext responseContext) throws IOException, ResponseDeserializeException {
        ContentType contentType = responseContext.getContentType();
        String mimeType = contentType == null ? null : contentType.getMimeType();
        T result;

        if (APPLICATION_JSON.getMimeType().equals(mimeType)) {
            result = deserialize(responseContext, jsonSerializer);
        } else if (APPLICATION_XML.getMimeType().equals(mimeType)) {
            result = deserialize(responseContext, xmlSerializer);
        } else if (type == String.class) {
            result = (T) responseContext.getContentAsString();
        } else {
            throw new InvalidMimeTypeException(mimeType, "Deserialization content type: " + contentType + " doesn't supported for type: " + type);
        }
        return result;

    }

    private T deserialize(ResponseContext responseContext, ObjectMapper objectMapper) throws ResponseDeserializeException {
        try {
            return deserialize(responseContext.getContent(), type, objectMapper);
        } catch (IOException e) {
            throw new ResponseDeserializeException(e);
        }
    }

    private static <T> T deserialize(InputStream inputStreamToDeserialize, Type type, ObjectMapper objectMapper) throws IOException {
        ArgsCheck.notNull(inputStreamToDeserialize, "inputStreamToDeserialize");
        ArgsCheck.notNull(type, "type");
        long startTime = System.currentTimeMillis();
        JavaType javaType = objectMapper.constructType(type);
        T result = objectMapper.readValue(inputStreamToDeserialize, javaType);
        LOGGER.debug("Time of deserialization inputStream to type: [" + type + "] is " + HttpRequestUtils.humanTime(startTime));
        return result;
    }


    private ObjectMapper defaultInit(ObjectMapper objectMapper) {

        objectMapper.configOverride(LocalDate.class).setFormat(JsonFormat.Value.forPattern(dateDeserializeContext.getDatePattern()));
        objectMapper.configOverride(LocalTime.class).setFormat(JsonFormat.Value.forPattern(dateDeserializeContext.getTimePattern()));
        objectMapper.configOverride(LocalDateTime.class).setFormat(JsonFormat.Value.forPattern(dateDeserializeContext.getDateTimePattern()));

        objectMapper.configOverride(java.time.LocalDate.class).setFormat(JsonFormat.Value.forPattern(dateDeserializeContext.getDatePattern()));
        objectMapper.configOverride(java.time.LocalTime.class).setFormat(JsonFormat.Value.forPattern(dateDeserializeContext.getTimePattern()));
        objectMapper.configOverride(java.time.LocalDateTime.class).setFormat(JsonFormat.Value.forPattern(dateDeserializeContext.getDateTimePattern()));

        objectMapper.setSerializationInclusion(NON_NULL)
                .disable(FAIL_ON_EMPTY_BEANS)
                .disable(FAIL_ON_UNKNOWN_PROPERTIES)
                .registerModules(new JodaModule(),
                        new ParameterNamesModule(JsonCreator.Mode.PROPERTIES),
                        new Jdk8Module(), new JavaTimeModule());
        return objectMapper;
    }
}
