package org.aion.zero.impl.trie.scan;

import org.aion.rlp.Value;
import org.aion.util.conversions.Hex;
import org.aion.zero.impl.trie.scan.ScanAction;

public class TraceAllNodes implements ScanAction {

    StringBuilder output = new StringBuilder();

    @Override
    public void doOnNode(byte[] hash, Value node) {

        output.append(Hex.toHexString(hash)).append(" ==> ").append(node.toString()).append("\n");
    }

    public String getOutput() {
        return output.toString();
    }
}
