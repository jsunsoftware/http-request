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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;

class ObjectMapperInitializer {

    private ObjectMapperInitializer() {
    }

    /**
     * Returns a JSON mapper ready to use. When {@code objectMapper} is {@code null}, a fresh
     * {@link ObjectMapper} is constructed with the library defaults. Otherwise the supplied mapper
     * is taken to be an owned instance (see {@link HttpRequestBuilder#setDefaultJsonMapper(ObjectMapper)},
     * which takes a defensive copy) and the given date patterns are installed on it in place.
     */
    static ObjectMapper initJsonMapperIfNull(ObjectMapper objectMapper, Map<Class<?>, String> dateTypeToPattern) {
        if (objectMapper == null) {
            return defaultInit(new ObjectMapper(), dateTypeToPattern);
        }
        installDatePatterns(objectMapper, dateTypeToPattern);
        return objectMapper;
    }

    static ObjectMapper initJsonMapperIfNull(ObjectMapper objectMapper, DateDeserializeContext dateDeserializeContext) {
        return initJsonMapperIfNull(objectMapper, dateDeserializeContext == null ? null : dateDeserializeContext.getDateTypeToPattern());
    }

    /**
     * XML counterpart of {@link #initJsonMapperIfNull(ObjectMapper, Map)}.
     */
    static ObjectMapper initXmlMapperIfNull(ObjectMapper objectMapper, Map<Class<?>, String> dateTypeToPattern) {
        if (objectMapper == null) {
            return defaultInit(new XmlMapper(), dateTypeToPattern);
        }
        installDatePatterns(objectMapper, dateTypeToPattern);
        return objectMapper;
    }

    static ObjectMapper initXmlMapperIfNull(ObjectMapper objectMapper, DateDeserializeContext dateDeserializeContext) {
        return initXmlMapperIfNull(objectMapper, dateDeserializeContext == null ? null : dateDeserializeContext.getDateTypeToPattern());
    }

    /**
     * Installs the given date-type → pattern map as Jackson {@code configOverride}s on {@code target}.
     * Mutates {@code target} in place; no-op when the map is {@code null} or empty.
     */
    private static void installDatePatterns(ObjectMapper target, Map<Class<?>, String> dateTypeToPattern) {
        if (dateTypeToPattern == null || dateTypeToPattern.isEmpty()) {
            return;
        }
        dateTypeToPattern.forEach((type, pattern) ->
                target.configOverride(type).setFormat(JsonFormat.Value.forPattern(pattern))
        );
    }

    /**
     * Applies the library's default configuration (date patterns, inclusion, feature toggles, and
     * standard Jackson modules) to the given mapper <em>in place</em> and returns it.
     * <p>
     * This helper mutates its argument — callers must pass a fresh/owned instance (e.g. a
     * {@code new ObjectMapper()} / {@code new XmlMapper()} constructed by the caller, or an
     * already-defensively-copied snapshot). Never pass a user-supplied mapper here directly;
     * defensive copying at public API boundaries is the responsibility of
     * {@link HttpRequestBuilder#setDefaultJsonMapper(ObjectMapper)} /
     * {@link HttpRequestBuilder#setDefaultXmlMapper(ObjectMapper)}.
     *
     * @return the same instance, now configured.
     */
    static ObjectMapper defaultInit(ObjectMapper objectMapper, Map<Class<?>, String> dateTypeToPattern) {

        DateDeserializeContext dateDeserializeContext = dateTypeToPattern == null || dateTypeToPattern.isEmpty() ?
                DefaultDateDeserializeContext.DEFAULT : new BasicDateDeserializeContext(dateTypeToPattern);

        return defaultInit(objectMapper, dateDeserializeContext);
    }

    /**
     * See {@link #defaultInit(ObjectMapper, Map)}. Mutates the argument in place.
     */
    static ObjectMapper defaultInit(ObjectMapper objectMapper, DateDeserializeContext dateDeserializeContext) {

        installDatePatterns(objectMapper, dateDeserializeContext.getDateTypeToPattern());

        objectMapper.setSerializationInclusion(NON_NULL)
                .disable(FAIL_ON_EMPTY_BEANS)
                .disable(FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .registerModules(
                        new ParameterNamesModule(JsonCreator.Mode.PROPERTIES),
                        new Jdk8Module(), new JavaTimeModule()
                );
        return objectMapper;
    }
}
