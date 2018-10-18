/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 */
package org.aion.base.util;

import java.util.Arrays;
import java.util.Map;

/**
 * hashmap class for byte[] -> byte[] mapping.
 *
 * @author jin
 */
public class ByteArrayMap extends HashMap<byte[], byte[]> {

    private static final long serialVersionUID = -5203317888679449508L;
    transient ByteArrayNode[] table;

    public ByteArrayMap() {
        super();
    }

    public ByteArrayMap(int cap) {
        super(cap);
    }

    @Override
    protected int hash(Object key) {
        if (key == null || !(key instanceof byte[])) {
            return 0;
        }
        byte[] bs = (byte[]) key;
        return Arrays.hashCode(bs);
    }

    @Override
    protected boolean keyEquals(Object key, byte[] k) {
        if (!(key instanceof byte[])) {
            return false;
        }
        return Arrays.equals((byte[]) key, k);
    }

    @Override
    protected boolean valEquals(Object val, byte[] v) {
        if (!(val instanceof byte[])) {
            return false;
        }
        return Arrays.equals((byte[]) val, v);
    }

    @Override
    protected Node<byte[], byte[]> newNode(int hash, byte[] key, byte[] value,
        Node<byte[], byte[]> next) {
        return new ByteArrayNode(hash, key, value, next);

    }

    @Override
    protected Node<byte[], byte[]> replacementNode(Node<byte[], byte[]> p,
        Node<byte[], byte[]> next) {
        return new ByteArrayNode(p.hash, p.key, p.value, next);
    }

    @Override
    protected TreeNode<byte[], byte[]> newTreeNode(int hash, byte[] key, byte[] value,
        Node<byte[], byte[]> next) {
        return new ByteArrayTreeNode(hash, key, value, next);

    }

    @Override
    protected TreeNode<byte[], byte[]> replacementTreeNode(Node<byte[], byte[]> p,
        Node<byte[], byte[]> next) {
        return new ByteArrayTreeNode(p.hash, p.key, p.value, next);
    }

    static class ByteArrayNode extends Node<byte[], byte[]> {

        ByteArrayNode(int hash, byte[] key, byte[] value, Node<byte[], byte[]> next) {
            super(hash, key, value, next);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                if (Arrays.equals(key, (byte[]) e.getKey())
                    && Arrays.equals(value, (byte[]) e.getValue())) {
                    return true;
                }

            }
            return false;
        }

    }

    static class ByteArrayTreeNode extends TreeNode<byte[], byte[]> {

        ByteArrayTreeNode(int hash, byte[] key, byte[] value, Node<byte[], byte[]> next) {
            super(hash, key, value, next);
        }

        @Override
        protected boolean keyEquals(Object key, byte[] k) {
            if (!(key instanceof byte[])) {
                return false;
            }
            return Arrays.equals((byte[]) key, k);
        }

        @Override
        protected boolean valEquals(Object val, byte[] v) {
            if (!(val instanceof byte[])) {
                return false;
            }
            return Arrays.equals((byte[]) val, v);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Map.Entry) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                if (Arrays.equals(key, (byte[]) e.getKey())
                    && Arrays.equals(value, (byte[]) e.getValue())) {
                    return true;
                }

            }
            return false;
        }
    }
}

