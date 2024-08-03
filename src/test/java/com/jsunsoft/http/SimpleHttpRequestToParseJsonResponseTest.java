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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;


public class SimpleHttpRequestToParseJsonResponseTest {
    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(8080);

    private static final String RESPONSE_DATA_STRING = "{\n" +
            "  \"displayLength\": \"4\",\n" +
            "  \"iTotal\": \"20\",\n" +
            "  \"users\": [\n" +
            "    {\n" +
            "      \"id\": \"2\",\n" +
            "      \"userName\": \"Test1\",\n" +
            "      \"Group\": {   \"id\":1,\n" +
            "        \"name\":\"Test-Admin\"\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"17\",\n" +
            "      \"userName\": \"Test2\",\n" +
            "      \"Group\": {   \"id\":1,\n" +
            "        \"name\":\"Test-Admin\"\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"32\",\n" +
            "      \"userName\": \"Test3\",\n" +
            "      \"Group\": {   \"id\":1,\n" +
            "        \"name\":\"Test-Admin\"\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"35\",\n" +
            "      \"userName\": \"Test4\",\n" +
            "      \"Group\": {   \"id\":1,\n" +
            "        \"name\":\"Test-Admin\"\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "  ]\n" +
            "}";

    private static final String RESPONSE_DATA_STRING_OVERRIDDEN_DATE_PATTEN = "{\n" +
            "  \"displayLength\": \"4\",\n" +
            "  \"iTotal\": \"20\",\n" +
            "  \"javaLocalDateTime\": \"11/05/1993 05:00:00\",\n" +
            "  \"jodaLocalDate\": \"20170925\",\n" +
            "  \"users\": [\n" +
            "    {\n" +
            "      \"id\": \"2\",\n" +
            "      \"userName\": \"Test1\",\n" +
            "      \"Group\": {   \"id\":1,\n" +
            "        \"name\":\"Test-Admin\"\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"17\",\n" +
            "      \"userName\": \"Test2\",\n" +
            "      \"Group\": {   \"id\":1,\n" +
            "        \"name\":\"Test-Admin\"\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"32\",\n" +
            "      \"userName\": \"Test3\",\n" +
            "      \"Group\": {   \"id\":1,\n" +
            "        \"name\":\"Test-Admin\"\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"id\": \"35\",\n" +
            "      \"userName\": \"Test4\",\n" +
            "      \"Group\": {   \"id\":1,\n" +
            "        \"name\":\"Test-Admin\"\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "  ]\n" +
            "}";

    private final String responseMapString = "{\n" +
            "  \"testKey\" : \"testValue\"\n" +
            "}";

    private static final CloseableHttpClient closeableHttpClient = ClientBuilder.create().build();

    private static final HttpRequest HTTP_REQUEST = HttpRequestBuilder.create(closeableHttpClient)
            .build();

    private static final HttpRequest HTTP_REQUEST_DATE_PATTER_OVERRIDDEN = HttpRequestBuilder.create(closeableHttpClient)
            .addDefaultDateDeserializationPattern(LocalDateTime.class, "dd/MM/yyyy HH:mm:ss")
            .addDefaultDateDeserializationPattern(LocalDate.class, "yyyyMMdd")
            .build();

    private static final HttpRequest HTTP_REQUEST_WITH_BODY_READER = HttpRequestBuilder.create(closeableHttpClient)
            .addBodyReader(new ResponseBodyReader<Map<String, String>>() {
                @Override
                public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
                    return bodyReadableContext.getType() == Map.class;
                }

                @Override
                public Map<String, String> read(ResponseBodyReaderContext<Map<String, String>> bodyReaderContext) throws IOException {
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.readValue(bodyReaderContext.getContent(), mapper.constructType(bodyReaderContext.getGenericType()));
                }
            })
            .addBodyReader(new ResponseBodyReader<ResponseData>() {
                @Override
                public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
                    return bodyReadableContext.getType() == ResponseData.class;
                }

                @Override
                public ResponseData read(ResponseBodyReaderContext<ResponseData> bodyReaderContext) throws IOException {
                    return new ObjectMapper()
                            .disable(FAIL_ON_UNKNOWN_PROPERTIES)
                            .readValue(bodyReaderContext.getContent(), bodyReaderContext.getType());
                }
            })
            .build();

    @Before
    public void before() {

        wireMockRule.stubFor(get(urlEqualTo("/get"))
                .willReturn(
                        aResponse()
                                .withBody(RESPONSE_DATA_STRING)
                                .withHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType())
                                .withStatus(200)
                )
        );

        wireMockRule.stubFor(get(urlEqualTo("/getWithOverriddenDates"))
                .willReturn(
                        aResponse()
                                .withBody(RESPONSE_DATA_STRING_OVERRIDDEN_DATE_PATTEN)
                                .withHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType())
                                .withStatus(200)
                )
        );

        wireMockRule.stubFor(get(urlEqualTo("/get/map"))
                .willReturn(
                        aResponse()
                                .withBody(responseMapString)
                                .withHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType())
                                .withStatus(200)
                )
        );
    }

    @Test
    public void testParsingResponseDataWithDefaultBodyReader() {

        HTTP_REQUEST.target("http://localhost:8080/get").get(ResponseData.class)
                .ifHasContent(responseData -> {
                    Optional<User> foundedUser = responseData.getUsers()
                            .stream()
                            .filter(user -> "Test1".equals(user.getUserName()))
                            .findFirst();
                    foundedUser.ifPresent(user -> Assert.assertEquals(2, user.getId()));
                });

        ResponseData responseData = HTTP_REQUEST.target("http://localhost:8080/get").get()
                .readEntity(ResponseData.class);

        Optional<User> foundedUser = responseData.getUsers()
                .stream()
                .filter(user -> "Test1".equals(user.getUserName()))
                .findFirst();
        foundedUser.ifPresent(user -> Assert.assertEquals(2, user.getId()));
    }

    @Test
    public void testParsingResponseDataWithDefaultBodyReaderDatePatternOverridden() {

        HTTP_REQUEST_DATE_PATTER_OVERRIDDEN.target("http://localhost:8080/getWithOverriddenDates").get(ResponseData.class)
                .ifHasContent(responseData -> {
                    Optional<User> foundedUser = responseData.getUsers()
                            .stream()
                            .filter(user -> "Test1".equals(user.getUserName()))
                            .findFirst();
                    foundedUser.ifPresent(user -> Assert.assertEquals(2, user.getId()));
                });

        ResponseData responseData = HTTP_REQUEST_DATE_PATTER_OVERRIDDEN.target("http://localhost:8080/getWithOverriddenDates").get()
                .readEntity(ResponseData.class);

        Assert.assertEquals(LocalDateTime.of(1993, Month.MAY, 11, 5, 0, 0), responseData.getJavaLocalDateTime());
        Assert.assertEquals(new LocalDate(2017, 9, 25), responseData.getJodaLocalDate());

        Optional<User> foundedUser = responseData.getUsers()
                .stream()
                .filter(user -> "Test1".equals(user.getUserName()))
                .findFirst();
        foundedUser.ifPresent(user -> Assert.assertEquals(2, user.getId()));
    }

    @Test
    public void testParsingResponseDataWithCustomBodyReader() {

        HTTP_REQUEST_WITH_BODY_READER.target("http://localhost:8080/get").get(ResponseData.class)
                .ifHasContent(rd -> {
                    Optional<User> foundedUser = rd.getUsers()
                            .stream()
                            .filter(user -> "Test1".equals(user.getUserName()))
                            .findFirst();
                    foundedUser.ifPresent(user -> Assert.assertEquals(2, user.getId()));
                });

        ResponseData responseData = HTTP_REQUEST_WITH_BODY_READER.target("http://localhost:8080/get").get()
                .readEntity(ResponseData.class);

        Optional<User> foundedUser = responseData.getUsers()
                .stream()
                .filter(user -> "Test1".equals(user.getUserName()))
                .findFirst();
        foundedUser.ifPresent(user -> Assert.assertEquals(2, user.getId()));
    }

    @Test
    public void testParsingMapWithCustomBodyReader() {

        HTTP_REQUEST_WITH_BODY_READER.target("http://localhost:8080/get/map").get(new TypeReference<Map<String, String>>() {})
                .ifHasContent(r -> Assert.assertEquals("testValue", r.get("testKey")));

        Map<String, String> r = HTTP_REQUEST_WITH_BODY_READER.target("http://localhost:8080/get/map").get()
                .readEntity(new TypeReference<Map<String, String>>() {});

        Assert.assertEquals("testValue", r.get("testKey"));
    }

    static class ResponseData {
        private int displayLength;
        private int iTotal;


        private LocalDateTime javaLocalDateTime;

        private LocalDate jodaLocalDate;
        private List<User> users;

        public int getDisplayLength() {
            return displayLength;
        }

        public void setDisplayLength(int displayLength) {
            this.displayLength = displayLength;
        }

        public int getiTotal() {
            return iTotal;
        }

        public void setiTotal(int iTotal) {
            this.iTotal = iTotal;
        }

        public LocalDateTime getJavaLocalDateTime() {
            return javaLocalDateTime;
        }

        public void setJavaLocalDateTime(LocalDateTime javaLocalDateTime) {
            this.javaLocalDateTime = javaLocalDateTime;
        }

        public LocalDate getJodaLocalDate() {
            return jodaLocalDate;
        }

        public void setJodaLocalDate(LocalDate jodaLocalDate) {
            this.jodaLocalDate = jodaLocalDate;
        }

        public List<User> getUsers() {
            return users;
        }

        public void setUsers(List<User> users) {
            this.users = users;
        }
    }

    static class User {
        private int id;
        private String userName;
        private Group Group;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public Group getGroup() {
            return Group;
        }

        public void setGroup(Group group) {
            Group = group;
        }
    }

    static class Group {
        private int id;
        private String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
