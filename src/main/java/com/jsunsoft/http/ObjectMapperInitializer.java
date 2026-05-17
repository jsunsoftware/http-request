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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

import java.util.Map;

class ObjectMapperInitializer {

    private ObjectMapperInitializer() {
    }

    /**
     * Returns a JSON mapper ready to use. When {@code objectMapper} is {@code null}, a fresh
     * {@link JsonMapper} configured with the library defaults is returned. Otherwise the supplied
     * mapper is taken as-is — if {@code dateTypeToPattern} is non-empty, a fresh derivative
     * is produced via {@link ObjectMapper#rebuild() rebuild()} with the date-pattern overrides
     * installed; otherwise the supplied mapper is returned unchanged.
     * <p>
     * Jackson 3 mappers are immutable, so a "snapshot" can never be observed mutating; callers
     * who pass in their own mapper retain a fully-isolated instance regardless of what we do
     * downstream.
     */
    static ObjectMapper initJsonMapperIfNull(ObjectMapper objectMapper, Map<Class<?>, String> dateTypeToPattern) {
        if (objectMapper == null) {
            return buildDefaultJsonMapper(dateTypeToPattern);
        }
        return applyDatePatterns(objectMapper, dateTypeToPattern);
    }

    /**
     * XML counterpart of {@link #initJsonMapperIfNull(ObjectMapper, Map)}.
     */
    static ObjectMapper initXmlMapperIfNull(ObjectMapper objectMapper, Map<Class<?>, String> dateTypeToPattern) {
        if (objectMapper == null) {
            return buildDefaultXmlMapper(dateTypeToPattern);
        }
        return applyDatePatterns(objectMapper, dateTypeToPattern);
    }

    private static ObjectMapper buildDefaultJsonMapper(Map<Class<?>, String> dateTypeToPattern) {
        JsonMapper.Builder builder = JsonMapper.builder();
        applyLibraryDefaults(builder);
        applyDatePatternOverrides(builder, dateTypeToPattern);
        return builder.build();
    }

    private static ObjectMapper buildDefaultXmlMapper(Map<Class<?>, String> dateTypeToPattern) {
        XmlMapper.Builder builder = XmlMapper.builder();
        applyLibraryDefaults(builder);
        applyDatePatternOverrides(builder, dateTypeToPattern);
        return builder.build();
    }

    /**
     * Applies the library's specific configuration on top of the Jackson 3 default builder.
     * <p>
     * The 2.x library disabled {@code FAIL_ON_EMPTY_BEANS}, {@code FAIL_ON_UNKNOWN_PROPERTIES}
     * and {@code WRITE_DATES_AS_TIMESTAMPS} explicitly to override the 2.x default-on; Jackson 3
     * already defaults all three to off so no explicit toggle is required here. The only
     * remaining override is the inclusion shorthand: 2.x's
     * {@code setDefaultPropertyInclusion(NON_NULL)} is internally
     * {@code JsonInclude.Value.construct(NON_NULL, NON_NULL)} and sets BOTH value-inclusion
     * (top-level property nulls dropped) AND content-inclusion (null entries inside
     * maps/collections dropped); we replicate the pairing so {@code Map} serialization output
     * stays identical to the 2.x library default. {@code NON_NULL} inclusion is not a Jackson
     * default in any version, so this line is the real configuration.
     */
    private static <M extends ObjectMapper, B extends MapperBuilder<M, B>> void applyLibraryDefaults(B builder) {
        builder.changeDefaultPropertyInclusion(v -> v
                .withValueInclusion(JsonInclude.Include.NON_NULL)
                .withContentInclusion(JsonInclude.Include.NON_NULL));
    }

    private static void applyDatePatternOverrides(MapperBuilder<?, ?> builder, Map<Class<?>, String> dateTypeToPattern) {
        if (dateTypeToPattern == null || dateTypeToPattern.isEmpty()) {
            return;
        }
        dateTypeToPattern.forEach((type, pattern) ->
                builder.withConfigOverride(type, cfg -> cfg.setFormat(JsonFormat.Value.forPattern(pattern))));
    }

    /**
     * Derives a new mapper from {@code source} with the given date-type → pattern overrides
     * installed via {@code configOverride}. Returns {@code source} unchanged when the map is
     * {@code null} or empty.
     * <p>
     * Jackson 3's built mappers are immutable, so this can never be done "in place"; we always
     * round-trip through the source's {@link ObjectMapper#rebuild() builder}.
     */
    private static ObjectMapper applyDatePatterns(ObjectMapper source, Map<Class<?>, String> dateTypeToPattern) {
        if (dateTypeToPattern == null || dateTypeToPattern.isEmpty()) {
            return source;
        }
        // rebuild() returns the concrete MapperBuilder subtype matching `source` at runtime; the
        // wildcard captures the (unknown but bounded) <M, B> type parameters.
        MapperBuilder<?, ?> builder = source.rebuild();
        applyDatePatternOverrides(builder, dateTypeToPattern);
        return builder.build();
    }
}
