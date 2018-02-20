/*******************************************************************************
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
 ******************************************************************************/

#include "org_aion_crypto_hash_Blake2bNative.h"
#include "blake2.h"

#define HASH_LEN 32

JNIEXPORT jbyteArray JNICALL Java_org_aion_crypto_hash_Blake2bNative_blake256
  (JNIEnv *env, jclass cls, jbyteArray in)
{
    jbyte* inBuf = (*env)->GetByteArrayElements(env, in, NULL);
    jsize inLen = (*env)->GetArrayLength(env, in);

    uint8_t out[HASH_LEN];
    blake2b(out, HASH_LEN, inBuf, inLen, NULL, 0, false);

    jbyteArray ret = (*env)->NewByteArray(env, HASH_LEN);
    (*env)->SetByteArrayRegion(env, ret, 0, HASH_LEN, out);

    (*env)->ReleaseByteArrayElements(env, in, inBuf, JNI_ABORT);

    return ret;
}
