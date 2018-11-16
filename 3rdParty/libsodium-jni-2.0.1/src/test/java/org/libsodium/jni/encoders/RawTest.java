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

package org.libsodium.jni.encoders;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static junit.framework.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RawTest {

    private Encoder encoder;

    @Before
    public void setUp() {
        encoder = new Raw();
    }

    @Test
    public void testEncode() throws Exception {
        String value = "hello";
        assertEquals(value, encoder.encode(value.getBytes()));
    }

    @Test
    public void testEncodeNullString() throws Exception {
        byte[] value = null;
        try {
            assertNull(encoder.encode(value));
        } catch (Exception e) {
            fail("Should not raise any exception");
        }
    }

    @Test
    public void testDecode() throws Exception {
        String value = "hello";
        assertTrue(Arrays.equals(encoder.decode(value), value.getBytes()));
    }

    @Test
    public void testDecodeNullString() throws Exception {
        String value = null;
        try {
            assertNull(encoder.decode(value));
        } catch (Exception e) {
            e.printStackTrace();
            fail("Should not raise any exception");
        }
    }
}
