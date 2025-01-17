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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

class IOUtils {

    private IOUtils() {
        throw new AssertionError("No com.jsunsoft.http.IOUtils instances for you!");
    }

    static ByteArrayOutputStream toByteArrayOutputStream(final InputStream inputStream, long contentLength) throws IOException {

        if (inputStream == null || contentLength == 0) {
            return null;
        }

        int bufferInitialSize = resolveBufferInitialSize(contentLength);
        byte[] buffer = new byte[bufferInitialSize];

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(buffer.length);

        int length;

        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }

        return outputStream;

    }

    static byte[] toByteArray(final InputStream inputStream, long contentLength) throws IOException {

        ByteArrayOutputStream outputStream = toByteArrayOutputStream(inputStream, contentLength);

        return outputStream == null ? null : outputStream.toByteArray();
    }

    private static int resolveBufferInitialSize(long contentLength) throws IOException {
        int result;
        if (contentLength > Integer.MAX_VALUE) {
            throw new InvalidContentLengthException(contentLength, "Content length is large. Content length greater than Integer.MAX_VALUE");
        }
        int integerContentLength = (int) contentLength;

        if (integerContentLength > 0) {
            result = integerContentLength;
        } else {
            result = 1024;
        }

        return result;
    }
}
