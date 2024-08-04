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

import com.fasterxml.jackson.annotation.JsonRootName;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.apache.hc.core5.http.ContentType.APPLICATION_XML;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_LENGTH;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpRequestSimpleTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestSimpleTest.class);

    private static final String XML_BODY = "<xml><id>1</id><key>testValue</key></xml>";
    private static final String TEXT_BODY = "abcd";
    private static final String JSON_BODY = "{\"id\":1,\"key\":\"testValue\"}";

    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(8080);
    private final String userAgent = "JsunSoftAgent/1.0";

    private final HttpRequest httpRequestUserAgent = HttpRequestBuilder.create(new ClientBuilder().build())
            .addDefaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build();

    private final HttpRequest xmlHttpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
            .addContentType(APPLICATION_XML)
            .build();

    private final HttpRequest basicHttpRequest = HttpRequestBuilder.create((new ClientBuilder().build()))
            .build();

    @Test
    public void userAgentTest() {
        wireMockRule.stubFor(get(urlEqualTo("/userAgent"))
                .withHeader("User-Agent", equalTo(userAgent))
                .willReturn(aResponse().withStatus(200)));

        assertTrue(httpRequestUserAgent.target("http://localhost:8080/userAgent").get(Void.class).isSuccess());
    }

    @Test
    public void xmlParsingTest() {
        wireMockRule.stubFor(post(urlEqualTo("/xml"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_XML.toString()))
                .withRequestBody(equalTo(XML_BODY))
                .willReturn(
                        aResponse()
                                .withBody(XML_BODY)
                                .withHeader(CONTENT_TYPE, APPLICATION_XML.toString())
                                .withHeader(CONTENT_LENGTH, String.valueOf(XML_BODY.length()))
                                .withStatus(200)
                )
        );

        WebTarget webTarget = xmlHttpRequest.immutableTarget("http://localhost:8080/xml");

        ResponseHandler<Wrapper> responseHandler = webTarget
                .post(XML_BODY, Wrapper.class);
        responseHandler.filter(ResponseHandler::hasNotContent).ifPassed(r -> LOGGER.info(r.getErrorText()));

        assertTrue(responseHandler.isSuccess());
        assertTrue(responseHandler.hasContent());
        assertTrue(responseHandler.containsHeader(CONTENT_LENGTH));
        assertEquals(String.valueOf(XML_BODY.length()), responseHandler.getFirstHeaderValue(CONTENT_LENGTH));

        Wrapper parsedXml = responseHandler.get();

        assertEquals(1, parsedXml.id);
    }

    @Test
    public void requestXmlSerializationTest() {
        wireMockRule.stubFor(post(urlEqualTo("/xml"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_XML.toString()))
                .withRequestBody(equalTo(XML_BODY))
                .willReturn(
                        aResponse()
                                .withBody(XML_BODY)
                                .withHeader(CONTENT_TYPE, APPLICATION_XML.toString())
                                .withHeader(CONTENT_LENGTH, String.valueOf(XML_BODY.length()))
                                .withStatus(200)
                )
        );

        WebTarget webTarget = xmlHttpRequest.immutableTarget("http://localhost:8080/xml");

        Wrapper xmlWrapper = webTarget
                .post(XML_BODY, Wrapper.class)
                .requiredGet();

        ResponseHandler<Wrapper> rh = webTarget
                .post(xmlWrapper, Wrapper.class);

        assertTrue(rh.isSuccess());
        assertTrue(rh.hasContent());
        assertTrue(rh.containsHeader(CONTENT_LENGTH));
        assertEquals(String.valueOf(XML_BODY.length()), rh.getFirstHeaderValue(CONTENT_LENGTH));

        Wrapper parsedXml = rh.get();

        assertEquals(1, parsedXml.id);
    }

    @Test
    public void withoutParseTest() {
        wireMockRule.stubFor(post(urlEqualTo("/text"))
                .willReturn(
                        aResponse()
                                .withBody(TEXT_BODY)
                                .withStatus(200)
                )
        );

        ResponseHandler<String> responseHandler = basicHttpRequest.target("http://localhost:8080/text")
                .post(String.class);

        assertEquals(TEXT_BODY, responseHandler.get());
    }

    @Test
    public void withoutJsonParseTest() {

        wireMockRule.stubFor(post(urlEqualTo("/json"))
                .willReturn(
                        aResponse()
                                .withHeader(CONTENT_TYPE, APPLICATION_JSON.toString())
                                .withBody(JSON_BODY)
                                .withStatus(200)
                )
        );

        ResponseHandler<String> responseHandler = basicHttpRequest.target("http://localhost:8080/json")
                .post(String.class);

        assertEquals(JSON_BODY, responseHandler.get());
    }

    @Test
    public void requestJsonSerializationTest() {
        wireMockRule.stubFor(post(urlEqualTo("/json"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON.toString()))
                .withRequestBody(equalTo(JSON_BODY))
                .willReturn(
                        aResponse()
                                .withBody(JSON_BODY)
                                .withHeader(CONTENT_TYPE, APPLICATION_JSON.toString())
                                .withHeader(CONTENT_LENGTH, String.valueOf(JSON_BODY.length()))
                                .withStatus(200)
                )
        );

        WebTarget webTarget = basicHttpRequest.immutableTarget("http://localhost:8080/json");

        Wrapper jsonWrapper = webTarget
                .addContentType(APPLICATION_JSON)
                .post(JSON_BODY, Wrapper.class)
                .requiredGet();

        ResponseHandler<Wrapper> rh = webTarget
                .addContentType(APPLICATION_JSON)
                .post(jsonWrapper, Wrapper.class);

        assertTrue(rh.isSuccess());
        assertTrue(rh.hasContent());
        assertTrue(rh.containsHeader(CONTENT_LENGTH));
        assertEquals(String.valueOf(JSON_BODY.length()), rh.getFirstHeaderValue(CONTENT_LENGTH));

        Wrapper parsedXml = rh.get();

        assertEquals(1, parsedXml.id);
    }

    @Test
    public void addParametersAsQueryStringTest() {
        wireMockRule.stubFor(get(urlPathMatching("/get-param"))
                .withQueryParam("test", equalTo("testValue"))
                .willReturn(aResponse().withStatus(200)));

        assertTrue(
                basicHttpRequest.target("http://localhost:8080/get-param")
                        .addParameters("test=testValue&param2=param2")
                        .get(Void.class)
                        .isSuccess()
        );
    }

    @Test
    public void withoutBadRequestTest() {
        wireMockRule.stubFor(post(urlEqualTo("/text"))
                .willReturn(
                        aResponse()
                                .withBody(TEXT_BODY)
                                .withStatus(400)
                )
        );

        ResponseHandler<String> responseHandler = basicHttpRequest.target("http://localhost:8080/text")
                .post(String.class);

        assertEquals(TEXT_BODY, responseHandler.getErrorText());
    }

    @JsonRootName("xml")
    private static class Wrapper {
        private int id;
        private String key;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }


}
