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

import org.junit.Assert;
import org.junit.Test;
import org.libsodium.jni.fixture.TestVectors;

import java.util.Arrays;

import static org.libsodium.jni.encoders.Encoder.HEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PointTest {

    @Test
    public void testMultipleIntegersWithBasePoint() throws Exception {
        Point point = new Point();
        String mult = point.mult(TestVectors.ALICE_PRIVATE_KEY, HEX).toString();
        Assert.assertEquals("Should return a serialized point", TestVectors.ALICE_PUBLIC_KEY, mult);
    }

    @Test
    public void testMultipleIntegersWithArbitraryPoints() throws Exception {
        Point point = new Point(TestVectors.BOB_PUBLIC_KEY, HEX);
        String mult = point.mult(TestVectors.ALICE_PRIVATE_KEY, HEX).toString();
        Assert.assertEquals("Should return a valid serialized point", TestVectors.ALICE_MULT_BOB, mult);
    }

    @Test
    public void testSerializeToBytes() throws Exception {
        Point point = new Point(TestVectors.BOB_PUBLIC_KEY, HEX);
        assertTrue("Should serialize to bytes", Arrays.equals(HEX.decode(TestVectors.BOB_PUBLIC_KEY), point.toBytes()));
    }

    @Test
    public void testSerializeToHex() throws Exception {
        Point point = new Point(TestVectors.BOB_PUBLIC_KEY, HEX);
        Assert.assertEquals("Should serialize to hex", TestVectors.BOB_PUBLIC_KEY, point.toString());
    }
}
