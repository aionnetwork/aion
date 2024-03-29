package org.aion.zero.impl.trie;

import static java.util.Arrays.copyOfRange;
import static org.aion.rlp.CompactEncoder.binToNibbles;
import static org.aion.rlp.CompactEncoder.hasTerminator;
import static org.aion.rlp.CompactEncoder.packNibbles;
import static org.aion.rlp.CompactEncoder.unpackToNibbles;
import static org.aion.rlp.RLP.calcElementPrefixSize;
import static org.aion.util.bytes.ByteUtil.matchingNibbleLength;
import static org.aion.util.types.ByteArrayWrapper.wrap;
import static org.aion.zero.impl.trie.Node.isEmptyNode;
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
import java.util.concurrent.locks.ReentrantLock;
import org.aion.base.ConstantUtil;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.ByteArrayKeyValueDatabase;
import org.aion.db.impl.ByteArrayKeyValueStore;
import org.aion.rlp.RLP;
import org.aion.rlp.SharedRLPList;
import org.aion.rlp.Value;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.conversions.Hex;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.trie.scan.CollectFullSetOfNodes;
import org.aion.zero.impl.trie.scan.CollectMappings;
import org.aion.zero.impl.trie.scan.CountNodes;
import org.aion.zero.impl.trie.scan.ExtractToDatabase;
import org.aion.zero.impl.trie.scan.ScanAction;
import org.aion.zero.impl.trie.scan.TraceAllNodes;

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
    private Object root;
    private Cache cache;

    private boolean pruningEnabled;
    private ReentrantLock lock;

    public TrieImpl(ByteArrayKeyValueStore db) {
        this(db, "");
    }

    public TrieImpl(ByteArrayKeyValueStore db, Object root) {
        this(new Cache(db), root);
    }

    public TrieImpl(final Cache cache, Object root) {
        this.cache = cache;
        this.root = root;
        lock = new ReentrantLock();
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Cache getCache() {
        return this.cache;
    }

    public Object getRoot() {
        return root;
    }

    /** for testing TrieTest.testRollbackToRootScenarios */
    public void setRoot(Object root) {
        this.root = root;
    }

    @Override
    public void setRoot(byte[] root) {
        lock.lock();
        try {
            this.root = root;
        } finally {
            lock.unlock();
        }
    }

    private void deserializeRoot(byte[] data) {
        try {
            ByteArrayInputStream b = new ByteArrayInputStream(data);
            ObjectInputStream o = new ObjectInputStream(b);
            root = o.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
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
    @VisibleForTesting
    byte[] get(String key) {
        return this.get(key.getBytes());
    }

    @Override
    public byte[] get(byte[] key) {
        lock.lock();
        try {
            byte[] k = binToNibbles(key);
            Object o = this.get(this.root, k);
            if (o instanceof String) {
                return ((String)o).getBytes();
            } else if (o instanceof byte[]){
                return (byte[]) o;
            } else {
                // TODO: do we really need to check this case?
                return ByteUtil.EMPTY_BYTE_ARRAY;
            }
        } finally {
            lock.unlock();
        }
    }

    /** Insert key/value pair into trie. */
    @VisibleForTesting
    void update(String key, String value) {
        this.update(key.getBytes(), value.getBytes());
    }

    @Override
    public void update(byte[] key, byte[] value) {
        lock.lock();
        try {
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

            if (isEmptyNode(root)) {
                cache.markRemoved(getRootHashInner());
            }

            this.root = this.insert(this.root, k, value);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isValidRoot(byte[] root) {
        lock.lock();
        try {
            return this.getNode(root) != null;
        } finally {
            lock.unlock();
        }
    }

    /** Delete a key/value pair from the trie. */
    @VisibleForTesting
    void delete(String key) {
        this.delete(key.getBytes());
    }

    @Override
    public void delete(byte[] key) {
        lock.lock();
        try {
            byte[] k = binToNibbles(key);

            if (isEmptyNode(root)) {
                cache.markRemoved(getRootHashInner());
            }

            this.root = this.delete(this.root, k);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public byte[] getRootHash() {
        lock.lock();
        try {
            return getRootHashInner();
        } finally {
            lock.unlock();
        }
    }

    private byte[] getRootHashInner() {
        if (root == null
            || (root instanceof byte[] && ((byte[]) root).length == 0)
            || (root instanceof String && "".equals(root))) {
            return ConstantUtil.EMPTY_TRIE_HASH;
        } else if (root instanceof byte[]) {
            return (byte[])root;
        } else {
            return HashUtil.h256(RLP.encode(root));
        }
    }

    private Object get(Object node, byte[] key) {
        int keypos = 0;
        while (key.length - keypos != 0 && !isEmptyNode(node)) {
            Value currentNode = this.getNode(node);
            if (currentNode == null) {
                return null;
            }

            if (currentNode.length() == Node.PAIR_SIZE) {
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
        if (currentNode.length() == Node.PAIR_SIZE) {
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
                Object[] scaledSlice = Node.createSlices();

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

            // clone the current node over to the new node
            Object[] newNode = Node.clone(currentNode);

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
        if (currentNode.length() == Node.PAIR_SIZE) {
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
                if (child.length() == Node.PAIR_SIZE) {
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
            // clone the current node over to a new node
            Object[] itemList = Node.clone(currentNode);

            // Replace the first nibble in the key
            itemList[key[0]] = this.delete(itemList[key[0]], copyOfRange(key, 1, key.length));

            byte amount = -1;
            for (byte i = 0; i < Node.BRANCH_SIZE; i++) {
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
                if (child.length() == Node.PAIR_SIZE) {
                    key = concatenate(new byte[] {amount}, unpackToNibbles(child.get(0).asBytes()));
                    newNode = new Object[] {packNibbles(key), child.get(1).asObj()};
                } else if (child.length() == Node.BRANCH_SIZE) {
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
        } else if (keyBytes.length < ByteUtil.EMPTY_WORD.length) {
            return new Value(keyBytes);
        }
        Node nodeFromCache = this.cache.get(keyBytes);
        return nodeFromCache == null ? null : nodeFromCache.getValue();
    }

    private Object putToCache(Object node) {
        Value value = new Value(node);
        byte[] enc = value.encode();
        if (enc.length >= ByteUtil.EMPTY_WORD.length) {
            byte[] sha = HashUtil.h256(enc);
            this.cache.put(ByteArrayWrapper.wrap(sha), new Node(value, true));
            return sha;
        }

        return node;
    }

    // Simple compare function which compares two tries based on their stateRoot
    @Override
    public boolean equals(Object trie) {
        lock.lock();
        try {
            return this == trie
                    || trie instanceof Trie
                            && Arrays.equals(this.getRootHashInner(), ((Trie) trie).getRootHash());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void sync() {
        lock.lock();
        try {
            this.cache.commit();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void syncWithoutFlush() {
        lock.lock();
        try {
            this.cache.commitWithoutFlush();
        } finally {
            lock.unlock();
        }
    }

    /** ****************************** Utility functions * ***************************** */
    private void scanTree(byte[] hash, ScanAction scanAction) {
        Node node = this.getCache().get(hash);
        if (node == null) {
            throw new RuntimeException("Not found: " + Hex.toHexString(hash));
        }

        if (node.isPair()) {
            if (node.isExtension()) {
                scanTree(node.getKey(), scanAction);
            }
        } else if (node.isBranch()) {
            for (int index = 0; index < Node.BRANCH_SIZE; index++) {
                Value item = node.getBranchItem(index);
                if (item.isHashCode()) {
                    scanTree(item.asBytes(), scanAction);
                }
            }
        } else {
            return;
        }
        scanAction.doOnNode(hash, node.getValue());
    }

    private void scanTreeLoop(byte[] hash, ScanAction scanAction) {

        List<byte[]> hashes = new ArrayList<>();
        hashes.add(hash);

        while (!hashes.isEmpty()) {
            byte[] myHash = hashes.remove(0);
            Node node = this.getCache().get(myHash);
            if (node == null) {
                throw new RuntimeException("Not found: " + Hex.toHexString(myHash));
            }

            if (node.isPair()) {
                if (node.isExtension()) {
                    hashes.add(node.getKey());
                }
            } else if (node.isBranch()) {
                for (int index = 0; index < Node.BRANCH_SIZE; index++) {
                    Value item = node.getBranchItem(index);
                    if (item.isHashCode()) {
                        hashes.add(item.asBytes());
                    }
                }
            } else {
                continue;
            }
            scanAction.doOnNode(myHash, node.getValue());
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

        List<byte[]> hashes = new ArrayList<>();
        hashes.add(hash);

        while (!hashes.isEmpty()) {
            byte[] myHash = hashes.remove(0);
            Node node = this.getCache().get(myHash);
            if (node == null) {
                System.out.println("Skipped key. Not found: " + Hex.toHexString(myHash));
            } else {
                if (node.isPair()) {
                    if (node.isExtension()) {
                        byte[] key = node.getKey();
                        if (!db.get(key).isPresent()) {
                            hashes.add(key);
                        }
                    }
                } else if (node.isBranch()) {
                    for (int index = 0; index < Node.BRANCH_SIZE; index++) {
                        Value item = node.getBranchItem(index);
                        if (item.isHashCode()) {
                            byte[] key = item.asBytes();
                            if (!db.get(key).isPresent()) {
                                hashes.add(key);
                            }
                        }
                    }
                } else {
                    continue;
                }
                scanAction.doOnNode(myHash, node.getValue());
            }
        }
    }

    public void deserialize(SharedRLPList storage) {
        lock.lock();
        try {
            SharedRLPList rlpList = (SharedRLPList) storage.get(0);

            SharedRLPList valsList = (SharedRLPList) rlpList.get(1);
            for (int i = 0; i < valsList.size(); ++i) {

                byte[] val = valsList.get(i).getRLPData();
                byte[] key = new byte[ByteUtil.EMPTY_WORD.length];

                Value value = Value.fromRlpEncoded(val);
                System.arraycopy(rlpList.get(0).getRLPData(), i * ByteUtil.EMPTY_WORD.length, key, 0, ByteUtil.EMPTY_WORD.length);
                cache.getNodes().put(wrap(key), new Node(value));
            }

            this.deserializeRoot(rlpList.get(2).getRLPData());
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    public byte[] serialize() {
        lock.lock();
        try {
            Map<ByteArrayWrapper, Node> map = getCache().getNodes();

            int keysTotalSize = 0;
            int valsTotalSize = 0;

            Set<ByteArrayWrapper> keys = map.keySet();
            for (ByteArrayWrapper key : keys) {
                Node node = map.get(key);
                if (node == null) {
                    continue;
                }

                byte[] keyBytes = key.toBytes();
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

                // TODO: make internal wrapper operation
                System.arraycopy(
                        key.toBytes(),
                        0,
                        rlpData,
                        (listHeader.length + keysHeader.length + k_1),
                        key.length());

                k_1 += key.length();

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
        } finally {
            lock.unlock();
        }
    }

    public String getTrieDump() {
        lock.lock();
        try {
            TraceAllNodes traceAction = new TraceAllNodes();
            Value value = new Value(root);
            if (value.isHashCode()) {
                this.scanTree(this.getRootHashInner(), traceAction);
            } else {
                traceAction.doOnNode(this.getRootHashInner(), value);
            }

            final String root;
            if (this.getRoot() instanceof Value) {
                root = "root: " + Hex.toHexString(getRootHashInner()) + " => " + this.getRoot() + "\n";
            } else {
                root = "root: " + Hex.toHexString(getRootHashInner()) + "\n";
            }
            return root + traceAction.getOutput();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getTrieDump(byte[] stateRoot) {
        lock.lock();
        try {
            TraceAllNodes traceAction = new TraceAllNodes();
            traceTrie(stateRoot, traceAction);
            return "root: " + Hex.toHexString(stateRoot) + "\n" + traceAction.getOutput();
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    public Set<ByteArrayWrapper> getTrieKeys(byte[] stateRoot) {
        CollectFullSetOfNodes traceAction = new CollectFullSetOfNodes();
        traceTrie(stateRoot, traceAction);
        return traceAction.getCollectedHashes();
    }

    public int getTrieSize(byte[] stateRoot) {
        lock.lock();
        try {
            CountNodes traceAction = new CountNodes();
            traceTrie(stateRoot, traceAction);
            return traceAction.getCount();
        } finally {
            lock.unlock();
        }
    }

    private void traceTrie(byte[] stateRoot, ScanAction action) {
        Value value = new Value(stateRoot);

        if (value.isHashCode()) {
            scanTreeLoop(stateRoot, action);
        } else {
            action.doOnNode(stateRoot, value);
        }
    }

    @Override
    public Set<ByteArrayWrapper> getMissingNodes(byte[] keyOrValue) {
        lock.lock();
        try {
            CollectFullSetOfNodes scanAction = new CollectFullSetOfNodes();
            List<byte[]> hashes = appendHashes(keyOrValue);

            int items = hashes.size();
            for (int i = 0; i < items; i++) {
                byte[] hash = hashes.get(i);
                Node node = this.getCache().get(hash);
                if (node == null) {
                    // performs action for missing nodes
                    scanAction.doOnNode(hash, null);
                } else if (node.isPair() && node.isExtension()) {
                    hashes.add(node.getKey());
                    items++;
                } else if (node.isBranch()) {
                    for (int index = 0; index < Node.BRANCH_SIZE; index++) {
                        Value item = node.getBranchItem(index);
                        if (item.isHashCode()) {
                            hashes.add(item.asBytes());
                            items++;
                        }
                    }
                }
            }
            return scanAction.getCollectedHashes();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<ByteArrayWrapper, byte[]> getReferencedTrieNodes(byte[] keyOrValue, int limit) {
        lock.lock();
        try {
            CollectMappings collect = new CollectMappings();
            List<byte[]> hashes = appendHashes(keyOrValue);

            int items = hashes.size();
            for (int i = 0; (i < items) && (collect.getSize() < limit); i++) {
                byte[] myHash = hashes.get(i);
                Node node = this.getCache().get(myHash);

                if (node != null) {
                    if (node.isPair()) {
                        if (node.isExtension()) {
                            hashes.add(node.getKey());
                            items++;
                        }
                    } else if (node.isBranch()) {
                        for (int index = 0; index < Node.BRANCH_SIZE; index++) {
                            Value item = node.getBranchItem(index);
                            if (item.isHashCode()) {
                                hashes.add(item.asBytes());
                                items++;
                            }
                        }
                    }
                    collect.doOnNode(myHash, node.getValue());
                }
            }

            return collect.getNodes();
        } finally {
            lock.unlock();
        }
    }

    private List<byte[]> appendHashes(byte[] bytes) {
        List<byte[]> hashes = new ArrayList<>();
        Value node;

        if (bytes.length == ByteUtil.EMPTY_WORD.length) {
            // it's considered a hashCode/key according to Value.isHashCode()
            node = new Value(bytes);
        } else {
            node = Value.fromRlpEncoded(bytes);
        }

        if (node == null) {
            return hashes;
        }

        if (node.isHashCode()) {
            hashes.add(node.asBytes());
        } else if (node.isList()) {
            List<Object> siblings = node.asList();
            if (siblings.size() == Node.PAIR_SIZE) {
                Value val = new Value(siblings.get(1));
                if (val.isHashCode() && !hasTerminator((byte[]) siblings.get(0))) {
                    hashes.add(val.asBytes());
                }
            } else {
                for (int j = 0; j < Node.BRANCH_SIZE; ++j) {
                    Value val = new Value(siblings.get(j));
                    if (val.isHashCode()) {
                        hashes.add(val.asBytes());
                    }
                }
            }
        }
        return hashes;
    }

    @Override
    public long saveFullStateToDatabase(byte[] stateRoot, ByteArrayKeyValueDatabase db) {
        lock.lock();
        try {
            ExtractToDatabase traceAction = new ExtractToDatabase(db);
            traceTrie(stateRoot, traceAction);
            db.commit();
            return traceAction.count;
        } finally {
            lock.unlock();
        }
    }

    private void traceDiffTrie(byte[] stateRoot, ScanAction action, ByteArrayKeyValueDatabase db) {
        Value value = new Value(stateRoot);

        if (value.isHashCode() && !db.get(value.asBytes()).isPresent()) {
            scanTreeDiffLoop(stateRoot, action, db);
        } else {
            action.doOnNode(stateRoot, value);
        }
    }

    @Override
    public long saveDiffStateToDatabase(byte[] stateRoot, ByteArrayKeyValueDatabase db) {
        lock.lock();
        try {
            ExtractToDatabase traceAction = new ExtractToDatabase(db);
            traceDiffTrie(stateRoot, traceAction, db);
            db.commit();
            return traceAction.count;
        } finally {
            lock.unlock();
        }
    }
}
