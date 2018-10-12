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
#include "org_aion_crypto_hash_Blake2bNative.h"
#include "blake2.h"
#include <string.h> // for functions memset
#include <endian.h>
#include <stdint.h> // for types uint32_t,uint64_t


#define HASH_LEN 32
#define HASHESPERBLAKE 2
#define HASHOUT 54
#define N 210
#define K 9

typedef uint32_t u32;
typedef unsigned char uchar;

JNIEXPORT jbyteArray JNICALL Java_org_aion_crypto_hash_Blake2bNative_blake256
  (JNIEnv *env, jclass cls, jbyteArray in)
{
    jbyte* inBuf = (*env)->GetByteArrayElements(env, in, NULL);
    jsize inLen = (*env)->GetArrayLength(env, in);

    uint8_t out[HASH_LEN];
    //blake2b(out, HASH_LEN, inBuf, inLen, NULL, 0);

    blake2b(out, inBuf, NULL, HASH_LEN, inLen, 0);

    jbyteArray ret = (*env)->NewByteArray(env, HASH_LEN);
    (*env)->SetByteArrayRegion(env, ret, 0, HASH_LEN, out);

    (*env)->ReleaseByteArrayElements(env, in, inBuf, JNI_ABORT);

    return ret;
}

/*
 * Class:     org_aion_crypto_hash_Blake2bNative
 * Method:    genSolutionHash
 * Signature: ([B[B[I)[B
 */
JNIEXPORT jobjectArray JNICALL Java_org_aion_crypto_hash_Blake2bNative_genSolutionHash
  (JNIEnv *env, jclass cls, jbyteArray personalizationBytes, jbyteArray nonceBytes, jintArray indicesArray,
  jbyteArray headerBytes)
{
    // Get personalization bytes
    jbyte* personalization = (*env)->GetByteArrayElements(env, personalizationBytes, NULL);
    jsize personalizationLen = (*env)->GetArrayLength(env, personalizationBytes);

    // Get nonce
    jbyte* nonce = (*env)->GetByteArrayElements(env, nonceBytes, NULL);
    jsize nonceLen = (*env)->GetArrayLength(env, nonceBytes);

    // Get solution indices
    jint *indices = (*env)->GetIntArrayElements(env, indicesArray, NULL);
    jsize indicesSize = (*env)->GetArrayLength(env, indicesArray);

    // Get header bytes
    jbyte* header = (*env)->GetByteArrayElements(env, headerBytes, NULL);
    jsize headerLen = (*env)->GetArrayLength(env, headerBytes);

    // Create blake2b state
    blake2b_state ctx;

    blake2b_param P[1];
    P->digest_length = 54;
    P->key_length    = 0;
    P->fanout        = 1;
    P->depth         = 1;
    P->leaf_length   = 0;
    P->node_offset   = 0;
    P->node_depth    = 0;
    P->inner_length  = 0;
    memset(P->reserved, 0, sizeof(P->reserved));
    memset(P->salt,     0, sizeof(P->salt));
    memcpy(P->personal, personalization, personalizationLen);
    blake2b_init_param(&ctx, P);

    // Add header
    blake2b_update(&ctx, header, headerLen);

    // Add nonce
    blake2b_update(&ctx, nonce, nonceLen);

    // Create return array
    jclass byteClass = (*env)->FindClass(env, "[B");
    jbyteArray hashes = (*env)->NewByteArray(env, HASHOUT/HASHESPERBLAKE);
    jobjectArray outer = (*env)->NewObjectArray(env, indicesSize, byteClass, hashes);

    // Generate hashes
    int i;
    for(i = 0; i < indicesSize; i++){
        blake2b_state state = ctx;
//        memcpy(&state, &ctx, sizeof(blake2b_state));

        u32 leb = htole32(indices[i]/HASHESPERBLAKE);
        blake2b_update(&state, (uchar *)&leb, sizeof(u32));

        // Generate hashes and copy to inner object
        uchar blakehash[54];
        uchar indexHash[27];
        blake2b_final(&state, blakehash, HASHOUT);

        memcpy(indexHash, blakehash + (indices[i] % HASHESPERBLAKE) * (N+7)/8, (N+7)/8);

        jbyteArray hash = (*env)->NewByteArray(env, HASHOUT/HASHESPERBLAKE);
        (*env)->SetByteArrayRegion(env, hash, 0, HASHOUT/HASHESPERBLAKE, indexHash);

        //Add inner to outer
        (*env)->SetObjectArrayElement(env, outer, i, hash);

        //Release hash to reclaim memory
        (*env)->DeleteLocalRef(env, hash);
    }

    // Release memory
    (*env)->ReleaseByteArrayElements(env,personalizationBytes,personalization, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, nonceBytes, nonce, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, headerBytes, header, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, indicesArray, indices, JNI_ABORT);

    return outer;
}