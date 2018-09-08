/* sodium.i */
/* Apache 2.0 License */
%module Sodium

%include "typemaps.i"
%include "stdint.i"
%include "arrays_java.i"
%include "carrays.i"
%include "various.i"
%include "java.swg"
%include "typemaps.i"

/* Basic mappings */
%apply int {unsigned long long};
%apply long[] {unsigned long long *};
%apply int {size_t};
%apply int {uint32_t};
%apply long {uint64_t};

/* unsigned char */
%typemap(jni) unsigned char *       "jbyteArray"
%typemap(jtype) unsigned char *     "byte[]"
%typemap(jstype) unsigned char *    "byte[]"
%typemap(in) unsigned char *{
    $1 = (unsigned char *) JCALL2(GetByteArrayElements, jenv, $input, 0);
}
%typemap(argout) unsigned char *{
    JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, 0);
}
%typemap(javain) unsigned char *"$javainput"
/* Prevent default freearg typemap from being used */
%typemap(freearg) unsigned char *""

/* uint8_t */
%typemap(jni) uint8_t *"jbyteArray"
%typemap(jtype) uint8_t *"byte[]"
%typemap(jstype) uint8_t *"byte[]"
%typemap(in) uint8_t *{
    $1 = (uint8_t *) JCALL2(GetByteArrayElements, jenv, $input, 0);
}
%typemap(argout) uint8_t *{
    JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, 0);
}
%typemap(javain) uint8_t *"$javainput"
%typemap(freearg) uint8_t *""

/* Strings */
%typemap(jni) char *"jbyteArray"
%typemap(jtype) char *"byte[]"
%typemap(jstype) char *"byte[]"
%typemap(in) char *{
    $1 = (char *) JCALL2(GetByteArrayElements, jenv, $input, 0);
}
%typemap(argout) char *{
    JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, 0);
}
%typemap(javain) char *"$javainput"
%typemap(freearg) char *""


/* char types */
%typemap(jni) char *BYTE "jbyteArray"
%typemap(jtype) char *BYTE "byte[]"
%typemap(jstype) char *BYTE "byte[]"
%typemap(in) char *BYTE {
    $1 = (char *) JCALL2(GetByteArrayElements, jenv, $input, 0);
}
%typemap(argout) char *BYTE {
    JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, 0);
}
%typemap(javain) char *BYTE "$javainput"
/* Prevent default freearg typemap from being used */
%typemap(freearg) char *BYTE ""

/* Fixed size strings/char arrays */
%typemap(jni) char [ANY]"jbyteArray"
%typemap(jtype) char [ANY]"byte[]"
%typemap(jstype) char [ANY]"byte[]"
%typemap(in) char [ANY]{
    $1 = (char *) JCALL2(GetByteArrayElements, jenv, $input, 0);
}
%typemap(argout) char [ANY]{
    JCALL3(ReleaseByteArrayElements, jenv, $input, (jbyte *) $1, 0);
}
%typemap(javain) char [ANY]"$javainput"
%typemap(freearg) char [ANY]""

%include "state-datatypes.i"

%include "sodium/export.h"
%include "sodium/core.h"
// %include "sodium/crypto_aead_aes256gcm.h"
%include "sodium/crypto_aead_chacha20poly1305.h"
%include "sodium/crypto_aead_xchacha20poly1305.h"
%include "sodium/crypto_auth.h"
//%include "sodium/crypto_auth_hmacsha256.h"
//%include "sodium/crypto_auth_hmacsha512256.h"
//%include "sodium/crypto_auth_hmacsha512.h"
%include "sodium/crypto_box_curve25519xchacha20poly1305.h"
%include "sodium/crypto_box_curve25519xsalsa20poly1305.h"
%include "sodium/crypto_box.h"
%include "sodium/crypto_core_ed25519.h"
%include "sodium/crypto_core_hchacha20.h"
%include "sodium/crypto_core_hsalsa20.h"
%include "sodium/crypto_core_salsa2012.h"
%include "sodium/crypto_core_salsa208.h"
%include "sodium/crypto_core_salsa20.h"
//%include "sodium/crypto_generichash_blake2b.h"
//%include "sodium/crypto_generichash.h"
%include "sodium/crypto_hash.h"
%include "sodium/crypto_hash_sha256.h"
%include "sodium/crypto_hash_sha512.h"
%include "sodium/crypto_kdf_blake2b.h"
%include "sodium/crypto_kdf.h"
%include "sodium/crypto_kx.h"
%include "sodium/crypto_onetimeauth.h"
%include "sodium/crypto_onetimeauth_poly1305.h"
%include "sodium/crypto_pwhash_argon2id.h"
%include "sodium/crypto_pwhash_argon2i.h"
%include "sodium/crypto_pwhash.h"
%include "sodium/crypto_pwhash_scryptsalsa208sha256.h"
%include "sodium/crypto_scalarmult_curve25519.h"
%include "sodium/crypto_scalarmult_ed25519.h"
%include "sodium/crypto_scalarmult.h"
%include "sodium/crypto_secretbox.h"
%include "sodium/crypto_secretbox_xchacha20poly1305.h"
%include "sodium/crypto_secretbox_xsalsa20poly1305.h"
%include "sodium/crypto_secretstream_xchacha20poly1305.h"
%include "sodium/crypto_shorthash.h"
%include "sodium/crypto_shorthash_siphash24.h"
%include "sodium/crypto_sign_ed25519.h"
%include "sodium/crypto_sign_edwards25519sha512batch.h"
%include "sodium/crypto_sign.h"
%include "sodium/crypto_stream_chacha20.h"
%include "sodium/crypto_stream.h"
%include "sodium/crypto_stream_salsa2012.h"
%include "sodium/crypto_stream_salsa208.h"
%include "sodium/crypto_stream_salsa20.h"
%include "sodium/crypto_stream_xchacha20.h"
%include "sodium/crypto_stream_xsalsa20.h"
%include "sodium/crypto_verify_16.h"
%include "sodium/crypto_verify_32.h"
%include "sodium/crypto_verify_64.h"
%include "sodium/export.h"
%include "sodium/private"
%include "sodium/randombytes.h"
%include "sodium/randombytes_nativeclient.h"
%include "sodium/randombytes_salsa20_random.h"
%include "sodium/randombytes_sysrandom.h"
%include "sodium/runtime.h"
%include "sodium/utils.h"
%include "sodium/version.h"
