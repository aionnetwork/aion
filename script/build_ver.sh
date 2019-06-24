#!/bin/bash
string_ver=$(cat modAionImpl/src/org/aion/zero/impl/Version.java | sed -n '4p')
prefix='public static final String KERNEL_VERSION = "'
postfix='";'
tmp="$(<<<"$string_ver" tr -d "$prefix")"
build_ver="$(<<<"$tmp" tr -d "$postfix")"
echo "$build_ver"
