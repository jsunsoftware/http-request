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

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomArgsCheckTest {

    @Test
    void testCheckIsCorrectTypeForDeserializationWhenTypeIsInputStream() {
        assertThrows(IllegalArgumentException.class, () -> CustomArgsCheck.checkIsCorrectTypeForDeserialization(InputStream.class));
    }

    @Test
    void testCheckIsCorrectTypeForDeserializationWhenTypeIsInputStreamWithTypeReference() {
        Type type = new TypeReference<InputStream>() {
        }.getType();

        assertThrows(IllegalArgumentException.class, () -> CustomArgsCheck.checkIsCorrectTypeForDeserialization(type));
    }

    @Test
    void testCheckIsCorrectTypeForDeserializationWhenTypeIsNotInputStream() {
        CustomArgsCheck.checkIsCorrectTypeForDeserialization(Object.class);
    }

    @Test
    void testCheckIsCorrectTypeForDeserializationWhenTypeIsNotInputStreamWithTypeReference() {
        CustomArgsCheck.checkIsCorrectTypeForDeserialization(Object.class);
    }
}
