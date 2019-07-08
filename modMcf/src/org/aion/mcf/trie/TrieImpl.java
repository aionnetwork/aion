package org.aion.mcf.trie;

import static java.util.Arrays.copyOfRange;
import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.rlp.CompactEncoder.binToNibbles;
import static org.aion.rlp.CompactEncoder.hasTerminator;
import static org.aion.rlp.CompactEncoder.packNibbles;
import static org.aion.rlp.CompactEncoder.unpackToNibbles;
import static org.aion.rlp.RLP.calcElementPrefixSize;
import static org.aion.util.bytes.ByteUtil.matchingNibbleLength;
import static org.aion.util.types.ByteArrayWrapper.wrap;
import static org.spongycastle.util.Arrays.concatenate;

import com.google.common.annotations.VisibleForTesting;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.mcf.trie.scan.CollectFullSetOfNodes;
import org.aion.mcf.trie.scan.CollectMappings;
import org.aion.mcf.trie.scan.CountNodes;
import org.aion.mcf.trie.scan.ExtractToDatabase;
import org.aion.mcf.trie.scan.ScanAction;
import org.aion.mcf.trie.scan.TraceAllNodes;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.rlp.Value;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;

/**
 * The modified Merkle Patricia tree (trie) provides a persistent data structure to map between
 * arbitrary-length binary data (byte arrays). It is defined in terms of a mutable data structure to
 * map between 256-bit binary fragments and arbitrary-length binary data, typically implemented as a
 * database. The core of the trie, and its sole requirement in terms of the protocol specification
 * is to provide a single value that identifies a given set of key-value pairs, which may either a
 * 32 byte sequence or the empty byte sequence. It is left as an implementation consideration to
 * store and maintain the structure of the trie in a manner the allows effective and efficient
 * realisation of the protocol.
 *
 * <p>The trie implements a caching mechanism and will use cached values if they are present. If a
 * node is not present in the cache it will try to fetch it from the database and store the cached
 * value.
 *
 * <p><b>Note:</b> the data isn't persisted unless `sync` is explicitly called.
 *
 * <p>This Trie implementation supports node pruning (i.e. obsolete nodes are marked for removal in
 * the Cache and actually removed from the underlying storage on [sync] call), but the algorithm is
 * not suitable for the most general case. In general case a trie node might be referenced from
 * several parent nodes and for correct pruning the reference counting algorithm needs to be
 * implemented. As soon as the real life tree keys are hashes it is very unlikely the case so the
 * pruning algorithm is simplified in this implementation.
 *
 * @author Nick Savers
 * @since 20.05.2014
 */
public class TrieImpl implements Trie {
    private static final byte PAIR_SIZE = 2;
    private static final byte LIST_SIZE = 17;
    private static final int MAX_SIZE = 20;

    // private Object prevRoot;
    private Object root;
    private Cache cache;

    private boolean pruningEnabled;

    public TrieImpl(ByteArrayKeyValueStore db) {
        this(db, "");
    }

    public TrieImpl(ByteArrayKeyValueStore db, Object root) {
        this(new Cache(db), root);
    }

    public TrieImpl(final Cache cache, Object root) {
        this.cache = cache;
        this.root = root;
        // this.prevRoot = root;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Cache getCache() {
        return this.cache;
    }

    //    @Deprecated
    //    public Object getPrevRoot() {
    //        return prevRoot;
    //    }
    //
    //    public void setPrevRoot(Object previousRoot) {
    //        prevRoot = previousRoot;
    //    }

    public Object getRoot() {
        return root;
    }

    /** for testing TrieTest.testRollbackToRootScenarios */
    public void setRoot(Object root) {
        this.root = root;
    }

    @Override
    public void setRoot(byte[] root) {
        this.root = root;
    }

    private void deserializeRoot(byte[] data) {
        synchronized (cache) {
            try {
                ByteArrayInputStream b = new ByteArrayInputStream(data);
                ObjectInputStream o = new ObjectInputStream(b);
                root = o.readObject();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isPruningEnabled() {
        return pruningEnabled;
    }

    public void setPruningEnabled(boolean pruningIsEnabled) {
        pruningEnabled = pruningIsEnabled;
    }

    public TrieImpl withPruningEnabled(boolean pruningEnabled) {
        this.pruningEnabled = pruningEnabled;
        return this;
    }

    /** Retrieve a value from a key as String. */
    public byte[] get(String key) {
        return this.get(key.getBytes());
    }

    @Override
    public byte[] get(byte[] key) {
        synchronized (cache) {
            byte[] k = binToNibbles(key);
            Value c = new Value(this.get(this.root, k));

            return c.asBytes();
        }
    }

    /** Insert key/value pair into trie. */
    @VisibleForTesting
    void update(String key, String value) {
        this.update(key.getBytes(), value.getBytes());
    }

    @Override
    public void update(byte[] key, byte[] value) {
        if (key == null) {
            throw new NullPointerException("The key should not be null.");
        }
        // value checks are added to enforce separation of insert and delete
        if (value == null) {
            throw new NullPointerException("The value should not be null.");
        }
        // note: this can be removed if a VM will want to allow empty additions
        if (value.length == 0) {
            throw new IllegalArgumentException("The value should not be empty.");
        }

        byte[] k = binToNibbles(key);

        synchronized (cache) {
            if (isEmptyNode(root)) {
                cache.markRemoved(getRootHash());
            }

            this.root = this.insert(this.root, k, value);
        }
    }

    @Override
    public synchronized boolean isValidRoot(byte[] root) {
        return !(this.getNode(root) == null);
    }

    /** Delete a key/value pair from the trie. */
    @VisibleForTesting
    void delete(String key) {
        this.delete(key.getBytes());
    }

    @Override
    public void delete(byte[] key) {
        synchronized (cache) {
            byte[] k = binToNibbles(key);

            if (isEmptyNode(root)) {
                cache.markRemoved(getRootHash());
            }

            this.root = this.delete(this.root, k);
        }
    }

    @Override
    public byte[] getRootHash() {
        synchronized (cache) {
            if (root == null
                    || (root instanceof byte[] && ((byte[]) root).length == 0)
                    || (root instanceof String && "".equals(root))) {
                return EMPTY_TRIE_HASH;
            } else if (root instanceof byte[]) {
                return (byte[]) this.getRoot();
            } else {
                Value rootValue = new Value(this.getRoot());
                return HashUtil.h256(rootValue.encode());
            }
        }
    }

    private Object get(Object node, byte[] key) {
        synchronized (cache) {
            int keypos = 0;
            while (key.length - keypos != 0 && !isEmptyNode(node)) {
                Value currentNode = this.getNode(node);
                if (currentNode == null) {
                    return null;
                }

                if (currentNode.length() == PAIR_SIZE) {
                    // Decode the key
                    byte[] k = unpackToNibbles(currentNode.get(0).asBytes());
                    Object v = currentNode.get(1).asObj();

                    if (key.length - keypos >= k.length
                            && Arrays.equals(k, copyOfRange(key, keypos, k.length + keypos))) {
                        node = v;
                        keypos += k.length;
                    } else {
                        return "";
                    }
                } else {
                    node = currentNode.get(key[keypos]).asObj();
                    keypos++;
                }
            }
            return node;
        }
    }

    /**
     * Update or add the item inside a node.
     *
     * @return the updated node with rlp encoded
     */
    private Object insert(Object node, byte[] key, Object value) {

        if (key.length == 0) {
            return value;
        }

        if (isEmptyNode(node)) {
            Object[] newNode = new Object[] {packNibbles(key), value};
            return this.putToCache(newNode);
        }

        Value currentNode = this.getNode(node);

        if (currentNode == null) {
            throw new RuntimeException("Invalid Trie state, missing node " + new Value(node));
        }

        // Check for "special" 2 slice type node
        if (currentNode.length() == PAIR_SIZE) {
            // Decode the key
            byte[] k = unpackToNibbles(currentNode.get(0).asBytes());
            Object v = currentNode.get(1).asObj();

            // Matching key pair (ie. there's already an object with this key)
            if (Arrays.equals(k, key)) {
                Object[] newNode = new Object[] {packNibbles(key), value};
                return this.putToCache(newNode);
            }

            Object newHash;
            int matchingLength = matchingNibbleLength(key, k);
            if (matchingLength == k.length) {
                // Insert the hash, creating a new node
                byte[] remainingKeypart = copyOfRange(key, matchingLength, key.length);
                newHash = this.insert(v, remainingKeypart, value);

            } else {

                // Expand the 2 length slice to a 17 length slice
                // Create two nodes to putToCache into the new 17 length node
                Object oldNode = this.insert("", copyOfRange(k, matchingLength + 1, k.length), v);
                Object newNode =
                        this.insert("", copyOfRange(key, matchingLength + 1, key.length), value);

                // Create an expanded slice
                Object[] scaledSlice = emptyStringSlice(17);

                // Set the copied and new node
                scaledSlice[k[matchingLength]] = oldNode;
                scaledSlice[key[matchingLength]] = newNode;
                newHash = this.putToCache(scaledSlice);
            }

            markRemoved(HashUtil.h256(currentNode.encode()));

            if (matchingLength == 0) {
                // End of the chain, return
                return newHash;
            } else {
                Object[] newNode =
                        new Object[] {packNibbles(copyOfRange(key, 0, matchingLength)), newHash};
                return this.putToCache(newNode);
            }
        } else {

            // Copy the current node over to the new node
            Object[] newNode = copyNode(currentNode);

            // Replace the first nibble in the key
            newNode[key[0]] =
                    this.insert(
                            currentNode.get(key[0]).asObj(),
                            copyOfRange(key, 1, key.length),
                            value);

            if (!Arrays.equals(
                    HashUtil.h256(getNode(newNode).encode()),
                    HashUtil.h256(currentNode.encode()))) {
                markRemoved(HashUtil.h256(currentNode.encode()));
                if (!isEmptyNode(currentNode.get(key[0]))) {
                    markRemoved(currentNode.get(key[0]).asBytes());
                }
            }

            return this.putToCache(newNode);
        }
    }

    private Object delete(Object node, byte[] key) {

        if (key.length == 0 || isEmptyNode(node)) {
            return "";
        }

        // New node
        Value currentNode = this.getNode(node);
        if (currentNode == null) {
            throw new RuntimeException("Invalid Trie state, missing node " + new Value(node));
        }

        // Check for "special" 2 slice type node
        if (currentNode.length() == PAIR_SIZE) {
            // Decode the key
            byte[] k = unpackToNibbles(currentNode.get(0).asBytes());
            Object v = currentNode.get(1).asObj();

            // Matching key pair (ie. there's already an object with this key)
            if (Arrays.equals(k, key)) {
                return "";
            } else if (Arrays.equals(copyOfRange(key, 0, k.length), k)) {
                Object hash = this.delete(v, copyOfRange(key, k.length, key.length));
                Value child = this.getNode(hash);

                Object newNode;
                if (child.length() == PAIR_SIZE) {
                    byte[] newKey = concatenate(k, unpackToNibbles(child.get(0).asBytes()));
                    newNode = new Object[] {packNibbles(newKey), child.get(1).asObj()};
                } else {
                    newNode = new Object[] {currentNode.get(0), hash};
                }
                markRemoved(HashUtil.h256(currentNode.encode()));
                return this.putToCache(newNode);
            } else {
                return node;
            }
        } else {
            // Copy the current node over to a new node
            Object[] itemList = copyNode(currentNode);

            // Replace the first nibble in the key
            itemList[key[0]] = this.delete(itemList[key[0]], copyOfRange(key, 1, key.length));

            byte amount = -1;
            for (byte i = 0; i < LIST_SIZE; i++) {
                if (itemList[i] != "") {
                    if (amount == -1) {
                        amount = i;
                    } else {
                        amount = -2;
                    }
                }
            }

            Object[] newNode = null;
            if (amount == 16) {
                newNode = new Object[] {packNibbles(new byte[] {16}), itemList[amount]};
            } else if (amount >= 0) {
                Value child = this.getNode(itemList[amount]);
                if (child.length() == PAIR_SIZE) {
                    key = concatenate(new byte[] {amount}, unpackToNibbles(child.get(0).asBytes()));
                    newNode = new Object[] {packNibbles(key), child.get(1).asObj()};
                } else if (child.length() == LIST_SIZE) {
                    newNode = new Object[] {packNibbles(new byte[] {amount}), itemList[amount]};
                }
            } else {
                newNode = itemList;
            }

            if (!Arrays.equals(
                    HashUtil.h256(getNode(newNode).encode()),
                    HashUtil.h256(currentNode.encode()))) {
                markRemoved(HashUtil.h256(currentNode.encode()));
            }

            return this.putToCache(newNode);
        }
    }

    private void markRemoved(byte[] hash) {
        if (pruningEnabled) {
            cache.markRemoved(hash);
        }
    }

    /**
     * Helper method to retrieve the actual node. If the node is not a list and length is > 32 bytes
     * get the actual node from the db.
     */
    private Value getNode(Object node) {

        Value val = new Value(node);

        // in that case we got a node
        // so no need to encode it
        if (!val.isBytes()) {
            return val;
        }

        byte[] keyBytes = val.asBytes();
        if (keyBytes.length == 0) {
            return val;
        } else if (keyBytes.length < 32) {
            return new Value(keyBytes);
        }
        return this.cache.get(keyBytes);
    }

    private Object putToCache(Object node) {
        return this.cache.put(node);
    }

    private static boolean isEmptyNode(Object node) {
        Value n = new Value(node);
        return (node == null
                || (n.isString() && (n.asString().isEmpty() || n.get(0).isNull()))
                || n.length() == 0);
    }

    private static Object[] copyNode(Value currentNode) {
        Object[] itemList = emptyStringSlice(LIST_SIZE);
        for (int i = 0; i < LIST_SIZE; i++) {
            Object cpy = currentNode.get(i).asObj();
            if (cpy != null) {
                itemList[i] = cpy;
            }
        }
        return itemList;
    }

    // Simple compare function which compares two tries based on their stateRoot
    @Override
    public boolean equals(Object trie) {
        return this == trie
                || trie instanceof Trie
                        && Arrays.equals(this.getRootHash(), ((Trie) trie).getRootHash());
    }

    @Override
    public void sync() {
        synchronized (cache) {
            boolean flushCache = !cache.isDirty() || cache.getSize() > MAX_SIZE;
            sync(flushCache);
        }
    }

    @Override
    public void sync(boolean flushCache) {
        synchronized (cache) {
            this.cache.commit(flushCache);
            // this.prevRoot = this.root;
        }
    }

    // never used
    //    @Override
    //    public void undo() {
    //        synchronized (cache) {
    //            this.cache.undo();
    //            this.root = this.prevRoot;
    //        }
    //    }

    // Returns a copy of this trie
    public TrieImpl copy() {
        synchronized (cache) {
            TrieImpl trie = new TrieImpl(this.cache.getDb(), this.root);
            for (ByteArrayWrapper key : this.cache.getNodes().keySet()) {
                Node node = this.cache.getNodes().get(key);
                trie.cache.getNodes().put(key, node.copy());
            }
            return trie;
        }
    }

    /** ****************************** Utility functions * ***************************** */
    // Created an array of empty elements of required length
    private static Object[] emptyStringSlice(int l) {
        Object[] slice = new Object[l];
        for (int i = 0; i < l; i++) {
            slice[i] = "";
        }
        return slice;
    }

    // not used
    //    /**
    //     * Insert/delete operations on a Trie structure leaves the old nodes in cache, this method
    //     * scans the cache and removes them. The method is not thread safe, the tree should not be
    //     * modified during the cleaning process.
    //     */
    //    public void cleanCache() {
    //        synchronized (cache) {
    //            CollectFullSetOfNodes collectAction = new CollectFullSetOfNodes();
    //
    //            this.scanTree(this.getRootHash(), collectAction);
    //
    //            Set<ByteArrayWrapper> hashSet = collectAction.getCollectedHashes();
    //            Map<ByteArrayWrapper, Node> nodes = this.getCache().getNodes();
    //            Set<ByteArrayWrapper> toRemoveSet = new HashSet<>();
    //
    //            for (ByteArrayWrapper key : nodes.keySet()) {
    //                if (!hashSet.contains(key)) {
    //                    toRemoveSet.add(key);
    //                }
    //            }
    //
    //            for (ByteArrayWrapper key : toRemoveSet) {
    //                this.getCache().delete(key.copyOfData());
    //                // if (LOG.isTraceEnabled()) {
    //                // LOG.trace("Garbage collected node: [{}]",
    //                // Hex.toHexString(key.copyOfData()));
    //                // }
    //            }
    //        }
    //    }

    private void scanTree(byte[] hash, ScanAction scanAction) {
        synchronized (cache) {
            Value node = this.getCache().get(hash);
            if (node == null) {
                throw new RuntimeException("Not found: " + Hex.toHexString(hash));
            }

            if (node.isList()) {
                List<Object> siblings = node.asList();
                if (siblings.size() == PAIR_SIZE) {
                    Value val = new Value(siblings.get(1));
                    if (val.isHashCode() && !hasTerminator((byte[]) siblings.get(0))) {
                        scanTree(val.asBytes(), scanAction);
                    }
                } else {
                    for (int j = 0; j < LIST_SIZE; ++j) {
                        Value val = new Value(siblings.get(j));
                        if (val.isHashCode()) {
                            scanTree(val.asBytes(), scanAction);
                        }
                    }
                }
                scanAction.doOnNode(hash, node);
            }
        }
    }

    private void scanTreeLoop(byte[] hash, ScanAction scanAction) {

        ArrayList<byte[]> hashes = new ArrayList<>();
        hashes.add(hash);

        while (!hashes.isEmpty()) {
            synchronized (cache) {
                byte[] myHash = hashes.remove(0);
                Value node = this.getCache().get(myHash);
                if (node == null) {
                    throw new RuntimeException("Not found: " + Hex.toHexString(myHash));
                }

                if (node.isList()) {
                    List<Object> siblings = node.asList();
                    if (siblings.size() == PAIR_SIZE) {
                        Value val = new Value(siblings.get(1));
                        if (val.isHashCode() && !hasTerminator((byte[]) siblings.get(0))) {
                            // scanTree(val.asBytes(), scanAction);
                            hashes.add(val.asBytes());
                        }
                    } else {
                        for (int j = 0; j < LIST_SIZE; ++j) {
                            Value val = new Value(siblings.get(j));
                            if (val.isHashCode()) {
                                // scanTree(val.asBytes(), scanAction);
                                hashes.add(val.asBytes());
                            }
                        }
                    }
                    scanAction.doOnNode(myHash, node);
                }
            }
        }
    }

    /**
     * Scans the trie similar to {@link #scanTreeLoop(byte[], ScanAction)}, but stops once a state
     * is found in the given database.
     *
     * @param hash state root
     * @param scanAction action to perform on each node
     * @param db database containing keys that need not be explored
     */
    private void scanTreeDiffLoop(
            byte[] hash, ScanAction scanAction, ByteArrayKeyValueDatabase db) {

        ArrayList<byte[]> hashes = new ArrayList<>();
        hashes.add(hash);

        while (!hashes.isEmpty()) {
            synchronized (cache) {
                byte[] myHash = hashes.remove(0);
                Value node = this.getCache().get(myHash);
                if (node == null) {
                    System.out.println("Skipped key. Not found: " + Hex.toHexString(myHash));
                } else {
                    if (node.isList()) {
                        List<Object> siblings = node.asList();
                        if (siblings.size() == PAIR_SIZE) {
                            Value val = new Value(siblings.get(1));
                            if (val.isHashCode() && !hasTerminator((byte[]) siblings.get(0))) {
                                // scanTree(val.asBytes(), scanAction);
                                byte[] valBytes = val.asBytes();
                                if (!db.get(valBytes).isPresent()) {
                                    hashes.add(valBytes);
                                }
                            }
                        } else {
                            for (int j = 0; j < LIST_SIZE; ++j) {
                                Value val = new Value(siblings.get(j));
                                if (val.isHashCode()) {
                                    // scanTree(val.asBytes(), scanAction);
                                    byte[] valBytes = val.asBytes();
                                    if (!db.get(valBytes).isPresent()) {
                                        hashes.add(valBytes);
                                    }
                                }
                            }
                        }
                        scanAction.doOnNode(myHash, node);
                    }
                }
            }
        }
    }

    public void deserialize(byte[] data) {
        synchronized (cache) {
            RLPList rlpList = (RLPList) RLP.decode2(data).get(0);

            RLPItem keysElement = (RLPItem) rlpList.get(0);
            RLPList valsList = (RLPList) rlpList.get(1);
            RLPItem root = (RLPItem) rlpList.get(2);

            for (int i = 0; i < valsList.size(); ++i) {

                byte[] val = valsList.get(i).getRLPData();
                byte[] key = new byte[32];

                Value value = Value.fromRlpEncoded(val);
                System.arraycopy(keysElement.getRLPData(), i * 32, key, 0, 32);
                cache.getNodes().put(wrap(key), new Node(value));
            }

            this.deserializeRoot(root.getRLPData());
        }
    }

    public byte[] serialize() {

        synchronized (cache) {
            Map<ByteArrayWrapper, Node> map = getCache().getNodes();

            int keysTotalSize = 0;
            int valsTotalSize = 0;

            Set<ByteArrayWrapper> keys = map.keySet();
            for (ByteArrayWrapper key : keys) {
                Node node = map.get(key);
                if (node == null) {
                    continue;
                }

                byte[] keyBytes = key.getData();
                keysTotalSize += keyBytes.length;

                byte[] valBytes = node.getValue().getData();
                valsTotalSize += valBytes.length + calcElementPrefixSize(valBytes);
            }

            byte[] root = null;
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                ObjectOutputStream o = new ObjectOutputStream(b);
                o.writeObject(this.getRoot());
                root = b.toByteArray();
                root = RLP.encodeElement(root);
            } catch (IOException e) {
                e.printStackTrace();
            }

            byte[] keysHeader = RLP.encodeLongElementHeader(keysTotalSize);
            byte[] valsHeader = RLP.encodeListHeader(valsTotalSize);
            byte[] listHeader =
                    RLP.encodeListHeader(
                            keysTotalSize
                                    + keysHeader.length
                                    + valsTotalSize
                                    + valsHeader.length
                                    + root.length);

            byte[] rlpData =
                    new byte
                            [keysTotalSize
                                    + keysHeader.length
                                    + valsTotalSize
                                    + valsHeader.length
                                    + listHeader.length
                                    + root.length];

            // copy headers:
            // [ rlp_list_header, rlp_keys_header, rlp_keys, rlp_vals_header,
            // rlp_val]
            System.arraycopy(listHeader, 0, rlpData, 0, listHeader.length);
            System.arraycopy(keysHeader, 0, rlpData, listHeader.length, keysHeader.length);
            System.arraycopy(
                    valsHeader,
                    0,
                    rlpData,
                    (listHeader.length + keysHeader.length + keysTotalSize),
                    valsHeader.length);
            System.arraycopy(
                    root,
                    0,
                    rlpData,
                    (listHeader.length
                            + keysHeader.length
                            + keysTotalSize
                            + valsTotalSize
                            + valsHeader.length),
                    root.length);

            int k_1 = 0;
            int k_2 = 0;
            for (ByteArrayWrapper key : keys) {
                Node node = map.get(key);
                if (node == null) {
                    continue;
                }

                System.arraycopy(
                        key.getData(),
                        0,
                        rlpData,
                        (listHeader.length + keysHeader.length + k_1),
                        key.getData().length);

                k_1 += key.getData().length;

                byte[] valBytes = RLP.encodeElement(node.getValue().getData());

                System.arraycopy(
                        valBytes,
                        0,
                        rlpData,
                        listHeader.length
                                + keysHeader.length
                                + keysTotalSize
                                + valsHeader.length
                                + k_2,
                        valBytes.length);
                k_2 += valBytes.length;
            }

            return rlpData;
        }
    }

    public String getTrieDump() {

        synchronized (cache) {
            TraceAllNodes traceAction = new TraceAllNodes();
            Value value = new Value(root);
            if (value.isHashCode()) {
                this.scanTree(this.getRootHash(), traceAction);
            } else {
                traceAction.doOnNode(this.getRootHash(), value);
            }

            final String root;
            if (this.getRoot() instanceof Value) {
                root = "root: " + Hex.toHexString(getRootHash()) + " => " + this.getRoot() + "\n";
            } else {
                root = "root: " + Hex.toHexString(getRootHash()) + "\n";
            }
            return root + traceAction.getOutput();
        }
    }

    @Override
    public String getTrieDump(byte[] stateRoot) {
        TraceAllNodes traceAction = new TraceAllNodes();
        traceTrie(stateRoot, traceAction);
        return "root: " + Hex.toHexString(stateRoot) + "\n" + traceAction.getOutput();
    }

    @SuppressWarnings("unused")
    public Set<ByteArrayWrapper> getTrieKeys(byte[] stateRoot) {
        CollectFullSetOfNodes traceAction = new CollectFullSetOfNodes();
        traceTrie(stateRoot, traceAction);
        return traceAction.getCollectedHashes();
    }

    public int getTrieSize(byte[] stateRoot) {
        CountNodes traceAction = new CountNodes();
        traceTrie(stateRoot, traceAction);
        return traceAction.getCount();
    }

    private void traceTrie(byte[] stateRoot, ScanAction action) {
        synchronized (cache) {
            Value value = new Value(stateRoot);

            if (value.isHashCode()) {
                scanTreeLoop(stateRoot, action);
            } else {
                action.doOnNode(stateRoot, value);
            }
        }
    }

    // not used
    //    public boolean validate() {
    //        try {
    //            // fails when a referenced node is not found
    //            // indicating that the root is not valid
    //            scanTreeLoop(getRootHash(), new CountNodes());
    //        } catch (Exception e) {
    //            return false;
    //        }
    //        return true;
    //    }

    @Override
    public Set<ByteArrayWrapper> getMissingNodes(byte[] keyOrValue) {
        CollectFullSetOfNodes scanAction = new CollectFullSetOfNodes();
        ArrayList<byte[]> hashes = new ArrayList<>();
        Value node;

        synchronized (cache) {
            appendHashes(keyOrValue, hashes);

            int items = hashes.size();
            for (int i = 0; i < items; i++) {
                byte[] myHash = hashes.get(i);

                node = this.getCache().get(myHash);
                if (node == null) {
                    // performs action for missing nodes
                    scanAction.doOnNode(myHash, null);
                } else {
                    if (node.isList()) {
                        List<Object> siblings = node.asList();
                        if (siblings.size() == PAIR_SIZE) {
                            Value val = new Value(siblings.get(1));
                            if (val.isHashCode() && !hasTerminator((byte[]) siblings.get(0))) {
                                hashes.add(val.asBytes());
                                items++;
                            }
                        } else {
                            for (int j = 0; j < LIST_SIZE; ++j) {
                                Value val = new Value(siblings.get(j));
                                if (val.isHashCode()) {
                                    hashes.add(val.asBytes());
                                    items++;
                                }
                            }
                        }
                    }
                }
            }
        }

        return scanAction.getCollectedHashes();
    }

    @Override
    public Map<ByteArrayWrapper, byte[]> getReferencedTrieNodes(byte[] keyOrValue, int limit) {
        CollectMappings collect = new CollectMappings();
        ArrayList<byte[]> hashes = new ArrayList<>();
        Value node;

        synchronized (cache) {
            appendHashes(keyOrValue, hashes);

            int items = hashes.size();
            for (int i = 0; (i < items) && (collect.getSize() < limit); i++) {
                byte[] myHash = hashes.get(i);
                node = this.getCache().get(myHash);

                if (node != null) {
                    if (node.isList()) {
                        List<Object> siblings = node.asList();
                        if (siblings.size() == PAIR_SIZE) {
                            Value val = new Value(siblings.get(1));
                            if (val.isHashCode() && !hasTerminator((byte[]) siblings.get(0))) {
                                hashes.add(val.asBytes());
                                items++;
                            }
                        } else {
                            for (int j = 0; j < LIST_SIZE; ++j) {
                                Value val = new Value(siblings.get(j));
                                if (val.isHashCode()) {
                                    hashes.add(val.asBytes());
                                    items++;
                                }
                            }
                        }
                    }
                    collect.doOnNode(myHash, node);
                }
            }
        }
        return collect.getNodes();
    }

    private void appendHashes(byte[] bytes, ArrayList<byte[]> hashes) {
        Value node;

        if (bytes.length == 32) {
            // it's considered a hashCode/key according to Value.isHashCode()
            node = new Value(bytes);
        } else {
            node = Value.fromRlpEncoded(bytes);
        }

        if (node == null) {
            return;
        }

        if (node.isHashCode()) {
            hashes.add(node.asBytes());
        } else if (node.isList()) {
            List<Object> siblings = node.asList();
            if (siblings.size() == PAIR_SIZE) {
                Value val = new Value(siblings.get(1));
                if (val.isHashCode() && !hasTerminator((byte[]) siblings.get(0))) {
                    hashes.add(val.asBytes());
                }
            } else {
                for (int j = 0; j < LIST_SIZE; ++j) {
                    Value val = new Value(siblings.get(j));
                    if (val.isHashCode()) {
                        hashes.add(val.asBytes());
                    }
                }
            }
        }
    }

    @Override
    public long saveFullStateToDatabase(byte[] stateRoot, ByteArrayKeyValueDatabase db) {
        ExtractToDatabase traceAction = new ExtractToDatabase(db);
        traceTrie(stateRoot, traceAction);
        return traceAction.count;
    }

    private void traceDiffTrie(byte[] stateRoot, ScanAction action, ByteArrayKeyValueDatabase db) {
        synchronized (cache) {
            Value value = new Value(stateRoot);

            if (value.isHashCode() && !db.get(value.asBytes()).isPresent()) {
                scanTreeDiffLoop(stateRoot, action, db);
            } else {
                action.doOnNode(stateRoot, value);
            }
        }
    }

    @Override
    public long saveDiffStateToDatabase(byte[] stateRoot, ByteArrayKeyValueDatabase db) {
        ExtractToDatabase traceAction = new ExtractToDatabase(db);
        traceDiffTrie(stateRoot, traceAction, db);
        return traceAction.count;
    }
}
