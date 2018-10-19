/**
 * Copyright 2013 Bruno Oliveira, and individual contributors
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.libsodium.jni.crypto;

import static org.libsodium.jni.NaCl.sodium;
import static org.libsodium.jni.SodiumConstants.SCALAR_BYTES;
import static org.libsodium.jni.crypto.Util.zeros;
import static org.libsodium.jni.encoders.Encoder.HEX;

import org.libsodium.jni.Sodium;
import org.libsodium.jni.encoders.Encoder;

public class Point {

    private static final String STANDARD_GROUP_ELEMENT =
            "0900000000000000000000000000000000000000000000000000000000000000";

    private final byte[] point;

    public Point() {
        this.point = HEX.decode(STANDARD_GROUP_ELEMENT);
    }

    public Point(byte[] point) {
        this.point = point;
    }

    public Point(String point, Encoder encoder) {
        this(encoder.decode(point));
    }

    public Point mult(byte[] n) {
        byte[] result = zeros(SCALAR_BYTES);
        sodium();
        Sodium.crypto_scalarmult_curve25519(result, n, point);
        return new Point(result);
    }

    public Point mult(String n, Encoder encoder) {
        return mult(encoder.decode(n));
    }

    @Override
    public String toString() {
        return HEX.encode(point);
    }

    public byte[] toBytes() {
        return point;
    }
}
