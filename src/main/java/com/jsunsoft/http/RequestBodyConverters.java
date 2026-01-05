/*
 * Copyright (c) 2025. Benik Arakelyan
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
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.nio.charset.Charset;

import static java.nio.charset.StandardCharsets.UTF_8;

class RequestBodyConverters {

    private RequestBodyConverters() {
    }

    static RequestBodyConverter jsonConverter(ObjectMapper json) {
        return new JsonConverter(json);
    }

    static RequestBodyConverter xmlConverter(ObjectMapper xml) {
        return new XmlConverter(xml);
    }

    private static final class JsonConverter implements RequestBodyConverter {
        private final ObjectMapper json;

        JsonConverter(ObjectMapper json) {
            this.json = ArgsCheck.notNull(json, "json");
        }

        @Override
        public boolean canConvert(RequestBodyConverterContext context) {
            ContentType ct = context.getContentType();
            return ContentType.APPLICATION_JSON.isSameMimeType(ct);
        }

        @Override
        public HttpEntity convert(RequestBodyConverterContext context) throws RequestException {
            Charset charset = resolveCharset(context);
            try {
                String payload = json.writeValueAsString(context.getBody());
                return new StringEntity(payload, ContentType.APPLICATION_JSON.withCharset(charset));
            } catch (Exception e) {
                throw new RequestException("Serialization of request body failed.", e);
            }
        }
    }

    private static final class XmlConverter implements RequestBodyConverter {
        private final ObjectMapper xml;

        XmlConverter(ObjectMapper xml) {
            this.xml = ArgsCheck.notNull(xml, "xml");
        }

        @Override
        public boolean canConvert(RequestBodyConverterContext context) {
            ContentType ct = context.getContentType();
            return ContentType.APPLICATION_XML.isSameMimeType(ct) || ContentType.TEXT_XML.isSameMimeType(ct);
        }

        @Override
        public HttpEntity convert(RequestBodyConverterContext context) throws RequestException {
            Charset charset = resolveCharset(context);
            ContentType ct = context.getContentType();
            try {
                String payload = xml.writeValueAsString(context.getBody());
                ContentType outCt = ContentType.TEXT_XML.isSameMimeType(ct)
                        ? ContentType.TEXT_XML.withCharset(charset)
                        : ContentType.APPLICATION_XML.withCharset(charset);
                return new StringEntity(payload, outCt);
            } catch (Exception e) {
                throw new RequestException("Serialization of request body failed.", e);
            }
        }
    }

    private static Charset resolveCharset(RequestBodyConverterContext context) {
        if (context.getCharset() != null) {
            return context.getCharset();
        }
        ContentType ct = context.getContentType();
        if (ct != null && ct.getCharset() != null) {
            return ct.getCharset();
        }
        return UTF_8;
    }
}
