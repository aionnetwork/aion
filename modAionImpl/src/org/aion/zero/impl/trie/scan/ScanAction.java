package org.aion.zero.impl.trie.scan;

import org.aion.rlp.Value;

public interface ScanAction {

    void doOnNode(byte[] hash, Value node);
}
