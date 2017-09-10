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

import java.lang.reflect.Type;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;

//import org.apache.http.entity.ContentType;

/**
 * ResponseHandler objects are immutable they can be shared.
 *
 * @param <T> Type of deserialized content from response
 */

public final class ResponseHandler<T> {

    private final int statusCode;
    private final T content;
    private final String errorText;
    private final boolean isVoidType;
    private final ContentType contentType;
    private final URI uri;
    private final boolean success;
    private final ConnectionFailureType connectionFailureType;

    ResponseHandler(BasicHttpRequest<T> httpRequest, ConnectionFailureType connectionFailureType) {
        this(null, SC_SERVICE_UNAVAILABLE, "Connection was aborted", httpRequest.getType(), null, httpRequest.getUri(), connectionFailureType);
    }

    ResponseHandler(T content, int statusCode, String errorText, Type type, ContentType contentType, URI uri) {
        this(content, statusCode, errorText, type, contentType, uri, BasicConnectionFailureType.NONE);
    }

    private ResponseHandler(T content, int statusCode, String errorText, Type type, ContentType contentType, URI uri, ConnectionFailureType connectionFailureType) {
        this.statusCode = statusCode;
        this.content = content;
        this.errorText = errorText;
        this.isVoidType = HttpRequestUtils.isVoidType(type);
        this.contentType = contentType;
        this.uri = ArgsCheck.notNull(uri, "uri");
        this.success = HttpRequestUtils.isSuccess(statusCode);
        this.connectionFailureType = ArgsCheck.notNull(connectionFailureType, "connectionFailureType");
    }

    /**
     * @return {@code true} If content is present else {@code false}
     */
    public boolean hasContent() {
        return content != null;
    }

    /**
     * @return {@code true} If hasn't content else {@code false}
     */
    public boolean hasNotContent() {
        return content == null;
    }

    /**
     * @return Status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @param defaultValue value to return if content isn't present
     * @return Deserialized Content from response. If content isn't present returns defaultValue.
     * @throws UnsupportedOperationException if generic type is a Void
     */
    public T orElse(T defaultValue) {
        check();
        return content == null ? defaultValue : content;
    }

    /**
     * @param defaultValue value to return if status code is success and hasn't body
     * @return Deserialized Content from response. If hasn't body returns defaultValue.
     * @throws ResponseException             If status code is not success
     * @throws UnsupportedOperationException if generic type is a Void
     */
    public T orElseThrow(T defaultValue) {
        check();
        if (isNonSuccess()) {
            throw new ResponseException(statusCode, errorText, uri.toString());
        }
        return content == null ? defaultValue : content;
    }

    /**
     * @param exceptionFunction Instance of type {@link Function} by parameter this which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code null}.
     * @throws X If status code isn't success.
     */
    public <X extends Throwable> T orThrow(Function<ResponseHandler<? super T>, X> exceptionFunction) throws X {
        check();
        if (isNonSuccess()) {
            throw exceptionFunction.apply(this);
        }
        return content;
    }

    /**
     * @param defaultValue      Value to return if content is {@code null}
     * @param exceptionFunction Instance of type {@link Function} by parameter this which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code defaultValue}.
     * @throws X If status code isn't success.
     */
    public <X extends Throwable> T orThrow(T defaultValue, Function<ResponseHandler<? super T>, X> exceptionFunction) throws X {
        check();
        if (isNonSuccess()) {
            throw exceptionFunction.apply(this);
        }
        return content == null ? defaultValue : content;
    }

    /**
     * @param exceptionSupplier Instance of type {@link Supplier} which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code null}.
     * @throws X If status code isn't success.
     */
    public <X extends Throwable> T getOrThrow(Supplier<X> exceptionSupplier) throws X {
        check();
        if (isNonSuccess()) {
            throw exceptionSupplier.get();
        }
        return content;
    }

    /**
     * @param defaultValue      Value to return if content is {@code null}
     * @param exceptionSupplier Instance of type {@link Supplier} which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code defaultValue}.
     * @throws X If status code isn't success.
     */
    public <X extends Throwable> T getOrThrow(T defaultValue, Supplier<X> exceptionSupplier) throws X {
        check();
        if (isNonSuccess()) {
            throw exceptionSupplier.get();
        }
        return content == null ? defaultValue : content;
    }

    /**
     * @return Content from response. Returns null if hasn't body
     * @throws ResponseException             If response code is not success
     * @throws UnsupportedOperationException if generic type is a Void
     */
    public T orElseThrow() {
        check();
        if (isSuccess()) {
            return content;
        }
        throw new ResponseException(statusCode, errorText, uri.toString());
    }

    /**
     * Strongly recommend call get method after check content is present.
     * For example
     * <pre>
     *     if(responseHandler.hasContent()){
     *         responseHandler.get()
     *     }
     * </pre>
     * @return Deserialized content from response.
     * @throws NoSuchElementException        If content is not present
     * @throws UnsupportedOperationException if generic type is a Void
     * @see ResponseHandler#orElse(Object)
     * @see ResponseHandler#ifHasContent(Consumer)
     */
    public T get() {
        check();
        if (content == null) {
            throw new NoSuchElementException("Content is not present: Response code: [" + statusCode + ']');
        }
        return content;
    }

    /**
     * @return Deserialized content from response as optional instance.
     * @throws UnsupportedOperationException if generic type is a Void
     */
    Optional<T> getAsOptional() {
        check();
        return Optional.ofNullable(content);
    }

    /**
     * @return Returns the error text if the connection failed but the server sent useful data nonetheless.
     * @throws NoSuchElementException        If error text is not present
     * @throws UnsupportedOperationException if generic type is a Void
     */
    public String getErrorText() {
        if (errorText == null) {
            throw new IllegalStateException("Error text is not available: Response code: [" + statusCode + ']');
        }
        return errorText;
    }

    /**
     * @return Returns the connection URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     * @return Content type of response
     */
    public ContentType getContentType() {
        return contentType;
    }

    /**
     * @return Returns <b>true</b> if status code contains [200, 300) else <b>false</b>
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return Returns <b>true</b> if status code isn't contains [200, 300) else <b>false</b>
     */
    public boolean isNonSuccess() {
        return !isSuccess();
    }

    /**
     * If has a content, invoke the specified consumer with the content, otherwise do nothing.
     *
     * @param consumer block to be executed if has a content
     * @throws IllegalArgumentException if {@code consumer} is null
     */
    public void ifHasContent(Consumer<? super T> consumer) {
        ArgsCheck.notNull(consumer, "consumer");
        if (content != null) {
            consumer.accept(content);
        }
    }

    /**
     * If status code is success , invoke the specified consumer with the responseHandler and returns {@code OtherwiseSupport} with ignore else {@code OtherwiseSupport} with not ignore.
     *
     * @param consumer block to be executed if status code is success.
     * @return OtherwiseSupport instance to support action otherwise.
     * @see OtherwiseSupport#otherwise(Consumer)
     */
    public OtherwiseSupport<T> ifSuccess(Consumer<ResponseHandler<T>> consumer) {
        ArgsCheck.notNull(consumer, "consumer");

        OtherwiseSupport<T> otherwiseSupportResult;

        if (success) {
            consumer.accept(this);
            otherwiseSupportResult = OtherwiseSupport.createIgnored(this);
        } else {
            otherwiseSupportResult = OtherwiseSupport.createNotIgnored(this);
        }
        return otherwiseSupportResult;
    }

    public FilterSupport<T> filter(Predicate<ResponseHandler<T>> predicate) {
        return FilterSupport.create(this, predicate);
    }

    /**
     * @return connectionFailureType.
     * @see ConnectionFailureType
     */
    //todo rename and make public
    ConnectionFailureType getConnectionFailureType() {
        return connectionFailureType;
    }

    @Override
    public String toString() {
        return "ResponseHandler{" +
                "statusCode=" + statusCode +
                ", content=" + content +
                ", errorText='" + errorText + '\'' +
                ", uri=" + uri +
                ", connectionFailureType=" + connectionFailureType +
                '}';
    }

    private void check() {
        if (isVoidType) {
            throw new UnsupportedOperationException("Content is not available. Generic type is a Void");
        }
    }
}
