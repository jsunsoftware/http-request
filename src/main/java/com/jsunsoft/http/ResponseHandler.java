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
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.entity.ContentType;

import java.net.URI;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * ResponseHandler objects are immutable they can be shared.
 * <p>
 * The instance which contains converted response content and provide a lot of methods to manipulation and checking.
 *
 * @param <T> Type of converted content from response
 */
public interface ResponseHandler<T> {
    /**
     * @return {@code true} If content is present else {@code false}
     */
    boolean hasContent();

    /**
     * @return {@code true} If hasn't content else {@code false}
     */
    default boolean hasNotContent() {
        return !hasContent();
    }

    /**
     * @return the status code
     *
     * @deprecated use {@link #getCode()} instead.
     */
    default int getStatusCode() {
        return getCode();
    }

    /**
     * Obtains the code of this response message.
     *
     * @return the status code
     */
    int getCode();

    /**
     * @param defaultValue value to return if content isn't present
     * @return Deserialized Content from response. If content isn't present returns defaultValue.
     * @throws UnsupportedOperationException if generic type is a Void
     */
    T orElse(T defaultValue);

    /**
     * @param defaultValue value to return if status code is success and hasn't body
     * @return Deserialized Content from response. If hasn't body returns defaultValue.
     * @throws UnexpectedStatusCodeException If status code is not success
     * @throws UnsupportedOperationException if generic type is a Void
     */
    T orElseThrow(T defaultValue);

    /**
     * @param exceptionFunction Instance of type {@link Function} by parameter this which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code null}.
     * @throws X If status code isn't success.
     */
    <X extends Throwable> T orThrow(Function<ResponseHandler<? super T>, X> exceptionFunction) throws X;

    /**
     * @param defaultValue      Value to return if content is {@code null}
     * @param exceptionFunction Instance of type {@link Function} by parameter this which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code defaultValue}.
     * @throws X If status code isn't success.
     */
    <X extends Throwable> T orThrow(T defaultValue, Function<ResponseHandler<? super T>, X> exceptionFunction) throws X;

    /**
     * @param exceptionSupplier Instance of type {@link Supplier} which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code null}.
     * @throws X If status code isn't success.
     */
    <X extends Throwable> T getOrThrow(Supplier<X> exceptionSupplier) throws X;

    /**
     * @param defaultValue      Value to return if content is {@code null}
     * @param exceptionSupplier Instance of type {@link Supplier} which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code defaultValue}.
     * @throws X If status code isn't success.
     */
    <X extends Throwable> T getOrThrow(T defaultValue, Supplier<X> exceptionSupplier) throws X;

    /**
     * @return Content from response. Returns null if hasn't body
     * @throws UnexpectedStatusCodeException If response code is not success
     * @throws UnsupportedOperationException if generic type is a Void
     */
    T orElseThrow();

    /**
     * Strongly recommend call get method after check content is present.
     * For example
     * <pre>
     *     if(responseHandler.hasContent()){
     *         responseHandler.get()
     *     }
     * </pre>
     *
     * @return Deserialized content from response.
     * @throws NoSuchContentException        If content is not present
     * @throws UnsupportedOperationException if generic type is a Void
     * @see ResponseHandler#orElse(Object)
     * @see ResponseHandler#getAsOptional()
     * @see ResponseHandler#ifHasContent(Consumer)
     */
    T get();

    /**
     * @return Deserialized Content from response.
     * @throws MissingResponseBodyException  If content isn't present
     * @throws UnexpectedStatusCodeException If response code is not success
     * @throws UnsupportedOperationException if generic type is a Void
     */
    T requiredGet();

    /**
     * @return Deserialized Content from response as {@link Optional} instance. If content isn't present returns empty {@link Optional} instance.
     * @throws UnsupportedOperationException if generic type is a Void
     */
    Optional<T> getAsOptional();

    /**
     * @return Deserialized Content from response as {@link Optional}. If content isn't present returns empty {@link Optional}.
     * @throws UnsupportedOperationException if generic type is a Void
     * @throws UnexpectedStatusCodeException If response code is not success
     */
    Optional<T> getAsOptionalOrThrow();

    /**
     * @return Returns the error text if the connection failed but the server sent useful data nonetheless.
     * @throws NoSuchElementException        If error text is not present
     * @throws UnsupportedOperationException if generic type is a Void
     */
    String getErrorText();

    /**
     * @return Returns the connection URI
     */
    URI getURI();

    /**
     * Obtains the status line of this response.
     *
     * @return the status line.
     */
    StatusLine getStatusLine();

    /**
     * @return Content type of response
     */
    ContentType getContentType();

    /**
     * @return Returns <b>true</b> if status code contains [200, 300) else <b>false</b>
     */
    boolean isSuccess();

    /**
     * @return Returns <b>true</b> if status code isn't contains [200, 300) else <b>false</b>
     */
    default boolean isNonSuccess() {
        return !isSuccess();
    }

    /**
     * If has a content, invoke the specified consumer with the content, otherwise do nothing.
     *
     * @param consumer block to be executed if has a content
     * @throws IllegalArgumentException if {@code consumer} is null
     */
    void ifHasContent(Consumer<? super T> consumer);

    /**
     * If status code is success , invoke the specified consumer with the responseHandler and returns {@code OtherwiseSupport} with ignore else {@code OtherwiseSupport} with not ignore.
     *
     * @param consumer block to be executed if status code is success.
     * @return OtherwiseSupport instance to support action otherwise.
     * @see OtherwiseSupport#otherwise(Consumer)
     */
    OtherwiseSupport<T> ifSuccess(Consumer<ResponseHandler<T>> consumer);

    /**
     * If status code is not success , invoke the specified consumer with the responseHandler.
     *
     * @param consumer block to be executed if status code is not success.
     */
    void ifNotSuccess(Consumer<ResponseHandler<T>> consumer);

    FilterSupport<T> filter(Predicate<ResponseHandler<T>> predicate);

    boolean containsHeader(String name);

    Header[] getHeaders(String name);

    Header getFirstHeader(String name);

    Header getLastHeader(String name);

    Header[] getAllHeaders();

    @Beta
    default String getFirstHeaderValue(String name) {
        Header header = getFirstHeader(name);

        return header == null ? null : header.getValue();
    }

    @Beta
    default String getLastHeaderValue(String name) {
        Header header = getLastHeader(name);

        return header == null ? null : header.getValue();
    }
}
