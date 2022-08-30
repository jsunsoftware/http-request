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


import com.jsunsoft.http.annotations.Beta;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.net.URI;

public interface Response extends CloseableHttpResponse {

    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * Note: method will throw any unchecked exception which will occurred in specified {@link ResponseBodyReader}.
     *
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     *
     * @return Response entity
     *
     * @throws ResponseBodyProcessingException when body processing failed.
     */
    <T> T readEntity(Class<T> responseType);

    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * Note: method will throw any unchecked exception which will occurred in specified {@link ResponseBodyReader}.
     *
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     *
     * @return Response entity
     *
     * @throws ResponseBodyProcessingException when body processing failed.
     */
    <T> T readEntity(TypeReference<T> responseType);

    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * Note: method will throw any unchecked exception which will occurred in specified {@link ResponseBodyReader}.
     *
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     *
     * @return Response entity
     *
     * @throws IOException                 If the stream could not be created or error occurs reading the input stream.
     * @throws ResponseBodyReaderException If Cannot deserialize content
     */
    @Beta
    <T> T readEntityChecked(Class<T> responseType) throws IOException;

    /**
     * Read the entity input stream as an instance of specified Java type using a {@link ResponseBodyReader}.
     * <p>
     * <p>
     * Note: method will throw any unchecked exception which will occurred in specified {@link ResponseBodyReader}.
     *
     * @param responseType Java type the response entity will be converted to.
     * @param <T>          response entity type.
     *
     * @return Response entity
     *
     * @throws IOException                 If the stream could not be created or error occurs reading the input stream.
     * @throws ResponseBodyReaderException If Cannot deserialize content
     */
    @Beta
    <T> T readEntityChecked(TypeReference<T> responseType) throws IOException;

    /**
     * @return the request URI
     */
    URI getURI();

    default boolean hasEntity() {
        return getEntity() != null;
    }

    /**
     * @return Content type of response
     */
    default ContentType getContentType() {
        return ContentType.get(getEntity());
    }

    /**
     * @return the status code.
     *
     * @see #getCode()
     * @deprecated use getCode instead
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
    default int getCode() {
        return getStatusLine().getStatusCode();
    }

    /**
     * @return Returns <b>true</b> if status code contains [200, 300) else <b>false</b>
     */
    default boolean isSuccess() {
        return HttpRequestUtils.isSuccess(getStatusCode());
    }

    /**
     * @return Returns <b>true</b> if status code isn't contains [200, 300) else <b>false</b>
     */
    default boolean isNonSuccess() {
        return !isSuccess();
    }

}
