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
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;

import java.net.URI;
import java.time.Duration;
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
     * Checks if the content is present.
     *
     * @return {@code true} If content is present else {@code false}
     */
    boolean hasContent();

    /**
     * Checks if the content is not present.
     *
     * @return {@code true} If hasn't content else {@code false}
     */
    default boolean hasNotContent() {
        return !hasContent();
    }

    /**
     * Gets the status code.
     *
     * @return the status code
     * @deprecated use {@link #getCode()} instead.
     */
    @Deprecated
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
     * Gets the deserialized content from the response or returns a default value if content isn't present.
     *
     * @param defaultValue value to return if content isn't present
     * @return Deserialized Content from response. If content isn't present returns defaultValue.
     * @throws UnsupportedOperationException if generic type is a Void
     */
    T orElse(T defaultValue);

    /**
     * Gets the deserialized content from the response or defaultValue
     * and throws an exception if the status code is not successful.
     *
     * @param defaultValue value to return if status code is success and hasn't body
     * @return Deserialized Content from response. If hasn't body returns defaultValue.
     * @throws ResponseException If status code is not success
     * @throws UnsupportedOperationException if generic type is a Void
     */
    T orElseThrow(T defaultValue);

    /**
     * Gets the deserialized content from the response or throws an exception provided by the exception function if the status code is not successful.
     *
     * @param exceptionFunction Instance of type {@link Function} by parameter this which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code null}.
     * @throws X If status code isn't success.
     */
    <X extends Throwable> T orThrow(Function<ResponseHandler<? super T>, X> exceptionFunction) throws X;

    /**
     * Gets the deserialized content from the response or returns a default value
     * and throws an exception provided by the exception function if the status code is not successful.
     *
     * @param defaultValue      Value to return if content is {@code null}
     * @param exceptionFunction Instance of type {@link Function} by parameter this which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code defaultValue}.
     * @throws X If status code isn't success.
     */
    <X extends Throwable> T orThrow(T defaultValue, Function<ResponseHandler<? super T>, X> exceptionFunction) throws X;

    /**
     * Gets the deserialized content from the response
     * or throws an exception provided by the exception supplier if the status code is not successful.
     *
     * @param exceptionSupplier Instance of type {@link Supplier} which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code null}.
     * @throws X If status code isn't success.
     */
    <X extends Throwable> T getOrThrow(Supplier<X> exceptionSupplier) throws X;

    /**
     * Gets the deserialized content from the response or returns a default value
     * and throws an exception provided by the exception supplier if the status code is not successful.
     *
     * @param defaultValue      Value to return if content is {@code null}
     * @param exceptionSupplier Instance of type {@link Supplier} which returns exception to throw if status code isn't success.
     * @param <X>               Type of the exception to be thrown
     * @return Deserialized content from response. If hasn't body returns {@code defaultValue}.
     * @throws X If status code isn't success.
     */
    <X extends Throwable> T getOrThrow(T defaultValue, Supplier<X> exceptionSupplier) throws X;

    /**
     * Gets the deserialized content from the response or throws an exception if the status code is not successful.
     *
     * @return Content from response. Returns null if hasn't body
     * @throws ResponseException If response code is not success
     * @throws UnsupportedOperationException if generic type is a Void
     */
    T orElseThrow();

    /**
     * Gets the deserialized content from the response.
     * <p>
     * Strongly recommend calling get method after checking if content is present.
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
     * Gets the deserialized content from the response or throws an exception if content isn't present.
     *
     * @return Deserialized Content from response.
     * @throws MissingResponseBodyException  If content isn't present
     * @throws ResponseException If response code is not success
     * @throws UnsupportedOperationException if generic type is a Void
     */
    T requiredGet();

    /**
     * Gets the deserialized content from the response as an {@link Optional} instance. If content isn't present returns empty {@link Optional} instance.
     *
     * @return Deserialized Content from response as {@link Optional} instance. If content isn't present returns empty {@link Optional} instance.
     * @throws UnsupportedOperationException if generic type is a Void
     */
    Optional<T> getAsOptional();

    /**
     * Gets the deserialized content from the response as an {@link Optional}. If content isn't present returns empty {@link Optional}.
     *
     * @return Deserialized Content from response as {@link Optional}. If content isn't present returns empty {@link Optional}.
     * @throws UnsupportedOperationException if generic type is a Void
     * @throws ResponseException If response code is not success
     */
    Optional<T> getAsOptionalOrThrow();


    /**
     * Throws an {@link UnexpectedStatusCodeException} if the status code of the response is not successful.
     *
     * @throws ResponseException If the status code is not in the successful range.
     */
    void throwIfNotSuccess() throws UnexpectedStatusCodeException;

    /**
     * Gets the error text if the connection failed, but the server sent useful data nonetheless.
     *
     * @return Returns the error text if the connection failed, but the server sent useful data nonetheless.
     * @throws NoSuchElementException        If error text is not present
     * @throws UnsupportedOperationException if generic type is a Void
     */
    String getErrorText();

    /**
     * Gets the connection URI.
     *
     * @return Returns the connection URI
     */
    URI getURI();

    /**
     * Gets the content type of the response.
     *
     * @return Content type of response
     */
    ContentType getContentType();

    /**
     * Checks if the status code is successful.
     *
     * @return Returns <b>true</b> if status code contains [200, 300) else <b>false</b>
     */
    boolean isSuccess();

    /**
     * Checks if the status code is not successful.
     *
     * @return Returns <b>true</b> if status code isn't contains [200, 300) else <b>false</b>
     */
    default boolean isNonSuccess() {
        return !isSuccess();
    }

    /**
     * If content is present, invokes the specified consumer with the content, otherwise does nothing.
     *
     * @param consumer block to be executed if has a content
     * @throws IllegalArgumentException if {@code consumer} is null
     */
    void ifHasContent(Consumer<? super T> consumer);

    /**
     * If status code is successful, invokes the specified consumer with the responseHandler and returns {@code OtherwiseSupport} with ignore else {@code OtherwiseSupport} with not ignore.
     *
     * @param consumer block to be executed if status code is success.
     * @return OtherwiseSupport instance to support action otherwise.
     * @see OtherwiseSupport#otherwise(Consumer)
     */
    OtherwiseSupport<T> ifSuccess(Consumer<ResponseHandler<T>> consumer);

    /**
     * If status code is not successful, invokes the specified consumer with the responseHandler.
     *
     * @param consumer block to be executed if status code is not success.
     */
    void ifNotSuccess(Consumer<ResponseHandler<T>> consumer);

    /**
     * Filters the response based on the given predicate.
     *
     * @param predicate Predicate to filter the response
     * @return FilterSupport instance to support further filtering
     */
    FilterSupport<T> filter(Predicate<ResponseHandler<T>> predicate);

    /**
     * Checks if the response contains a header with the given name.
     *
     * @param name Name of the header
     * @return {@code true} if the header is present, {@code false} otherwise
     */
    boolean containsHeader(String name);

    /**
     * Gets all headers with the given name.
     *
     * @param name Name of the header
     * @return Array of headers with the given name
     */
    Header[] getHeaders(String name);

    /**
     * Gets the first header with the given name.
     *
     * @param name Name of the header
     * @return The first header with the given name
     */
    Header getFirstHeader(String name);

    /**
     * Gets the last header with the given name.
     *
     * @param name Name of the header
     * @return The last header with the given name
     */
    Header getLastHeader(String name);

    /**
     * Gets all headers.
     *
     * @return Array of all headers
     */
    Header[] getHeaders();

    /**
     * Gets the value of the first header with the given name.
     *
     * @param name Name of the header
     * @return Value of the first header with the given name, or {@code null} if not present
     */
    @Beta
    default String getFirstHeaderValue(String name) {
        Header header = getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    /**
     * Gets the value of the last header with the given name.
     *
     * @param name Name of the header
     * @return Value of the last header with the given name, or {@code null} if not present
     */
    @Beta
    default String getLastHeaderValue(String name) {
        Header header = getLastHeader(name);
        return header == null ? null : header.getValue();
    }

    /**
     * Gets the duration from the call request to the parsed response.
     *
     * @return Duration from the call request to the parsed response
     */
    @Beta
    Duration getDuration();
}
