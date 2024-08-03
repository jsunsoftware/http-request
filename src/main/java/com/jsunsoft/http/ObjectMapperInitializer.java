/*
 * Copyright (c) 2024. Benik Arakelyan
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;

class ObjectMapperInitializer {

    private ObjectMapperInitializer() {
    }

    static ObjectMapper initJsonMapperIfNull(ObjectMapper objectMapper, Map<Class<?>, String> dateTypeToPattern) {
        return objectMapper != null ? objectMapper : defaultInit(new ObjectMapper(), dateTypeToPattern);
    }

    static ObjectMapper initJsonMapperIfNull(ObjectMapper objectMapper, DateDeserializeContext dateDeserializeContext) {
        return objectMapper != null ? objectMapper : defaultInit(new ObjectMapper(), dateDeserializeContext);
    }

    static ObjectMapper initXmlMapperIfNull(ObjectMapper objectMapper, Map<Class<?>, String> dateTypeToPattern) {
        return objectMapper != null ? objectMapper : defaultInit(new XmlMapper(), dateTypeToPattern);
    }

    static ObjectMapper initXmlMapperIfNull(ObjectMapper objectMapper, DateDeserializeContext dateDeserializeContext) {
        return objectMapper != null ? objectMapper : defaultInit(new XmlMapper(), dateDeserializeContext);
    }

    static ObjectMapper defaultInit(ObjectMapper objectMapper, Map<Class<?>, String> dateTypeToPattern) {

        DateDeserializeContext dateDeserializeContext = dateTypeToPattern == null || dateTypeToPattern.isEmpty() ?
                DefaultDateDeserializeContext.DEFAULT : new BasicDateDeserializeContext(dateTypeToPattern);

        return defaultInit(objectMapper, dateDeserializeContext);
    }

    static ObjectMapper defaultInit(ObjectMapper objectMapper, DateDeserializeContext dateDeserializeContext) {

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
