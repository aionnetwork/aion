// Wagner's algorithm for Generalized Birthday Paradox, a memory-hard proof-of-work
// Copyright (c) 2016 John Tromp
// Modified Ross Kitsis 2017-2018

#include "org_aion_equihash_Equihash.h"
#include "equi_miner.h"
#include <unistd.h>
#include "ctype.h"
#include <jni.h>
//#include "equi.h"

//Replace default blake2b algorithm with implementation in sodium
#include "sodium.h"

u32 HEADERLEN;
static const u32 NONCELEN = 32;

int hextobyte(const char * x) {
  u32 b = 0;
  for (int i = 0; i < 2; i++) {
    uchar c = tolower(x[i]);
    assert(isxdigit(c));
    b = (b << 4) | (c - (c >= '0' && c <= '9' ? '0' : ('a' - 10)));
  }
  return b;
}

JNIEXPORT jobjectArray JNICALL Java_org_aion_equihash_Equihash_solve(JNIEnv *env, jobject obj, jbyteArray nc, jbyteArray blockHeader){
  int nthreads = 1;
  jbyte* ncArray = env->GetByteArrayElements(nc, NULL);
  char* nonce = (char*) ncArray;
  int nonceLen = env->GetArrayLength(nc);

  bool showsol = false;

  jbyte* hd = env->GetByteArrayElements(blockHeader, NULL);
  HEADERLEN = env->GetArrayLength(blockHeader);
  const char *header = (char*) hd;

#ifndef XWITHASH
  if (sizeof(tree) > 4)
    printf("WARNING: please compile with -DXWITHASH to shrink tree!\n");
#endif
#ifdef ATOMIC
  if (nthreads==1)
    printf("WARNING: use of atomics hurts single threaded performance!\n");
#else
  assert(nthreads==1);
#endif

  //printf(") with %d %d-bit digits and %d threads\n", NDIGITS, DIGITBITS, nthreads);
  thread_ctx *threads = (thread_ctx *)calloc(nthreads, sizeof(thread_ctx));
  assert(threads);
  equi eq(nthreads, HEADERLEN, NONCELEN);
  u32 sumnsols = 0;

  unsigned char headernonce[HEADERLEN + NONCELEN];

  //Start b2b initialization
  crypto_generichash_blake2b_state state;

  //Add personalizations
  EquiInitializeState(state);

  //Build H(I | V ..

  //Add header to the array
  memcpy(headernonce, header, HEADERLEN);

  //391 represents the offset
  memcpy(headernonce + HEADERLEN, nonce, nonceLen);
  //Header + nonce = HEADERNONCELEN
  crypto_generichash_blake2b_update(&state, headernonce, HEADERNONCELEN);

  eq.setstate(&state);

    for (int t = 0; t < nthreads; t++) {
      threads[t].id = t;
      threads[t].eq = &eq;
      int err = pthread_create(&threads[t].thread, NULL, worker, (void *)&threads[t]);
      assert(err == 0);
    }
    for (int t = 0; t < nthreads; t++) {
      int err = pthread_join(threads[t].thread, NULL);
      assert(err == 0);
    }
    u32 nsols, maxsols = min(MAXSOLS, eq.nsols);
    for (nsols = 0; nsols < maxsols; nsols++) {
      if (showsol) {
        printf("\nSolution");
        for (u32 i = 0; i < PROOFSIZE; i++){
        	//Format into blocks of 4
			  if(i % 4 == 0 && i > 0){
				  printf("\n");
			  }
        	printf(" %jx", (uintmax_t)eq.sols[nsols][i]);
        }
      }
    }
    sumnsols += nsols;


  free(threads);
  
  // Get the int array class
  jclass cls = env->FindClass("[I");
  
  //Check that the class was found
  if(cls == NULL){
	  printf("------------Long class not found------------");
	  printf("----Unable to run native equihash solver----");
  }
  
  //Create int array (outer)
  jintArray iniVal = env->NewIntArray(maxsols);
  
  //Create the returnable jobjectArray with an initial value (filled with empty arrays)
  jobjectArray outer = env->NewObjectArray(maxsols,cls, iniVal);
  
  //Loop through the solutions add add them to the outer array
  for(uint nsols = 0; nsols < maxsols; nsols++){
	  //Create array for solution
	  jintArray inner = env->NewIntArray(PROOFSIZE);

	  //Copy solutions to a temporary int array to be passed to JNI for better compatibility
	  int toPass[PROOFSIZE];
	  for(uint i = 0; i < PROOFSIZE; i++){
		  toPass[i] = eq.sols[nsols][i];
	  }
	  // Copy solution to innter
	  env->SetIntArrayRegion(inner, 0, PROOFSIZE, toPass);

	  //Add inner to outer
	  env->SetObjectArrayElement(outer, nsols, inner);

	  //Delete reference to inner to reclaim memory
	  env->DeleteLocalRef(inner);
  }
  
  //Release byte array elements to ensure no memory leaks
  env->ReleaseByteArrayElements(blockHeader, hd, JNI_ABORT);
  env->ReleaseByteArrayElements(nc, ncArray, JNI_ABORT);
  
  return outer;
}
