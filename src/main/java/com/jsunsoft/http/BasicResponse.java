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

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

class BasicResponse implements Response {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicResponse.class);

    private final ClassicHttpResponse classicHttpResponse;
    private final ResponseBodyReaderConfig responseBodyReaderConfig;
    private final URI uri;

    public BasicResponse(ClassicHttpResponse classicHttpResponse, ResponseBodyReaderConfig responseBodyReaderConfig, URI uri) {
        this.classicHttpResponse = classicHttpResponse;
        this.responseBodyReaderConfig = responseBodyReaderConfig;
        this.uri = uri;
    }

    /**
     * Ensures that the entity content is fully consumed and the content stream, if exists, is closed
     * then calls {@link EntityUtils#consume(HttpEntity)} then {@link CloseableHttpResponse#close()}
     */
    @Override
    public void close() throws IOException {

        try {
            EntityUtils.consume(getEntity());
        } catch (IOException e) {
            classicHttpResponse.close();
            throw e;
        }

        classicHttpResponse.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCode() {
        return classicHttpResponse.getCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCode(int code) throws IllegalStateException {
        classicHttpResponse.setCode(code);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getReasonPhrase() {
        return classicHttpResponse.getReasonPhrase();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReasonPhrase(String reason) throws IllegalStateException {
        classicHttpResponse.setReasonPhrase(reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HttpEntity getEntity() {
        return classicHttpResponse.getEntity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEntity(HttpEntity entity) {
        classicHttpResponse.setEntity(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {
        return classicHttpResponse.getLocale();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocale(Locale loc) {
        classicHttpResponse.setLocale(loc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProtocolVersion getVersion() {
        return classicHttpResponse.getVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setVersion(ProtocolVersion version) {
        classicHttpResponse.setVersion(version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(String name) {
        return classicHttpResponse.containsHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countHeaders(String name) {
        return classicHttpResponse.countHeaders(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header getHeader(String name) throws ProtocolException {
        return classicHttpResponse.getHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header[] getHeaders(String name) {
        return classicHttpResponse.getHeaders(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header getFirstHeader(String name) {
        return classicHttpResponse.getFirstHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header getLastHeader(String name) {
        return classicHttpResponse.getLastHeader(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Header[] getHeaders() {
        return classicHttpResponse.getHeaders();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(Header header) {
        classicHttpResponse.addHeader(header);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(String name, Object value) {
        classicHttpResponse.addHeader(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(Header header) {
        classicHttpResponse.setHeader(header);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(String name, Object value) {
        classicHttpResponse.setHeader(name, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeaders(Header... headers) {
        classicHttpResponse.setHeaders(headers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeHeader(Header header) {
        return classicHttpResponse.removeHeader(header);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeHeaders(String name) {
        return classicHttpResponse.removeHeaders(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Header> headerIterator() {
        return classicHttpResponse.headerIterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Header> headerIterator(String name) {
        return classicHttpResponse.headerIterator(name);
    }

    @Override
    public <T> T readEntity(Class<T> responseType) {
        return readEntityUnChecked(responseType, responseType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readEntity(TypeReference<T> responseType) {
        return (T) readEntityUnChecked(responseType.getRawType(), responseType.getType());
    }

    @Override
    public <T> T readEntityChecked(Class<T> responseType) throws IOException {
        return readEntityChecked(responseType, responseType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T readEntityChecked(TypeReference<T> responseType) throws IOException {
        return (T) readEntityChecked(responseType.getRawType(), responseType.getType());
    }

    private <T> T readEntityUnChecked(Class<T> type, Type genericType) {
        try {
            return readEntityChecked(type, genericType);
        } catch (ResponseBodyReaderException e) {
            throw new ResponseBodyProcessingException("Response deserialization failed. Cannot deserialize response to: [" + type + "].", e);
        } catch (IOException e) {
            throw new ResponseBodyProcessingException("Stream could not be created. Uri: [" + getURI() + "].", e);
        }
    }


    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * <p>
     * Note: method will throw any unchecked exception which will occurred in specified {@link ResponseBodyReader}.
     *
     * @param type        Java type the response entity will be converted to.
     * @param genericType Java type the response entity will be converted to.
     * @param <T>         response entity type which must match to the responseType.
     * @return Response entity
     * @throws IOException                 If the stream could not be created or error occurs reading the input stream.
     * @throws ResponseBodyReaderException If Cannot deserialize content
     */
    @SuppressWarnings("unchecked")
    private <T> T readEntityChecked(Class<T> type, Type genericType) throws IOException {
        T content;

        ResponseBodyReaderContext<T> responseBodyReaderContext = new BasicResponseBodyReaderContext<>(this, type, genericType, getURI());

        ResponseBodyReader<T> responseBodyReader =
                (ResponseBodyReader<T>) responseBodyReaderConfig.getResponseBodyReaders().stream()
                        .filter(rbr -> rbr.isReadable(responseBodyReaderContext))
                        .findFirst()
                        .orElseGet(() -> {
                            if (responseBodyReaderConfig.isUseDefaultReader() && responseBodyReaderConfig.getDefaultResponseBodyReader().isReadable(responseBodyReaderContext)){
                                return responseBodyReaderConfig.getDefaultResponseBodyReader();
                            }
                            return null;
                        });


        if (responseBodyReader != null) {
            try {
                content = responseBodyReader.read(responseBodyReaderContext);
            } catch (RuntimeException e) {
                throw new ResponseBodyReaderException("Error during reading response body by: " + responseBodyReader.getClass().getName(), e);
            }
        } else if (hasEntity()) {

            throw new ResponseBodyReaderNotFoundException(
                    "Can't found body reader for type: " + responseBodyReaderContext.getType() + " and content type: " + responseBodyReaderContext.getContentType()
            );
        } else {
            LOGGER.warn("Can't found body reader for type: {} when http entity is null.", responseBodyReaderContext.getType());
            content = null;
        }

        return content;
    }

    @Override
    public URI getURI() {
        return uri;
    }
}
