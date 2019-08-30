package org.aion.base;

import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import org.aion.crypto.HashUtil;
import org.aion.rlp.RLP;

public final class ConstantUtil {
    private ConstantUtil(){}

    public static final byte[] EMPTY_TRIE_HASH = HashUtil.h256(RLP.encodeElement(EMPTY_BYTE_ARRAY));
}
