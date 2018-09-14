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

package org.libsodium.jni.fixture;

public class TestVectors {

    /**
     * SHA256 test vectors
     */

    public static final String SHA256_MESSAGE = "My Bonnie lies over the ocean, my Bonnie lies over the sea";
    public static final String SHA256_DIGEST = "d281d10296b7bde20df3f3f4a6d1bdb513f4aa4ccb0048c7b2f7f5786b4bcb77";
    public static final String SHA256_DIGEST_EMPTY_STRING = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /**
     * SHA512 test vectors
     */
    public static final String SHA512_MESSAGE = "My Bonnie lies over the ocean, Oh bring back my Bonnie to me";
    public static final String SHA512_DIGEST = "2823e0373001b5f3aa6db57d07bc588324917fc221dd27975613942d7f2e19bf4" +
            "44654c8b9f4f9cb908ef15f2304522e60e9ced3fdec66e34bc2afb52be6ad1c";
    public static final String SHA512_DIGEST_EMPTY_STRING = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921" +
            "d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";



    /**
     * Blake2 test vectors
     */
    public static final String Blake2_MESSAGE = "The quick brown fox jumps over the lazy dog";
    public static final String Blake2_DIGEST = "a8add4bdddfd93e4877d2746e62817b116364a1fa7bc148d95090bc7333b3673f82401" +
            "cf7aa2e4cb1ecd90296e3f14cb5413f8ed77be73045b13914cdcd6a918";
    public static final String Blake2_DIGEST_EMPTY_STRING = "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f" +
            "71f5419d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce";

    /**
     * Curve25519 test vectors
     */

    public static final String BOB_PRIVATE_KEY = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb";
    public static final String BOB_PUBLIC_KEY = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f";

    public static final String ALICE_PRIVATE_KEY = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a";
    public static final String ALICE_PUBLIC_KEY = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a";
    public static final String ALICE_MULT_BOB = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

    public static final String BOX_NONCE = "69696ee955b62b73cd62bda875fc73d68219e0036b7a0b37";
    public static final String BOX_MESSAGE = "be075fc53c81f2d5cf141316ebeb0c7b5228c52a4c62cbd44b66849b64244ffc" +
            "e5ecbaaf33bd751a1ac728d45e6c61296cdc3c01233561f41db66cce314adb31" +
            "0e3be8250c46f06dceea3a7fa1348057e2f6556ad6b1318a024a838f21af1fde" +
            "048977eb48f59ffd4924ca1c60902e52f0a089bc76897040e082f93776384864" +
            "5e0705";
    public static final String BOX_CIPHERTEXT = "f3ffc7703f9400e52a7dfb4b3d3305d98e993b9f48681273c29650ba32fc76ce" +
            "48332ea7164d96a4476fb8c531a1186ac0dfc17c98dce87b4da7f011ec48c972" +
            "71d2c20f9b928fe2270d6fb863d51738b48eeee314a7cc8ab932164548e526ae" +
            "90224368517acfeabd6bb3732bc0e9da99832b61ca01b6de56244a9e88d5f9b3" +
            "7973f622a43d14a6599b1f654cb45a74e355a5";

    public static final String SECRET_KEY = "1b27556473e985d462cd51197a9a46c76009549eac6474f206c4ee0844f68389";

    public static final String SIGN_PRIVATE = "b18e1d0045995ec3d010c387ccfeb984d783af8fbb0f40fa7db126d889f6dadd";
    public static final String SIGN_MESSAGE = "916c7d1d268fc0e77c1bef238432573c39be577bbea0998936add2b50a653171" +
            "ce18a542b0b7f96c1691a3be6031522894a8634183eda38798a0c5d5d79fbd01" +
            "dd04a8646d71873b77b221998a81922d8105f892316369d5224c9983372d2313" +
            "c6b1f4556ea26ba49d46e8b561e0fc76633ac9766e68e21fba7edca93c4c7460" +
            "376d7f3ac22ff372c18f613f2ae2e856af40";
    public static final String SIGN_SIGNATURE = "6bd710a368c1249923fc7a1610747403040f0cc30815a00f9ff548a896bbda0b" +
            "4eb2ca19ebcf917f0f34200a9edbad3901b64ab09cc5ef7b9bcc3c40c0ff7509";
    public static final String SIGN_PUBLIC = "77f48b59caeda77751ed138b0ec667ff50f8768c25d48309a8f386a2bad187fb";

    public static final String BOB_SEED = "87b0a3092d7c7d08ca8c0e83be0fe0c102ba7cc297f0ce0a2e2e3d2925a4dd3b";
    public static final String BOB_ENCRYPTION_PRIVATE_KEY = "16785df23e2f298c38143a47477013c88226f562ec5a8a6c564fee3862e92e09";
    public static final String BOB_ENCRYPTION_PUBLIC_KEY = "37e971b2eecd2438fc53292c4ae2a762cead9355616434e762e2e1ea64bd1d10";
    public static final String BOB_SIGNING_KEY = "87b0a3092d7c7d08ca8c0e83be0fe0c102ba7cc297f0ce0a2e2e3d2925a4dd3b3c0c551d3e50597116c9812290d436a616023d86bf6e4a2a8ff46fd86819d5f9";
    public static final String BOB_VERIFY_KEY = "3c0c551d3e50597116c9812290d436a616023d86bf6e4a2a8ff46fd86819d5f9";

/**
 *
 seed
 BASE64: h7CjCS18fQjKjA6Dvg_gwQK6fMKX8M4KLi49KSWk3Ts
 HEX: 87b0a3092d7c7d08ca8c0e83be0fe0c102ba7cc297f0ce0a2e2e3d2925a4dd3b

 PrivateKey encryptionKeyPair.secretKey
 BASE64: Fnhd8j4vKYw4FDpHR3ATyIIm9WLsWopsVk_uOGLpLgk
 HEX: 16785df23e2f298c38143a47477013c88226f562ec5a8a6c564fee3862e92e09

 PublicKey encryptionKeyPair.publicKey
 BASE64: N-lxsu7NJDj8UyksSuKnYs6tk1VhZDTnYuLh6mS9HRA
 HEX: 37e971b2eecd2438fc53292c4ae2a762cead9355616434e762e2e1ea64bd1d10

 SigningKey signKeyPair.secretKey
 BASE64: h7CjCS18fQjKjA6Dvg_gwQK6fMKX8M4KLi49KSWk3Ts8DFUdPlBZcRbJgSKQ1DamFgI9hr9uSiqP9G_YaBnV-Q
 HEX: 87b0a3092d7c7d08ca8c0e83be0fe0c102ba7cc297f0ce0a2e2e3d2925a4dd3b3c0c551d3e50597116c9812290d436a616023d86bf6e4a2a8ff46fd86819d5f9

 VerifyKey signKeyPair.publicKey
 BASE64: PAxVHT5QWXEWyYEikNQ2phYCPYa_bkoqj_Rv2GgZ1fk
 HEX: 3c0c551d3e50597116c9812290d436a616023d86bf6e4a2a8ff46fd86819d5f9
 */

}