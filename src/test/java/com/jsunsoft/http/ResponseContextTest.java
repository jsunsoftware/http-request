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

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class ResponseContextTest {
    private HttpEntity httpEntity;
    private String content;

    @Before
    public final void before() throws UnsupportedEncodingException {
        content = "String to test";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        httpEntity = new BasicHttpEntity(inputStream, content.length(), ContentType.APPLICATION_JSON);
    }

    @Test
    public void testBasicResponseContextMethods() throws IOException {
        BasicClassicHttpResponse httpResponse = new BasicClassicHttpResponse(200);
        httpResponse.setEntity(httpEntity);
        ResponseBodyReaderContext<String> responseContext = new BasicResponseBodyReaderContext<>(httpResponse, String.class, String.class);
        Assert.assertEquals(content.length(), responseContext.getContentLength());
        Assert.assertEquals(content, ResponseBodyReader.stringReader().read(responseContext));
        Assert.assertEquals(ContentType.APPLICATION_JSON.getMimeType(), responseContext.getContentType().getMimeType());
    }
}
