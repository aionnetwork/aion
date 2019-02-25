package org.aion.mcf.trie;

import static org.aion.crypto.HashUtil.h256;

import java.util.Arrays;
import org.aion.base.db.IByteArrayKeyValueStore;

public class SecureTrie extends TrieImpl implements Trie {

    public SecureTrie(IByteArrayKeyValueStore db) {
        this(db, "");
    }

    public SecureTrie(IByteArrayKeyValueStore db, Object root) {
        super(db, root);
    }

    public SecureTrie(final Cache cache, Object root) {
        super(cache, root);
    }

    @Override
    public byte[] get(byte[] key) {
        return super.get(h256(key));
    }

    @Override
    public void update(byte[] key, byte[] value) {
        super.update(h256(key), value);
    }

    @Override
    public void delete(byte[] key) {
        super.delete(h256(key));
    }

    /**
     * Returns a copy of this trie.
     *
     * <p>The {@link Cache} object returned will be a copy of this object's cache, but the two will
     * share the same references to their data sources ({@link IByteArrayKeyValueStore} objects) and
     * each {@link Node} will retain the same references as the original node's {@link
     * org.aion.rlp.Value} objects.
     *
     * <p>The previous root and current root of this trie will return deep copies of these objects
     * only if they are of type {@code byte[]}. Otherwise, the original references will be passed
     * on. It is expected that they will indeed be byte arrays.
     *
     * @return A copy of this trie.
     */
    public SecureTrie copy() {
        synchronized (super.getCache()) {
            // Object originalPreviousRoot = super.getPrevRoot();
            Object originalRoot = super.getRoot();

            // Object previousRootCopy = null;
            Object rootCopy = null;

            // We should only be dealing in terms of byte arrays for the previous & current roots.
            // But if we do not get a byte[] here, then we cannot make a deep copy of the object.
            // if (originalPreviousRoot instanceof byte[]) {
            //     byte[] originalPreviousRootAsBytes = (byte[]) originalPreviousRoot;
            //     previousRootCopy =
            //            Arrays.copyOf(
            //                     originalPreviousRootAsBytes, originalPreviousRootAsBytes.length);
            // } else if (originalPreviousRoot != null) {
            //     previousRootCopy = originalPreviousRoot;
            // }

            if (originalRoot instanceof byte[]) {
                byte[] originalRootAsBytes = (byte[]) originalRoot;
                rootCopy = Arrays.copyOf(originalRootAsBytes, originalRootAsBytes.length);
            } else if (originalRoot != null) {
                rootCopy = originalRoot;
            }

            SecureTrie secureTrieCopy = new SecureTrie(super.getCache().copy(), rootCopy);
            // secureTrieCopy.setPrevRoot(previousRootCopy);
            secureTrieCopy.setPruningEnabled(super.isPruningEnabled());
            return secureTrieCopy;
        }
    }
}
