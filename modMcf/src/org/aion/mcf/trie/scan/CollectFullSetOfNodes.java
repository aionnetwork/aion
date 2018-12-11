package org.aion.mcf.trie.scan;

import java.util.HashSet;
import java.util.Set;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.rlp.Value;

public class CollectFullSetOfNodes implements ScanAction {
    Set<ByteArrayWrapper> nodes = new HashSet<>();

    @Override
    public void doOnNode(byte[] hash, Value node) {
        nodes.add(new ByteArrayWrapper(hash));
    }

    public Set<ByteArrayWrapper> getCollectedHashes() {
        return nodes;
    }
}
