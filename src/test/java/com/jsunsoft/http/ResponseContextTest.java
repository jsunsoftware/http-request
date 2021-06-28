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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
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
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8.name()));

        BasicHttpEntity basicHttpEntity = new BasicHttpEntity();
        basicHttpEntity.setContent(inputStream);
        basicHttpEntity.setContentLength(content.length());
        basicHttpEntity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        httpEntity = basicHttpEntity;
    }

    @Test
    public void testBasicResponseContextMethods() throws IOException {
        HttpResponse httpResponse = new BasicHttpResponse(new BasicStatusLine(new ProtocolVersion("", 1, 1), 200, ""));
        httpResponse.setEntity(httpEntity);
        ResponseBodyReaderContext responseContext = new BasicResponseBodyReaderContext(httpResponse, String.class);
        Assert.assertEquals(content.length(), responseContext.getContentLength());
        Assert.assertEquals(content, ResponseBodyReader.stringReader().read(new BasicResponseBodyReaderContext(httpResponse, String.class)));
        Assert.assertEquals(ContentType.APPLICATION_JSON.getMimeType(), responseContext.getContentType().getMimeType());
    }
}
