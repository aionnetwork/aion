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

public class UtilTest {
    @Test
    public void testPrependZeros() throws Exception {
        byte[] src = {'t', 'e', 's', 't'};
        byte[] result = Util.prependZeros(3, src);
        Assert.assertArrayEquals(new byte[]{0, 0, 0, 't', 'e', 's', 't'}, result);
    }
}
