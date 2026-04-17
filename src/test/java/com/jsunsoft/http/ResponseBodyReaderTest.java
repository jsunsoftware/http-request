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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.jsunsoft.http.DefaultDateDeserializeContext.DEFAULT;

class ResponseBodyReaderTest {

    @Test
    void testDeserializeResponse() throws IOException {
        String content = "{\n" +
                "              \"value\": 1,\n" +
                "              \"message\": \"Test message\",\n" +
                "              \"relations\": [\n" +
                "                {\n" +
                "                  \"string\": \"12345\",\n" +
                "                  \"localDate\": \"11/05/1993\"\n" +
                "                },\n" +
                "                {\n" +
                "                  \"string\": \"54321\",\n" +
                "                  \"javaLocalDate\": \"08/09/2017\"\n" +
                "                }\n" +
                "              ]\n" +
                "            }";

        ResponseBodyReaderContext<Result> responseContext = resolveResponseContext(content);

        ResponseBodyReader<Result> responseBodyReader = ResponseBodyReaders.jsonReader(
                ObjectMapperInitializer.defaultInit(new ObjectMapper(), DEFAULT)
        );

        Result result = responseBodyReader.read(responseContext);
        Assertions.assertEquals(1L, result.value);
        Assertions.assertEquals("Test message", result.message);
        Assertions.assertNotNull(result.getRelations());
        Assertions.assertEquals(2, result.getRelations().size());
        Assertions.assertEquals("12345", result.getRelations().get(0).string);
        Assertions.assertEquals(LocalDate.of(1993, 5, 11), result.getRelations().get(0).localDate);
        Assertions.assertEquals("54321", result.getRelations().get(1).string);
        Assertions.assertEquals(LocalDate.of(2017, 9, 8), result.getRelations().get(1).javaLocalDate);
    }

    @Test
    void initJsonMapperIfNullAppliesPatternsInPlaceOnOwnedMapper() throws IOException {
        // Contract at the internal boundary: the caller (HttpRequestBuilder.setDefaultJsonMapper)
        // has already taken a defensive copy, so the mapper arriving here is owned. This method
        // installs date-pattern configOverrides directly on it and returns the same instance.
        String content = "{\n" +
                "              \"value\": 1,\n" +
                "              \"message\": \"Test message\",\n" +
                "              \"relations\": [\n" +
                "                {\n" +
                "                  \"string\": \"12345\",\n" +
                "                  \"localDate\": \"19930511\"\n" +
                "                }\n" +
                "              ]\n" +
                "            }";

        ObjectMapper owned = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .registerModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule(com.fasterxml.jackson.annotation.JsonCreator.Mode.PROPERTIES))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        Map<Class<?>, String> dateTypeToPattern = new HashMap<>();
        dateTypeToPattern.put(LocalDate.class, "yyyyMMdd");

        ObjectMapper effective = ObjectMapperInitializer.initJsonMapperIfNull(owned, dateTypeToPattern);

        Assertions.assertSame(owned, effective, "Owned mapper must be mutated in place, not copied again");
        Assertions.assertNotNull(owned.getDeserializationConfig().findConfigOverride(LocalDate.class), "LocalDate configOverride must be installed");

        Result result = ResponseBodyReaders.<Result>jsonReader(effective).read(resolveResponseContext(content));
        Assertions.assertEquals(LocalDate.of(1993, 5, 11), result.getRelations().get(0).localDate);
    }

    @Test
    void httpRequestBuilderTakesDefensiveSnapshotOfUserJsonMapper() throws IOException {
        // End-to-end: routing a user mapper through the public setter + a date pattern must not
        // mutate the user's instance in any observable way.
        ObjectMapper userMapper = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        boolean failOnUnknownBefore = userMapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        int modulesBefore = userMapper.getRegisteredModuleIds().size();

        try (CloseableHttpClient client = ClientBuilder.create().build()) {
            HttpRequestBuilder.create(client)
                    .setDefaultJsonMapper(userMapper)
                    .addResponseDefaultDateDeserializationPattern(LocalDate.class, "yyyy-MM-dd")
                    .build();
        }

        Assertions.assertEquals(failOnUnknownBefore, userMapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES), "setDefaultJsonMapper must not flip FAIL_ON_UNKNOWN_PROPERTIES on the caller's mapper");
        Assertions.assertEquals(modulesBefore, userMapper.getRegisteredModuleIds().size(), "setDefaultJsonMapper must not register additional modules on the caller's mapper");
        Assertions.assertNull(userMapper.getDeserializationConfig().findConfigOverride(LocalDate.class), "setDefaultJsonMapper must not install date configOverrides on the caller's mapper");
    }

    @Test
    void noPatternsReturnsUserMapperUnchanged() {
        ObjectMapper userMapper = new ObjectMapper();
        ObjectMapper effective = ObjectMapperInitializer.initJsonMapperIfNull(userMapper, (Map<Class<?>, String>) null);
        Assertions.assertSame(userMapper, effective, "With no patterns, user's mapper should be returned as-is");

        Map<Class<?>, String> emptyPatterns = new HashMap<>();
        Assertions.assertSame(userMapper, ObjectMapperInitializer.initJsonMapperIfNull(userMapper, emptyPatterns),
                "With empty patterns, user's mapper should be returned as-is");
    }

    @Test
    void testDeserializeResponseWithOverriddenDateFormat() throws IOException {
        String content = "{\n" +
                "              \"value\": 1,\n" +
                "              \"message\": \"Test message\",\n" +
                "              \"relations\": [\n" +
                "                {\n" +
                "                  \"string\": \"12345\",\n" +
                "                  \"localDate\": \"19930511\"\n" +
                "                },\n" +
                "                {\n" +
                "                  \"string\": \"54321\",\n" +
                "                  \"javaLocalDate\": \"20170908\"\n" +
                "                }\n" +
                "              ]\n" +
                "            }";

        Map<Class<?>, String> dateTypeToPattern = new HashMap<>();
        dateTypeToPattern.put(LocalDate.class, "yyyyMMdd");

        DateDeserializeContext dateDeserializeContext = new BasicDateDeserializeContext(dateTypeToPattern);

        ResponseBodyReaderContext<Result> responseContext = resolveResponseContext(content);

        ResponseBodyReader<Result> responseBodyReader = ResponseBodyReaders.jsonReader(
                ObjectMapperInitializer.defaultInit(new ObjectMapper(), dateDeserializeContext)
        );

        Result result = responseBodyReader.read(responseContext);
        Assertions.assertEquals(1L, result.value);
        Assertions.assertEquals("Test message", result.message);
        Assertions.assertNotNull(result.getRelations());
        Assertions.assertEquals(2, result.getRelations().size());
        Assertions.assertEquals("12345", result.getRelations().get(0).string);
        Assertions.assertEquals(LocalDate.of(1993, 5, 11), result.getRelations().get(0).localDate);
        Assertions.assertEquals("54321", result.getRelations().get(1).string);
        Assertions.assertEquals(LocalDate.of(2017, 9, 8), result.getRelations().get(1).javaLocalDate);
    }

    private ResponseBodyReaderContext<Result> resolveResponseContext(String content) {
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        BasicHttpEntity basicHttpEntity = new BasicHttpEntity(inputStream, content.length(), ContentType.APPLICATION_JSON);

        BasicClassicHttpResponse httpResponse = new BasicClassicHttpResponse(200);
        httpResponse.setEntity(basicHttpEntity);

        return new BasicResponseBodyReaderContext<>(httpResponse, Result.class, Result.class, URI.create(""), 0);
    }

    private static class Result {
        private final String message;
        private final long value;
        private final List<Relation> relations;

        public Result(String message, long value, List<Relation> relations) {
            this.message = message;
            this.value = value;
            this.relations = relations;
        }

        public String getMessage() {
            return message;
        }

        public long getValue() {
            return value;
        }

        public List<Relation> getRelations() {
            return relations;
        }
    }

    private static class Relation {
        private final String string;
        private final LocalDate localDate;
        private final LocalDate javaLocalDate;

        public Relation(String string, LocalDate localDate, LocalDate javaLocalDate) {
            this.string = string;
            this.localDate = localDate;
            this.javaLocalDate = javaLocalDate;
        }

        public String getString() {
            return string;
        }

        public LocalDate getLocalDate() {
            return localDate;
        }

    }
}
