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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;

public class SimpleHttpRequestToParseJsonResponseTest {
    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(8080);

    private final String response = "{\n" +
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

    private static final HttpRequest<ResponseData> HTTP_REQUEST = HttpRequestBuilder.createGet("http://localhost:8080/get", ResponseData.class).build();

    @Test
    public void test() {
        wireMockRule.stubFor(get(urlEqualTo("/get"))
                .willReturn(
                        aResponse()
                                .withBody(response)
                                .withHeader(CONTENT_TYPE, APPLICATION_JSON.getMimeType())
                                .withStatus(200)
                )
        );

        HTTP_REQUEST.execute().ifHasContent(responseData -> {
            Optional<User> foundedUser = responseData.getUsers()
                    .stream()
                    .filter(user -> "Test1".equals(user.getUserName()))
                    .findFirst();
            foundedUser.ifPresent(user -> System.out.println(user.getId()));
        });
    }

    static class ResponseData {
        private int displayLength;
        private int iTotal;
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
