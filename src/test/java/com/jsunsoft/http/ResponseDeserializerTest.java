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

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.jsunsoft.http.BasicDateDeserializeContext.DEFAULT;

public class ResponseDeserializerTest {
    private ResponseBodyReaderContext responseContext;

    @Before
    public final void before() throws UnsupportedEncodingException {
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

        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8.name()));

        BasicHttpEntity basicHttpEntity = new BasicHttpEntity();
        basicHttpEntity.setContent(inputStream);
        basicHttpEntity.setContentLength(content.length());
        basicHttpEntity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        HttpResponse httpResponse = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("", 1, 1), 200, ""));
        httpResponse.setEntity(basicHttpEntity);
        responseContext = new BasicResponseBodyReaderContext(httpResponse, Result.class);
    }

    @Test
    public void testDeserializeResponse() throws IOException {
        ResponseDeserializer<Result> responseDeserializer = new DefaultResponseDeserializer<>(DEFAULT);
        Result result = responseDeserializer.deserialize(responseContext);
        Assert.assertEquals(1L, result.value);
        Assert.assertEquals("Test message", result.message);
        Assert.assertNotNull(result.getRelations());
        Assert.assertEquals(2, result.getRelations().size());
        Assert.assertEquals("12345", result.getRelations().get(0).string);
        Assert.assertEquals(new LocalDate(1993, 5, 11), result.getRelations().get(0).localDate);
        Assert.assertEquals("54321", result.getRelations().get(1).string);
        Assert.assertEquals(java.time.LocalDate.of(2017, 9, 8), result.getRelations().get(1).javaLocalDate);
    }

    private static class Result {
        private String message;
        private long value;
        private List<Relation> relations;

        public void setMessage(String message) {
            this.message = message;
        }

        public void setValue(long value) {
            this.value = value;
        }

        public void setRelations(List<Relation> relations) {
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
        private String string;
        private LocalDate localDate;
        private java.time.LocalDate javaLocalDate;

        public void setString(String string) {
            this.string = string;
        }

        public void setLocalDate(LocalDate localDate) {
            this.localDate = localDate;
        }

        public String getString() {
            return string;
        }

        public LocalDate getLocalDate() {
            return localDate;
        }

        public void setJavaLocalDate(java.time.LocalDate javaLocalDate) {
            this.javaLocalDate = javaLocalDate;
        }
    }
}
