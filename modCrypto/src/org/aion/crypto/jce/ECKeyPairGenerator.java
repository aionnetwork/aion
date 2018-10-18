/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */
package org.aion.crypto.jce;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;

public final class ECKeyPairGenerator {

    public static final String ALGORITHM = "EC";
    public static final String CURVE_NAME = "secp256k1";

    private static final String algorithmAssertionMsg =
        "Assumed JRE supports EC key pair generation";

    private static final String keySpecAssertionMsg =
        "Assumed correct key spec statically";

    private static final ECGenParameterSpec SECP256K1_CURVE
        = new ECGenParameterSpec(CURVE_NAME);

    private ECKeyPairGenerator() {
    }

    public static KeyPair generateKeyPair() {
        return Holder.INSTANCE.generateKeyPair();
    }

    public static KeyPairGenerator getInstance(final String provider, final SecureRandom random)
        throws NoSuchProviderException {
        try {
            final KeyPairGenerator gen = KeyPairGenerator.getInstance(ALGORITHM, provider);
            gen.initialize(SECP256K1_CURVE, random);
            return gen;
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(algorithmAssertionMsg, ex);
        } catch (InvalidAlgorithmParameterException ex) {
            throw new AssertionError(keySpecAssertionMsg, ex);
        }
    }

    public static KeyPairGenerator getInstance(final Provider provider, final SecureRandom random) {
        try {
            final KeyPairGenerator gen = KeyPairGenerator.getInstance(ALGORITHM, provider);
            gen.initialize(SECP256K1_CURVE, random);
            return gen;
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(algorithmAssertionMsg, ex);
        } catch (InvalidAlgorithmParameterException ex) {
            throw new AssertionError(keySpecAssertionMsg, ex);
        }
    }

    private static class Holder {

        private static final KeyPairGenerator INSTANCE;

        static {
            try {
                INSTANCE = KeyPairGenerator.getInstance(ALGORITHM);
                INSTANCE.initialize(SECP256K1_CURVE);
            } catch (NoSuchAlgorithmException ex) {
                throw new AssertionError(algorithmAssertionMsg, ex);
            } catch (InvalidAlgorithmParameterException ex) {
                throw new AssertionError(keySpecAssertionMsg, ex);
            }
        }
    }
}
