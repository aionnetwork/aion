package org.aion.mcf.trie.scan;

import java.util.HashSet;
import java.util.Set;
import org.aion.rlp.Value;
import org.aion.vm.api.types.ByteArrayWrapper;

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
