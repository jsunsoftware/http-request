package com.jsunsoft.http;

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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

class BasicResponse implements Response {
    private static final Log LOGGER = LogFactory.getLog(BasicResponse.class);

    private final CloseableHttpResponse closeableHttpResponse;
    private final URI uri;

    public BasicResponse(CloseableHttpResponse closeableHttpResponse, URI uri) {
        this.closeableHttpResponse = closeableHttpResponse;
        this.uri = uri;
    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        closeableHttpResponse.close();
    }

    /**
     * Obtains the status line of this response.
     * The status line can be set using one of the
     * {@link #setStatusLine setStatusLine} methods,
     * or it can be initialized in a constructor.
     *
     * @return the status line, or {@code null} if not yet set
     */
    @Override
    public StatusLine getStatusLine() {
        return closeableHttpResponse.getStatusLine();
    }

    /**
     * Sets the status line of this response.
     *
     * @param statusline the status line of this response
     */
    @Override
    public void setStatusLine(StatusLine statusline) {
        closeableHttpResponse.setStatusLine(statusline);
    }

    /**
     * Sets the status line of this response.
     * The reason phrase will be determined based on the current
     * {@link #getLocale locale}.
     *
     * @param ver  the HTTP version
     * @param code the status code
     */
    @Override
    public void setStatusLine(ProtocolVersion ver, int code) {
        closeableHttpResponse.setStatusLine(ver, code);
    }

    /**
     * Sets the status line of this response with a reason phrase.
     *
     * @param ver    the HTTP version
     * @param code   the status code
     * @param reason the reason phrase, or {@code null} to omit
     */
    @Override
    public void setStatusLine(ProtocolVersion ver, int code, String reason) {
        closeableHttpResponse.setStatusLine(ver, code, reason);
    }

    /**
     * Updates the status line of this response with a new status code.
     *
     * @param code the HTTP status code.
     * @throws IllegalStateException if the status line has not be set
     * @see HttpStatus
     * @see #setStatusLine(StatusLine)
     * @see #setStatusLine(ProtocolVersion, int)
     */
    @Override
    public void setStatusCode(int code) throws IllegalStateException {
        closeableHttpResponse.setStatusCode(code);
    }

    /**
     * Updates the status line of this response with a new reason phrase.
     *
     * @param reason the new reason phrase as a single-line string, or
     *               {@code null} to unset the reason phrase
     * @throws IllegalStateException if the status line has not be set
     * @see #setStatusLine(StatusLine)
     * @see #setStatusLine(ProtocolVersion, int)
     */
    @Override
    public void setReasonPhrase(String reason) throws IllegalStateException {
        closeableHttpResponse.setReasonPhrase(reason);
    }

    /**
     * Obtains the message entity of this response, if any.
     * The entity is provided by calling {@link #setEntity setEntity}.
     *
     * @return the response entity, or
     * {@code null} if there is none
     */
    @Override
    public HttpEntity getEntity() {
        return closeableHttpResponse.getEntity();
    }

    /**
     * Associates a response entity with this response.
     * <p>
     * Please note that if an entity has already been set for this response and it depends on
     * an input stream ({@link HttpEntity#isStreaming()} returns {@code true}),
     * it must be fully consumed in order to ensure release of resources.
     *
     * @param entity the entity to associate with this response, or
     *               {@code null} to unset
     * @see HttpEntity#isStreaming()
     * @see EntityUtils#updateEntity(HttpResponse, HttpEntity)
     */
    @Override
    public void setEntity(HttpEntity entity) {
        closeableHttpResponse.setEntity(entity);
    }

    /**
     * Obtains the locale of this response.
     * The locale is used to determine the reason phrase
     * for the {@link #setStatusCode status code}.
     * It can be changed using {@link #setLocale setLocale}.
     *
     * @return the locale of this response, never {@code null}
     */
    @Override
    public Locale getLocale() {
        return closeableHttpResponse.getLocale();
    }

    /**
     * Changes the locale of this response.
     *
     * @param loc the new locale
     */
    @Override
    public void setLocale(Locale loc) {
        closeableHttpResponse.setLocale(loc);
    }

    /**
     * Returns the protocol version this message is compatible with.
     */
    @Override
    public ProtocolVersion getProtocolVersion() {
        return closeableHttpResponse.getProtocolVersion();
    }

    /**
     * Checks if a certain header is present in this message. Header values are
     * ignored.
     *
     * @param name the header name to check for.
     * @return true if at least one header with this name is present.
     */
    @Override
    public boolean containsHeader(String name) {
        return closeableHttpResponse.containsHeader(name);
    }

    /**
     * Returns all the headers with a specified name of this message. Header values
     * are ignored. Headers are orderd in the sequence they will be sent over a
     * connection.
     *
     * @param name the name of the headers to return.
     * @return the headers whose name property equals {@code name}.
     */
    @Override
    public Header[] getHeaders(String name) {
        return closeableHttpResponse.getHeaders(name);
    }

    /**
     * Returns the first header with a specified name of this message. Header
     * values are ignored. If there is more than one matching header in the
     * message the first element of {@link #getHeaders(String)} is returned.
     * If there is no matching header in the message {@code null} is
     * returned.
     *
     * @param name the name of the header to return.
     * @return the first header whose name property equals {@code name}
     * or {@code null} if no such header could be found.
     */
    @Override
    public Header getFirstHeader(String name) {
        return closeableHttpResponse.getFirstHeader(name);
    }

    /**
     * Returns the last header with a specified name of this message. Header values
     * are ignored. If there is more than one matching header in the message the
     * last element of {@link #getHeaders(String)} is returned. If there is no
     * matching header in the message {@code null} is returned.
     *
     * @param name the name of the header to return.
     * @return the last header whose name property equals {@code name}.
     * or {@code null} if no such header could be found.
     */
    @Override
    public Header getLastHeader(String name) {
        return closeableHttpResponse.getLastHeader(name);
    }

    /**
     * Returns all the headers of this message. Headers are orderd in the sequence
     * they will be sent over a connection.
     *
     * @return all the headers of this message
     */
    @Override
    public Header[] getAllHeaders() {
        return closeableHttpResponse.getAllHeaders();
    }

    /**
     * Adds a header to this message. The header will be appended to the end of
     * the list.
     *
     * @param header the header to append.
     */
    @Override
    public void addHeader(Header header) {
        closeableHttpResponse.addHeader(header);
    }

    /**
     * Adds a header to this message. The header will be appended to the end of
     * the list.
     *
     * @param name  the name of the header.
     * @param value the value of the header.
     */
    @Override
    public void addHeader(String name, String value) {
        closeableHttpResponse.addHeader(name, value);
    }

    /**
     * Overwrites the first header with the same name. The new header will be appended to
     * the end of the list, if no header with the given name can be found.
     *
     * @param header the header to set.
     */
    @Override
    public void setHeader(Header header) {
        closeableHttpResponse.setHeader(header);
    }

    /**
     * Overwrites the first header with the same name. The new header will be appended to
     * the end of the list, if no header with the given name can be found.
     *
     * @param name  the name of the header.
     * @param value the value of the header.
     */
    @Override
    public void setHeader(String name, String value) {
        closeableHttpResponse.setHeader(name, value);
    }

    /**
     * Overwrites all the headers in the message.
     *
     * @param headers the array of headers to set.
     */
    @Override
    public void setHeaders(Header[] headers) {
        closeableHttpResponse.setHeaders(headers);
    }

    /**
     * Removes a header from this message.
     *
     * @param header the header to remove.
     */
    @Override
    public void removeHeader(Header header) {
        closeableHttpResponse.removeHeader(header);
    }

    /**
     * Removes all headers with a certain name from this message.
     *
     * @param name The name of the headers to remove.
     */
    @Override
    public void removeHeaders(String name) {
        closeableHttpResponse.removeHeaders(name);
    }

    /**
     * Returns an iterator of all the headers.
     *
     * @return Iterator that returns Header objects in the sequence they are
     * sent over a connection.
     */
    @Override
    public HeaderIterator headerIterator() {
        return closeableHttpResponse.headerIterator();
    }

    /**
     * Returns an iterator of the headers with a given name.
     *
     * @param name the name of the headers over which to iterate, or
     *             {@code null} for all headers
     * @return Iterator that returns Header objects with the argument name
     * in the sequence they are sent over a connection.
     */
    @Override
    public HeaderIterator headerIterator(String name) {
        return closeableHttpResponse.headerIterator(name);
    }

    /**
     * Returns the parameters effective for this message as set by
     * {@link #setParams(org.apache.http.params.HttpParams)}.
     *
     * @deprecated (4.3) use configuration classes provided 'org.apache.http.config'
     * and 'org.apache.http.client.config'
     */
    @Override
    @Deprecated
    public org.apache.http.params.HttpParams getParams() {
        return closeableHttpResponse.getParams();
    }

    /**
     * Provides parameters to be used for the processing of this message.
     *
     * @param params the parameters
     * @deprecated (4.3) use configuration classes provided 'org.apache.http.config'
     * and 'org.apache.http.client.config'
     */
    @Override
    @Deprecated
    public void setParams(org.apache.http.params.HttpParams params) {
        closeableHttpResponse.setParams(params);
    }

    public URI getUri() {
        return uri;
    }
}
