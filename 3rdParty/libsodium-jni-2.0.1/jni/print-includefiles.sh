#!/bin/bash -ev

src="../libsodium/src/libsodium/include/sodium"

ls "${src}" | while read line; do
	#%include "../libsodium/src/libsodium/include/sodium/export.h"
	#echo "%include \"${src}/${line}\""
	echo "%include \"sodium/${line}\""
done

