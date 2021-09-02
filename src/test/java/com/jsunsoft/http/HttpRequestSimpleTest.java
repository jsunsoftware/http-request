/*
 * Copyright (c) 2017-2021. Benik Arakelyan
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
import org.apache.http.HttpHeaders;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.http.HttpHeaders.CONTENT_LENGTH;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.entity.ContentType.APPLICATION_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpRequestSimpleTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestSimpleTest.class);

    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(8080);
    private final String userAgent = "JsunSoftAgent/1.0";

    private final HttpRequest httpRequestUserAgent = HttpRequestBuilder.create(new ClientBuilder().build())
            .addDefaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build();

    private final HttpRequest xmlHttpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
            .addContentType(APPLICATION_XML)
            .build();

    private final HttpRequest httpRequestWithoutParse = HttpRequestBuilder.create((new ClientBuilder().build()))
            .addBodyReader(ResponseBodyReader.stringReader())
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
        String xmlBody = "<xml><id>1</id></xml>";
        wireMockRule.stubFor(post(urlEqualTo("/xml"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_XML.toString()))
                .withRequestBody(equalTo(xmlBody))
                .willReturn(
                        aResponse()
                                .withBody(xmlBody)
                                .withHeader(CONTENT_TYPE, APPLICATION_XML.toString())
                                .withHeader(CONTENT_LENGTH, String.valueOf(xmlBody.length()))
                                .withStatus(200)
                )
        );

        WebTarget webTarget = xmlHttpRequest.target("http://localhost:8080/xml");

        ResponseHandler<XmlWrapper> responseHandler = webTarget
                .post(xmlBody, XmlWrapper.class);
        responseHandler.filter(ResponseHandler::hasNotContent).ifPassed(r -> LOGGER.info(r.getErrorText()));

        assertTrue(responseHandler.isSuccess());
        assertTrue(responseHandler.hasContent());
        assertTrue(responseHandler.containsHeader(CONTENT_LENGTH));
        assertEquals(String.valueOf(xmlBody.length()), responseHandler.getFirstHeaderValue(CONTENT_LENGTH));

        XmlWrapper parsedXml = webTarget.post(xmlBody, XmlWrapper.class).get();

        assertEquals(1, parsedXml.id);
    }

    @Test
    public void withoutParseTest() {
        String text = "abcd";
        wireMockRule.stubFor(post(urlEqualTo("/text"))
                .willReturn(
                        aResponse()
                                .withBody(text)
                                .withStatus(200)
                )
        );

        ResponseHandler<String> responseHandler = httpRequestWithoutParse.target("http://localhost:8080/text")
                .post(String.class);

        assertEquals("abcd", responseHandler.get());
    }

    private static class XmlWrapper {
        private int id;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }
    }


}
