package org.aion.mcf.trie.scan;

import java.util.HashMap;
import java.util.Map;
import org.aion.rlp.Value;
import org.aion.types.ByteArrayWrapper;

/** @author Alexandra Roatis */
public class CollectMappings implements ScanAction {

    Map<ByteArrayWrapper, byte[]> nodes = new HashMap<>();

    @Override
    public void doOnNode(byte[] hash, Value node) {
        nodes.put(new ByteArrayWrapper(hash), node.asBytes());
    }

    public Map<ByteArrayWrapper, byte[]> getNodes() {
        return nodes;
    }

    public int getSize() {
        return nodes.size();
    }
}
