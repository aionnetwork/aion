// Equihash solver
// Copyright (c) 2016-2016 John Tromp
// Modified Ross Kitsis 2017-2018

#ifdef __APPLE__
#include "osx_barrier.h"
#include <machine/endian.h>
#include <libkern/OSByteOrder.h>
#define htole32(x) OSSwapHostToLittleInt32(x)
#else
#include <endian.h>
#endif
#include <stdint.h> // for types uint32_t,uint64_t
#include <string.h> // for functions memset
#include <stdlib.h> // for function qsort
#include <stdio.h> // Print debug

// Replace the original blake algorithm with a more standard implementation in sodium
#include "sodium.h"


typedef uint32_t u32;
typedef unsigned char uchar;

// algorithm parameters, prefixed with W (for Wagner) to reduce include file conflicts

#ifndef WN
#define WN	210
#endif

#ifndef WK
#define WK	9
#endif

#define NDIGITS		(WK+1)
#define DIGITBITS	(WN/(NDIGITS))

static const u32 PROOFSIZE = 1<<WK;
static const u32 BASE = 1<<DIGITBITS;
static const u32 NHASHES = 2*BASE;
static const u32 HASHESPERBLAKE = 512/WN;
static const u32 HASHLEN = (WN+7)/8;
static const u32 HASHOUT = HASHESPERBLAKE*HASHLEN; //Add 7 to WN in order to round up to the nearest byte
static const int PERSONAL_BYTES = 16;
static u32 HEADERNONCELEN;

typedef u32 proof[PROOFSIZE];

void EquiInitializeState(crypto_generichash_blake2b_state& base_state){
	uint32_t le_N = htole32(WN);
	uint32_t le_K = htole32(WK);
	unsigned char personal[PERSONAL_BYTES] = {};
	memcpy(personal,"AION0PoW", 8);
	memcpy(personal+8,  &le_N, 4);
	memcpy(personal+12, &le_K, 4);

	crypto_generichash_blake2b_init_salt_personal(&base_state,
												NULL, 0, // No key.
												HASHOUT,
												NULL,    // No salt.
												personal);
}

enum verify_code { POW_OK, POW_HEADER_LENGTH, POW_DUPLICATE, POW_OUT_OF_ORDER, POW_NONZERO_XOR };
const char *errstr[] = { "OK", "wrong header length", "duplicate index", "indices out of order", "nonzero xor" };

int compu32(const void *pa, const void *pb) {
  u32 a = *(u32 *)pa, b = *(u32 *)pb;
  return a<b ? -1 : a==b ? 0 : +1;
}

bool duped(proof prf) {
  proof sortprf;
  memcpy(sortprf, prf, sizeof(proof));
  qsort(sortprf, PROOFSIZE, sizeof(u32), &compu32);
  for (u32 i=1; i<PROOFSIZE; i++)
    if (sortprf[i] <= sortprf[i-1])
      return true;
  return false;
}

/*
Verify functionality is included in the Java kernel.
The functions below are not actively used however are included for debugging purposes.
*/

void genhash_b(crypto_generichash_blake2b_state *ctx, u32 idx, uchar *hash) {
  crypto_generichash_blake2b_state state = *ctx;
  u32 leb = htole32(idx / HASHESPERBLAKE);

  crypto_generichash_blake2b_update(&state, (uchar *)&leb, sizeof(u32));
  uchar blakehash[HASHOUT];
  crypto_generichash_blake2b_final(&state, blakehash, HASHOUT);

  memcpy(hash, blakehash + (idx % HASHESPERBLAKE) * HASHLEN, HASHLEN);
}


int verifyrec_b(crypto_generichash_blake2b_state *ctx, u32 *indices, uchar *hash, int r) {
  if (r == 0) {
    genhash_b(ctx, *indices, hash);
    return POW_OK;
  }
  u32 *indices1 = indices + (1 << (r-1));
  if (*indices >= *indices1)
    return POW_OUT_OF_ORDER;
  uchar hash0[HASHLEN], hash1[HASHLEN];
  memset(hash0,0,HASHLEN);
  memset(hash1,0,HASHLEN);
  int vrf0 = verifyrec_b(ctx, indices,  hash0, r-1);
  if (vrf0 != POW_OK)
    return vrf0;
  int vrf1 = verifyrec_b(ctx, indices1, hash1, r-1);
  if (vrf1 != POW_OK)
    return vrf1;
  for (u32 i=0; i < HASHLEN; i++)
    hash[i] = hash0[i] ^ hash1[i];
  int i, b = r < WK ? r * DIGITBITS : WN;
  for (i = 0; i < b/8; i++)
    if (hash[i])
      return POW_NONZERO_XOR;
  if ((b%8) && hash[i] >> (8-(b%8)))
    return POW_NONZERO_XOR;

  //Check last 2 bits of last byte in step 9 independently of other steps
  //Byte 27 (210,9) is only partially filled; however it should be 0 as well due to zeroing of memory
  if(r == WK){
	  if(hash[HASHLEN - 1] >> 6)
        return POW_NONZERO_XOR;
  }

  return POW_OK;
}

int verify_b(u32 indices[PROOFSIZE], const unsigned char *headernonce, const u32 headerlen){
	 if (headerlen != HEADERNONCELEN)
	    return POW_HEADER_LENGTH;
	 if (duped(indices))
	    return POW_DUPLICATE;

	 crypto_generichash_blake2b_state state;

	 //Initialize personalizations
	 EquiInitializeState(state);

	 //Set I|V
	 crypto_generichash_blake2b_update(&state, headernonce, HEADERNONCELEN);

	 uchar hash[HASHLEN];
     memset(hash,0,HASHLEN);
	 return verifyrec_b(&state, indices, hash, WK);
}


