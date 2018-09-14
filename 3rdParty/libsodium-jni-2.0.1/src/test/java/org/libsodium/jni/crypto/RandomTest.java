/**
 * Copyright 2013 Bruno Oliveira, and individual contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.libsodium.jni.crypto;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RandomTest {

    @Test
    public void testProducesRandomBytes() throws Exception {
        final int size = 16;
        assertEquals("Invalid random bytes", size, new Random().randomBytes(size).length);
    }

    @Test
    public void testProducesDefaultRandomBytes() throws Exception {
        final int size = 32;
        assertEquals("Invalid random bytes", size, new Random().randomBytes().length);
    }

    @Test
    public void testProducesDifferentRandomBytes() throws Exception {
        final int size = 16;
        assertFalse("Should produce different random bytes", Arrays.equals(new Random().randomBytes(size), new Random().randomBytes(size)));
    }

    @Test
    public void testProducesDifferentDefaultRandomBytes() throws Exception {
        final int size = 32;
        assertFalse("Should produce different random bytes", Arrays.equals(new Random().randomBytes(), new Random().randomBytes(size)));
    }
}
