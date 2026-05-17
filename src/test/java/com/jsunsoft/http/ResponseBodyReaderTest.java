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

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.jsunsoft.http.DefaultDateDeserializeContext.DEFAULT;

class ResponseBodyReaderTest {

    @Test
    void testDeserializeResponse() throws IOException {
        String content = """
                {
                  "value": 1,
                  "message": "Test message",
                  "relations": [
                    {
                      "string": "12345",
                      "localDate": "11/05/1993"
                    },
                    {
                      "string": "54321",
                      "javaLocalDate": "08/09/2017"
                    }
                  ]
                }""";

        ResponseBodyReaderContext<Result> responseContext = resolveResponseContext(content);

        ResponseBodyReader<Result> responseBodyReader = ResponseBodyReaders.jsonReader(
                ObjectMapperInitializer.initJsonMapperIfNull(null, DEFAULT.getDateTypeToPattern())
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
    void initJsonMapperIfNullAppliesPatternsToOwnedMapper() throws IOException {
        // Contract at the internal boundary: the caller (HttpRequestBuilder.setDefaultJsonMapper)
        // hands us its mapper. Under Jackson 3 mappers are immutable, so we cannot mutate "in
        // place" — we instead produce a derivative via ObjectMapper#rebuild() with the
        // date-pattern configOverride installed. The original is by construction untouched.
        String content = """
                {
                  "value": 1,
                  "message": "Test message",
                  "relations": [
                    {
                      "string": "12345",
                      "localDate": "19930511"
                    }
                  ]
                }""";

        ObjectMapper owned = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        Map<Class<?>, String> dateTypeToPattern = new HashMap<>();
        dateTypeToPattern.put(LocalDate.class, "yyyyMMdd");

        ObjectMapper effective = ObjectMapperInitializer.initJsonMapperIfNull(owned, dateTypeToPattern);

        Assertions.assertNotSame(owned, effective, "Date overrides force a rebuild() — result must be a fresh derivative");

        Result result = ResponseBodyReaders.<Result>jsonReader(effective).read(resolveResponseContext(content));
        Assertions.assertEquals(LocalDate.of(1993, 5, 11), result.getRelations().get(0).localDate,
                "Date pattern override on the derivative must be honoured during deserialization");
    }

    @Test
    void httpRequestBuilderDoesNotMutateUserJsonMapper() throws IOException {
        // End-to-end: routing a user mapper through the public setter + a date pattern must not
        // change the user's instance in any observable way. In Jackson 3 the mapper is immutable
        // so this is structurally true, but the test guards the contract for future regressions.
        ObjectMapper userMapper = JsonMapper.builder().build();

        ResponseBodyReader<Result> referenceBefore = ResponseBodyReaders.jsonReader(userMapper);
        String unstructuredDate = """
                {"value":0,"message":"m","relations":[{"string":"x","localDate":"19930511"}]}""";
        // The user's mapper has no date pattern: parsing a custom-pattern date with it should fail.
        Assertions.assertThrows(Exception.class,
                () -> referenceBefore.read(resolveResponseContext(unstructuredDate)),
                "Sanity: user's untouched mapper cannot parse the custom yyyyMMdd date");

        try (CloseableHttpClient client = ClientBuilder.create().build()) {
            HttpRequestBuilder.create(client)
                    .setDefaultJsonMapper(userMapper)
                    .addResponseDefaultDateDeserializationPattern(LocalDate.class, "yyyyMMdd")
                    .build();
        }

        // After going through setDefaultJsonMapper + addResponseDefaultDateDeserializationPattern,
        // the *user's* mapper must still be unable to parse the custom-pattern date: it was never
        // mutated. The library's derivative is internal to the request config.
        Assertions.assertThrows(Exception.class,
                () -> ResponseBodyReaders.<Result>jsonReader(userMapper).read(resolveResponseContext(unstructuredDate)),
                "User's mapper must remain unconfigured for the custom date pattern");
    }

    @Test
    void defaultJsonMapperDropsBothNullValuesAndNullMapEntries() {
        // Pins the 2.x setDefaultPropertyInclusion(NON_NULL) semantics in the Jackson 3 builder
        // translation: NON_NULL must apply to BOTH value-inclusion (top-level property nulls
        // dropped) AND content-inclusion (null entries inside Maps/collections dropped). A
        // future cleanup that drops the .withContentInclusion(...) chain in applyLibraryDefaults
        // would silently regress Map serialization to emit null entries — this test fails fast
        // when that happens.
        ObjectMapper mapper = ObjectMapperInitializer.initJsonMapperIfNull(null, DEFAULT.getDateTypeToPattern());

        DtoWithNullables dto = new DtoWithNullables();
        dto.name = null;                                  // top-level null property
        dto.kept = "ok";
        Map<String, String> tags = new LinkedHashMap<>(); // preserve insertion order so the
        tags.put("present", "x");                         // assertion error messages are stable
        tags.put("missing", null);                        // null Map entry
        dto.tags = tags;

        String json = mapper.writeValueAsString(dto);

        Assertions.assertFalse(json.contains("\"name\""),
                "value-inclusion NON_NULL: top-level null property must be dropped. Got: " + json);
        Assertions.assertFalse(json.contains("\"missing\""),
                "content-inclusion NON_NULL: null Map entry must be dropped. Got: " + json);
        Assertions.assertTrue(json.contains("\"kept\":\"ok\""),
                "non-null top-level properties must be retained. Got: " + json);
        Assertions.assertTrue(json.contains("\"present\":\"x\""),
                "non-null Map entries must be retained. Got: " + json);
    }

    @Test
    void initXmlMapperIfNullAppliesPatternsToOwnedMapper() throws IOException {
        // Symmetric XML counterpart of initJsonMapperIfNullAppliesPatternsToOwnedMapper. Defends the
        // XmlMapper.rebuild() → withConfigOverride → build() path through applyDatePatterns; without
        // a behavioral test here, a regression that breaks the XmlMapper branch (e.g. a wildcard-
        // typing change that compiles but mis-binds the date override) would slip through silently.
        String xml = """
                <Result>
                  <message>Test message</message>
                  <value>1</value>
                  <relations>
                    <string>12345</string>
                    <localDate>19930511</localDate>
                  </relations>
                </Result>""";

        ObjectMapper owned = XmlMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        Map<Class<?>, String> dateTypeToPattern = new HashMap<>();
        dateTypeToPattern.put(LocalDate.class, "yyyyMMdd");

        ObjectMapper effective = ObjectMapperInitializer.initXmlMapperIfNull(owned, dateTypeToPattern);

        Assertions.assertNotSame(owned, effective,
                "Date overrides force an XmlMapper.rebuild() — result must be a fresh derivative");

        XmlResult result = effective.readValue(xml, XmlResult.class);
        Assertions.assertEquals(LocalDate.of(1993, 5, 11), result.getRelations().localDate,
                "Date pattern override on the XmlMapper derivative must be honoured during deserialization");
    }

    @Test
    void noPatternsReturnsUserMapperUnchanged() {
        ObjectMapper userMapper = JsonMapper.builder().build();
        ObjectMapper effective = ObjectMapperInitializer.initJsonMapperIfNull(userMapper, (Map<Class<?>, String>) null);
        Assertions.assertSame(userMapper, effective, "With no patterns, user's mapper should be returned as-is");

        Map<Class<?>, String> emptyPatterns = new HashMap<>();
        Assertions.assertSame(userMapper, ObjectMapperInitializer.initJsonMapperIfNull(userMapper, emptyPatterns),
                "With empty patterns, user's mapper should be returned as-is");
    }

    @Test
    void testDeserializeResponseWithOverriddenDateFormat() throws IOException {
        String content = """
                {
                  "value": 1,
                  "message": "Test message",
                  "relations": [
                    {
                      "string": "12345",
                      "localDate": "19930511"
                    },
                    {
                      "string": "54321",
                      "javaLocalDate": "20170908"
                    }
                  ]
                }""";

        Map<Class<?>, String> dateTypeToPattern = new HashMap<>();
        dateTypeToPattern.put(LocalDate.class, "yyyyMMdd");

        DateDeserializeContext dateDeserializeContext = new BasicDateDeserializeContext(dateTypeToPattern);

        ResponseBodyReaderContext<Result> responseContext = resolveResponseContext(content);

        ResponseBodyReader<Result> responseBodyReader = ResponseBodyReaders.jsonReader(
                ObjectMapperInitializer.initJsonMapperIfNull(null, dateDeserializeContext.getDateTypeToPattern())
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

    /**
     * Fixture for {@link #defaultJsonMapperDropsBothNullValuesAndNullMapEntries()}. Public fields
     * are sufficient for serialization under Jackson's default field-auto-detection visibility.
     */
    private static class DtoWithNullables {
        public String name;
        public String kept;
        public Map<String, String> tags;
    }

    /**
     * Fixture for {@link #initXmlMapperIfNullAppliesPatternsToOwnedMapper()}. Public no-arg
     * constructor + public mutable fields keep the Jackson wiring trivial across the JSON-vs-XML
     * comparison; the test only cares about the {@code LocalDate} round-trip.
     */
    public static class XmlResult {
        public String message;
        public long value;
        public XmlRelations relations;

        public XmlRelations getRelations() {
            return relations;
        }
    }

    public static class XmlRelations {
        public String string;
        public LocalDate localDate;
    }
}
