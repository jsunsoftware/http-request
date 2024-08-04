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

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Args;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

class HttpUriRequestBuilder {

    private String method;
    private URI uri;
    private Charset charset;
    private ProtocolVersion version;
    private HeaderGroup headerGroup;
    private HttpEntity entity;
    private List<NameValuePair> parameters;
    private RequestConfig config;

    HttpUriRequestBuilder(final String method) {
        super();
        this.charset = StandardCharsets.UTF_8;
        this.method = method;
    }

    HttpUriRequestBuilder(final Method method) {
        this(method.name());
    }

    HttpUriRequestBuilder(final String method, final URI uri) {
        super();
        this.method = method;
        this.uri = uri;
        this.charset = StandardCharsets.UTF_8;
    }

    HttpUriRequestBuilder(final Method method, final URI uri) {
        this(method.name(), uri);
    }

    HttpUriRequestBuilder(final String method, final String uri) {
        this(method, uri != null ? URI.create(uri) : null);
    }

    HttpUriRequestBuilder(final Method method, final String uri) {
        this(method.name(), uri);
    }

    HttpUriRequestBuilder() {
        this((String) null);
    }

    public static HttpUriRequestBuilder create(final String method) {
        Args.notBlank(method, "HTTP method");
        return new HttpUriRequestBuilder(method);
    }

    HttpUriRequestBuilder copyBuilder() {
        HttpUriRequestBuilder copyHttpUriRequestBuilder = new HttpUriRequestBuilder();

        copyHttpUriRequestBuilder.method = method;
        copyHttpUriRequestBuilder.uri = uri;
        copyHttpUriRequestBuilder.charset = charset;
        copyHttpUriRequestBuilder.version = version;

        if (headerGroup != null) {
            copyHttpUriRequestBuilder.headerGroup = new HeaderGroup();
            Header[] oldHeaders = headerGroup.getHeaders();

            Header[] copyHeaders = Arrays.copyOf(oldHeaders, oldHeaders.length);

            copyHttpUriRequestBuilder.headerGroup.setHeaders(copyHeaders);
        }

        if (entity != null) {
            throw new IllegalStateException("After initializing the httpEntity builder can't be copied.");
        }

        if (parameters != null) {
            copyHttpUriRequestBuilder.parameters = new ArrayList<>(parameters);
        }

        if (config != null) {
            copyHttpUriRequestBuilder.config = RequestConfig.copy(config).build();
        }

        return copyHttpUriRequestBuilder;
    }

    public HttpUriRequestBuilder setCharset(final Charset charset) {
        this.charset = charset;
        return this;
    }

    public Charset getCharset() {
        return charset;
    }

    public HttpUriRequestBuilder setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public ProtocolVersion getVersion() {
        return version;
    }

    public HttpUriRequestBuilder setVersion(final ProtocolVersion version) {
        this.version = version;
        return this;
    }

    public URI getUri() {
        return uri;
    }

    HttpUriRequestBuilder setUri(final URI uri) {
        this.uri = uri;
        return this;
    }

    HttpUriRequestBuilder setUri(final String uri) {
        this.uri = uri != null ? URI.create(uri) : null;
        return this;
    }

    public RequestConfig getConfig() {
        return config;
    }

    public HttpUriRequestBuilder setConfig(final RequestConfig config) {
        this.config = config;
        return this;
    }

    public Header[] getHeaders(final String name) {
        return headerGroup != null ? headerGroup.getHeaders(name) : null;
    }

    public HttpUriRequestBuilder setHeaders(final Header... headers) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.setHeaders(headers);
        return this;
    }

    public Header getFirstHeader(final String name) {
        return headerGroup != null ? headerGroup.getFirstHeader(name) : null;
    }

    public Header getLastHeader(final String name) {
        return headerGroup != null ? headerGroup.getLastHeader(name) : null;
    }

    public HttpUriRequestBuilder addHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.addHeader(header);
        return this;
    }

    public HttpUriRequestBuilder addHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.addHeader(new BasicHeader(name, value));
        return this;
    }

    public HttpUriRequestBuilder removeHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.removeHeader(header);
        return this;
    }

    public HttpUriRequestBuilder removeHeaders(final String name) {
        if (name == null || headerGroup == null) {
            return this;
        }
        for (final Iterator<Header> i = headerGroup.headerIterator(); i.hasNext(); ) {
            final Header header = i.next();
            if (name.equalsIgnoreCase(header.getName())) {
                i.remove();
            }
        }
        return this;
    }

    public HttpUriRequestBuilder setHeader(final Header header) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(header);
        return this;
    }

    public HttpUriRequestBuilder setHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.setHeader(new BasicHeader(name, value));
        return this;
    }

    public HttpEntity getEntity() {
        return entity;
    }

    public HttpUriRequestBuilder setEntity(final HttpEntity entity) {
        this.entity = entity;
        return this;
    }

    public HttpUriRequestBuilder setEntity(final String content, final ContentType contentType) {
        this.entity = new StringEntity(content, contentType);
        return this;
    }

    public HttpUriRequestBuilder setEntity(final String content) {
        this.entity = new StringEntity(content);
        return this;
    }

    public HttpUriRequestBuilder setEntity(final byte[] content, final ContentType contentType) {
        this.entity = new ByteArrayEntity(content, contentType);
        return this;
    }

    public List<NameValuePair> getParameters() {
        return parameters != null ? new ArrayList<>(parameters) :
                new ArrayList<>();
    }

    public HttpUriRequestBuilder addParameter(final NameValuePair nvp) {
        Args.notNull(nvp, "Name value pair");
        if (parameters == null) {
            parameters = new LinkedList<>();
        }
        parameters.add(nvp);
        return this;
    }

    public HttpUriRequestBuilder addParameter(final String name, final String value) {
        return addParameter(new BasicNameValuePair(name, value));
    }

    public HttpUriRequestBuilder addParameters(final NameValuePair... nvps) {
        for (final NameValuePair nvp : nvps) {
            addParameter(nvp);
        }
        return this;
    }

    public ClassicHttpRequest build() {
        URI uriCopy = this.uri != null ? this.uri : URI.create("/");
        HttpEntity entityCopy = this.entity;

        if (parameters != null && !parameters.isEmpty()) {
            if (entityCopy == null && (Method.POST.isSame(method) || Method.PUT.isSame(method))) {
                entityCopy = HttpEntities.createUrlEncoded(parameters, charset);
            } else {
                try {
                    uriCopy = new URIBuilder(uriCopy)
                            .setCharset(this.charset)
                            .addParameters(parameters)
                            .build();
                } catch (final URISyntaxException ex) {
                    // should never happen
                }
            }
        }

        if (entityCopy != null && Method.TRACE.isSame(method)) {
            throw new IllegalStateException(Method.TRACE + " requests may not include an entity");
        }

        final HttpUriRequestBase result = new HttpUriRequestBase(method, uriCopy);
        result.setVersion(this.version != null ? this.version : HttpVersion.HTTP_1_1);
        if (this.headerGroup != null) {
            result.setHeaders(this.headerGroup.getHeaders());
        }
        result.setEntity(entityCopy);
        result.setConfig(config);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("HttpUriRequestBuilder [method=");
        builder.append(method);
        builder.append(", charset=");
        builder.append(charset);
        builder.append(", version=");
        builder.append(version);
        builder.append(", uri=");
        builder.append(uri);
        builder.append(", headerGroup=");
        builder.append(headerGroup);
        builder.append(", entity=");
        builder.append(entity != null ? entity.getClass() : null);
        builder.append(", parameters=");
        builder.append(parameters);
        builder.append("]");
        return builder.toString();
    }
}
