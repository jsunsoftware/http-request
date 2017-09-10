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
import java.nio.charset.StandardCharsets;

public class ContentType {
    public static final ContentType APPLICATION_ATOM_XML = create(
            "application/atom+xml", StandardCharsets.ISO_8859_1);
    public static final ContentType APPLICATION_FORM_URLENCODED = create(
            "application/x-www-form-urlencoded", StandardCharsets.ISO_8859_1);
    public static final ContentType APPLICATION_JSON = create(
            "application/json", StandardCharsets.UTF_8);
    public static final ContentType APPLICATION_OCTET_STREAM = create(
            "application/octet-stream", (Charset) null);
    public static final ContentType APPLICATION_SVG_XML = create(
            "application/svg+xml", StandardCharsets.ISO_8859_1);
    public static final ContentType APPLICATION_XHTML_XML = create(
            "application/xhtml+xml", StandardCharsets.ISO_8859_1);
    public static final ContentType APPLICATION_XML = create(
            "application/xml", StandardCharsets.ISO_8859_1);
    public static final ContentType MULTIPART_FORM_DATA = create(
            "multipart/form-data", StandardCharsets.ISO_8859_1);
    public static final ContentType TEXT_HTML = create(
            "text/html", StandardCharsets.ISO_8859_1);
    public static final ContentType TEXT_PLAIN = create(
            "text/plain", StandardCharsets.ISO_8859_1);
    public static final ContentType TEXT_XML = create(
            "text/xml", StandardCharsets.ISO_8859_1);
    public static final ContentType WILDCARD = create(
            "*/*", (Charset) null);

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

    public static ContentType create(String mimeType, String charset) {
        return create(mimeType, Charset.forName(charset));
    }

    public static ContentType create(String mimeType, Charset charset) {
        return new ContentType(mimeType, charset);
    }

    public static ContentType create(String mimeType) {
        return new ContentType(mimeType, null);
    }

    @Override
    public String toString() {
        return "ContentType{" +
                "mimeType='" + mimeType + '\'' +
                ", charset=" + charset +
                '}';
    }

    static ContentType create(org.apache.http.entity.ContentType ct) {
        return new ContentType(ct.getMimeType(), ct.getCharset());
    }

    static ContentType create(HttpEntity httpEntity) {
        return create(org.apache.http.entity.ContentType.getOrDefault(httpEntity));
    }
}
