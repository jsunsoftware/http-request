/*
 * Copyright (c) 2021. Benik Arakelyan
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

import com.jsunsoft.http.annotations.Beta;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collection;

@Beta
class ImmutableWebTarget extends BasicWebTarget {

    ImmutableWebTarget(CloseableHttpClient closeableHttpClient, URI uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig) {
        super(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
    }

    ImmutableWebTarget(CloseableHttpClient closeableHttpClient, String uri, Collection<Header> defaultHeaders, Collection<NameValuePair> defaultRequestParameters, ResponseBodyReaderConfig responseBodyReaderConfig) throws URISyntaxException {
        super(closeableHttpClient, uri, defaultHeaders, defaultRequestParameters, responseBodyReaderConfig);
    }

    private ImmutableWebTarget(CloseableHttpClient closeableHttpClient, URIBuilder uriBuilder, HttpUriRequestBuilder httpUriRequestBuilder, ResponseBodyReaderConfig responseBodyReaderConfig) {
        super(closeableHttpClient, uriBuilder, httpUriRequestBuilder, responseBodyReaderConfig);
    }

    private WebTarget toBasicWebTarget() {
        return new BasicWebTarget(this);
    }

    @Override
    public WebTarget path(String path) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                HttpRequestUtils.appendPath(getUriBuilder(), path),
                getHttpUriRequestBuilder(),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public WebTarget setPath(String path) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                getUriBuilder().setPath(path),
                getHttpUriRequestBuilder(),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public WebTarget removeHeader(Header header) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                getUriBuilder(),
                getHttpUriRequestBuilder().removeHeader(header),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public WebTarget removeHeaders(String name) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                getUriBuilder(),
                getHttpUriRequestBuilder().removeHeaders(name),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public WebTarget updateHeader(Header header) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                getUriBuilder(),
                getHttpUriRequestBuilder().setHeader(header),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public WebTarget addHeader(Header header) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                getUriBuilder(),
                getHttpUriRequestBuilder().addHeader(header),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public WebTarget setCharset(Charset charset) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                getUriBuilder(),
                getHttpUriRequestBuilder().setCharset(charset),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public WebTarget setRequestConfig(RequestConfig requestConfig) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                getUriBuilder(),
                getHttpUriRequestBuilder().setConfig(requestConfig),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public WebTarget addParameter(NameValuePair nameValuePair) {
        return new ImmutableWebTarget(
                getCloseableHttpClient(),
                getUriBuilder(),
                getHttpUriRequestBuilder().addParameter(nameValuePair),
                getResponseBodyReaderConfig()
        );
    }

    @Override
    public Response request(HttpMethod method, HttpEntity httpEntity) {
        return new BasicWebTarget(
                getCloseableHttpClient(),
                getUriBuilder(),
                getHttpUriRequestBuilder(),
                getResponseBodyReaderConfig()
        ).request(method, httpEntity);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, HttpEntity httpEntity, Class<T> responseType) {
        return toBasicWebTarget().request(method, httpEntity, responseType);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, HttpEntity httpEntity, TypeReference<T> responseType) {
        return toBasicWebTarget().request(method, httpEntity, responseType);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, Class<T> responseType) {
        return toBasicWebTarget().request(method, responseType);
    }

    @Override
    public Response request(HttpMethod method, HttpContext context) {
        return toBasicWebTarget().request(method, context);
    }

    @Override
    public <T> ResponseHandler<T> request(HttpMethod method, TypeReference<T> typeReference) {
        return toBasicWebTarget().request(method, typeReference);
    }
}
