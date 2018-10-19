/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>The aion network project leverages useful source code from other open source projects. We
 * greatly appreciate the effort that was invested in these projects and we thank the individual
 * contributors for their work. For provenance information and contributors please see
 * <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * <p>Contributors to the aion source files in decreasing order of code volume: Aion foundation.
 * <ether.camp> team through the ethereumJ library. Ether.Camp Inc. (US) team through Ethereum
 * Harmony. John Tromp through the Equihash solver. Samuel Neves through the BLAKE2 implementation.
 * Zcash project team. Bitcoinj team.
 * ****************************************************************************
 */
package org.aion.mcf.trie;

import static org.aion.rlp.CompactEncoder.unpackToNibbles;

import java.util.List;
import org.aion.rlp.Value;

/*
 * @author Nick Savers
 * @since 20.05.2014
 */
public class TrieIterator {

    private TrieImpl trie;

    private List<byte[]> shas;
    private List<String> values;

    public TrieIterator(TrieImpl t) {
        this.trie = t;
    }

    // Some time in the near future this will need refactoring :-)
    // XXX Note to self, IsSlice == inline node. Str == keccak to node
    private void workNode(Value currentNode) {
        if (currentNode.length() == 2) {
            byte[] k = unpackToNibbles(currentNode.get(0).asBytes());

            if (currentNode.get(1).asString().isEmpty()) {
                this.workNode(currentNode.get(1));
            } else {
                if (k[k.length - 1] == 16) {
                    this.values.add(currentNode.get(1).asString());
                } else {
                    this.shas.add(currentNode.get(1).asBytes());
                    this.getNode(currentNode.get(1).asBytes());
                }
            }
        } else {
            for (int i = 0; i < currentNode.length(); i++) {
                if (i == 16 && currentNode.get(i).length() != 0) {
                    this.values.add(currentNode.get(i).asString());
                } else {
                    if (currentNode.get(i).asString().isEmpty()) {
                        this.workNode(currentNode.get(i));
                    } else {
                        String val = currentNode.get(i).asString();
                        if (!val.isEmpty()) {
                            this.shas.add(currentNode.get(1).asBytes());
                            this.getNode(val.getBytes());
                        }
                    }
                }
            }
        }
    }

    private void getNode(byte[] node) {
        Value currentNode = this.trie.getCache().get(node);
        this.workNode(currentNode);
    }

    private List<byte[]> collect() {
        if (this.trie.getRoot() == "") {
            return null;
        }
        this.getNode(new Value(this.trie.getRoot()).asBytes());
        return this.shas;
    }

    public int purge() {
        List<byte[]> shas = this.collect();

        for (byte[] sha : shas) {
            this.trie.getCache().delete(sha);
        }
        return this.values.size();
    }
}
