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

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;

final class BasicResponseBodyReaderContext<T> implements ResponseBodyReaderContext<T> {
    private final ClassicHttpResponse httpResponse;
    private final Class<T> type;
    private final Type genericType;
    private final URI uri;

    BasicResponseBodyReaderContext(ClassicHttpResponse httpResponse, Class<T> type, Type genericType, URI uri) {
        this.httpResponse = ArgsCheck.notNull(httpResponse, "httpResponse");
        this.type = ArgsCheck.notNull(type, "type");
        this.genericType = ArgsCheck.notNull(genericType, "genericType");
        this.uri = ArgsCheck.notNull(uri, "uri");
    }

    @Override
    public InputStream getContent() throws IOException {
        return httpResponse.getEntity().getContent();
    }

    @Override
    public ContentType getContentType() {
        return HttpRequestUtils.getContentTypeFromHttpEntity(getHttpEntity());
    }

    @Override
    public long getContentLength() {
        return getHttpEntity().getContentLength();
    }

    @Override
    public HttpEntity getHttpEntity() {
        return httpResponse.getEntity();
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public Type getGenericType() {
        return genericType;
    }

    @Override
    public int getStatusCode() {
        return httpResponse.getCode();
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public boolean hasEntity() {
        return getHttpEntity() != null;
    }
}
