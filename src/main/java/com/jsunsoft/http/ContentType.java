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

import java.nio.charset.Charset;

public class ContentType {
    private final String mimeType;
    private final Charset charset;

    private ContentType(String mimeType, Charset charset) {
        this.mimeType = mimeType;
        this.charset = charset;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Charset getCharset() {
        return charset;
    }

    public static ContentType create(String mimeType, Charset charset) {
        return new ContentType(mimeType, charset);
    }

    public static ContentType create(String mimeType) {
        return new ContentType(mimeType, null);
    }

    static ContentType create(org.apache.http.entity.ContentType ct) {
        return new ContentType(ct.getMimeType(), ct.getCharset());
    }

    static ContentType create(HttpEntity httpEntity) {
        return create(org.apache.http.entity.ContentType.getOrDefault(httpEntity));
    }
}
