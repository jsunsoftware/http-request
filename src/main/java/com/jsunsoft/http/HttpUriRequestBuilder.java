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

import org.apache.http.HttpRequest;
import org.apache.http.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.HeaderGroup;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

class HttpUriRequestBuilder {

    private String method;
    private Charset charset;
    private ProtocolVersion version;
    private URI uri;
    private HeaderGroup headerGroup;
    private HttpEntity entity;
    private List<NameValuePair> parameters;
    private RequestConfig config;

    HttpUriRequestBuilder(final String method) {
        super();
        this.charset = Consts.UTF_8;
        this.method = method;
    }

    HttpUriRequestBuilder(final String method, final URI uri) {
        super();
        this.method = method;
        this.uri = uri;
    }

    HttpUriRequestBuilder(final String method, final String uri) {
        super();
        this.method = method;
        this.uri = uri != null ? URI.create(uri) : null;
    }

    HttpUriRequestBuilder() {
        this(null);
    }

    public static HttpUriRequestBuilder create(final String method) {
        Args.notBlank(method, "HTTP method");
        return new HttpUriRequestBuilder(method);
    }

    public HttpUriRequestBuilder setMethod(String method) {
        this.method = method;
        return this;
    }

    public static HttpUriRequestBuilder copy(final HttpRequest request) {
        Args.notNull(request, "HTTP request");
        return new HttpUriRequestBuilder().doCopy(request);
    }

    private HttpUriRequestBuilder doCopy(final HttpRequest request) {
        if (request == null) {
            return this;
        }
        method = request.getRequestLine().getMethod();
        version = request.getRequestLine().getProtocolVersion();

        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        headerGroup.clear();
        headerGroup.setHeaders(request.getAllHeaders());

        parameters = null;
        entity = null;

        if (request instanceof HttpEntityEnclosingRequest) {
            final HttpEntity originalEntity = ((HttpEntityEnclosingRequest) request).getEntity();
            final ContentType contentType = ContentType.get(originalEntity);
            if (contentType != null &&
                    contentType.getMimeType().equals(ContentType.APPLICATION_FORM_URLENCODED.getMimeType())) {
                try {
                    final List<NameValuePair> formParams = URLEncodedUtils.parse(originalEntity);
                    if (!formParams.isEmpty()) {
                        parameters = formParams;
                    }
                } catch (final IOException ignore) {
                }
            } else {
                entity = originalEntity;
            }
        }

        if (request instanceof HttpUriRequest) {
            uri = ((HttpUriRequest) request).getURI();
        } else {
            uri = URI.create(request.getRequestLine().getUri());
        }

        if (request instanceof Configurable) {
            config = ((Configurable) request).getConfig();
        } else {
            config = null;
        }
        return this;
    }

    HttpUriRequestBuilder copyBuilder() {
        HttpUriRequestBuilder copyHttpUriRequestBuilder = new HttpUriRequestBuilder();

        copyHttpUriRequestBuilder.method = method;
        copyHttpUriRequestBuilder.charset = charset;
        copyHttpUriRequestBuilder.version = version;
        copyHttpUriRequestBuilder.uri = uri;

        if (headerGroup != null) {
            copyHttpUriRequestBuilder.headerGroup = new HeaderGroup();
            copyHttpUriRequestBuilder.headerGroup.setHeaders(headerGroup.getAllHeaders());
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

    public Header getFirstHeader(final String name) {
        return headerGroup != null ? headerGroup.getFirstHeader(name) : null;
    }

    public Header getLastHeader(final String name) {
        return headerGroup != null ? headerGroup.getLastHeader(name) : null;
    }

    public Header[] getHeaders(final String name) {
        return headerGroup != null ? headerGroup.getHeaders(name) : null;
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
        for (final HeaderIterator i = headerGroup.iterator(); i.hasNext(); ) {
            final Header header = i.nextHeader();
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
        this.headerGroup.updateHeader(header);
        return this;
    }

    public HttpUriRequestBuilder setHeader(final String name, final String value) {
        if (headerGroup == null) {
            headerGroup = new HeaderGroup();
        }
        this.headerGroup.updateHeader(new BasicHeader(name, value));
        return this;
    }

    public HttpEntity getEntity() {
        return entity;
    }

    public HttpUriRequestBuilder setEntity(final HttpEntity entity) {
        this.entity = entity;
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

    public RequestConfig getConfig() {
        return config;
    }

    public HttpUriRequestBuilder setConfig(final RequestConfig config) {
        this.config = config;
        return this;
    }

    public HttpUriRequest build() {
        final HttpRequestBase result;
        URI uriNotNull = this.uri != null ? this.uri : URI.create("/");
        HttpEntity entityCopy = this.entity;
        if (parameters != null && !parameters.isEmpty()) {
            if (entityCopy == null && (HttpPost.METHOD_NAME.equalsIgnoreCase(method)
                    || HttpPut.METHOD_NAME.equalsIgnoreCase(method))) {
                entityCopy = new UrlEncodedFormEntity(parameters, charset != null ? charset : HTTP.DEF_CONTENT_CHARSET);
            } else {
                try {
                    uriNotNull = new URIBuilder(uriNotNull)
                            .setCharset(this.charset)
                            .addParameters(parameters)
                            .build();
                } catch (final URISyntaxException ex) {
                    // should never happen
                }
            }
        }
        if (entityCopy == null) {
            result = new InternalRequest(method);
        } else {
            final InternalEntityEclosingRequest request = new InternalEntityEclosingRequest(method);
            request.setEntity(entityCopy);
            result = request;
        }
        result.setProtocolVersion(this.version);
        result.setURI(uriNotNull);
        if (this.headerGroup != null) {
            result.setHeaders(this.headerGroup.getAllHeaders());
        }
        result.setConfig(this.config);
        return result;
    }

    static class InternalRequest extends HttpRequestBase {

        private final String method;

        InternalRequest(final String method) {
            super();
            this.method = method;
        }

        @Override
        public String getMethod() {
            return this.method;
        }

    }

    static class InternalEntityEclosingRequest extends HttpEntityEnclosingRequestBase {

        private final String method;

        InternalEntityEclosingRequest(final String method) {
            super();
            this.method = method;
        }

        @Override
        public String getMethod() {
            return this.method;
        }

    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("RequestBuilder [method=")
                .append(method)
                .append(", charset=")
                .append(charset)
                .append(", version=")
                .append(version)
                .append(", uri=")
                .append(uri)
                .append(", headerGroup=")
                .append(headerGroup)
                .append(", entity=")
                .append(entity)
                .append(", parameters=")
                .append(parameters)
                .append(", config=")
                .append(config)
                .append("]");
        return builder.toString();
    }
}
