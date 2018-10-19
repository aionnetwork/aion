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
 * Contributors:
 *     Aion foundation.
 */

package org.libsodium.jni;

/**
 * Created with IntelliJ IDEA. User: josh Date: 7/14/13 Time: 7:31 PM To change this template use
 * File | Settings | File Templates.
 */
public abstract class SodiumConstants {
    public static final int SHA256BYTES = 32;
    public static final int SHA512BYTES = 64;
    public static final int BLAKE2B_OUTBYTES = 64;
    public static final int PUBLICKEY_BYTES = 32;
    public static final int SECRETKEY_BYTES = 32;
    public static final int NONCE_BYTES = 24;
    public static final int ZERO_BYTES = 32;
    public static final int BOXZERO_BYTES = 16;
    public static final int SCALAR_BYTES = 32;
    public static final int XSALSA20_POLY1305_SECRETBOX_KEYBYTES = 32;
    public static final int XSALSA20_POLY1305_SECRETBOX_NONCEBYTES = 24;
    public static final int SIGNATURE_BYTES = 64;
    public static final int AEAD_CHACHA20_POLY1305_KEYBYTES = 32;
    public static final int AEAD_CHACHA20_POLY1305_NPUBBYTES = 8;
    public static final int AEAD_CHACHA20_POLY1305_ABYTES = 8;
}
