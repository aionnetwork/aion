package org.aion.zero.impl.trie.scan;

import org.aion.rlp.Value;
import org.aion.zero.impl.trie.scan.ScanAction;

/** @author Alexandra Roatis */
public class CountNodes implements ScanAction {
    private int count = 0;

    @Override
    public void doOnNode(byte[] hash, Value node) {
        count++;
    }

    public int getCount() {
        return count;
    }
}
