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
 * Contributors:
 *     Aion foundation.
 */

package org.aion.mcf.trie;

import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.junit.Assert.assertEquals;

import junitparams.JUnitParamsRunner;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.mockdb.MockDB;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.util.encoders.Hex;

/** @author Alexandra Roatis */
@RunWith(JUnitParamsRunner.class)
public class TrieTestWithRootHashValues {

    private static String LONG_STRING =
            "1234567890abcdefghijklmnopqrstuvwxxzABCEFGHIJKLMNOPQRSTUVWXYZ";
    private static String ROOT_HASH_EMPTY = Hex.toHexString(EMPTY_TRIE_HASH);

    private static String c = "c";
    private static String ca = "ca";
    private static String cat = "cat";
    private static String dog = "dog";
    private static String doge = "doge";
    private static String test = "test";
    private static String dude = "dude";

    private MockDB mockDb = new MockDB("TrieTest");

    @Before
    public void setHashFunction() {
        HashUtil.setType(HashUtil.H256Type.KECCAK_256);
    }

    @After
    public void revertToOriginalHashFunction() {
        HashUtil.setType(HashUtil.H256Type.BLAKE2B_256);
    }

    @Test
    public void testPuppy() {
        TrieImpl trie = new TrieImpl(null);
        trie.update("do", "verb");
        trie.update("doge", "coin");
        trie.update("horse", "stallion");
        trie.update("dog", "puppy");

        assertEquals(
                "5991bb8c6514148a29db676a14ac506cd2cd5775ace63c30a4fe457715e9ac84",
                Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testEmptyValues() {
        TrieImpl trie = new TrieImpl(null);
        trie.update("do", "verb");
        trie.update("ether", "wookiedoo");
        trie.update("horse", "stallion");
        trie.update("shaman", "horse");
        trie.update("doge", "coin");
        trie.delete("ether");
        trie.update("dog", "puppy");
        trie.delete("shaman");

        assertEquals(
                "5991bb8c6514148a29db676a14ac506cd2cd5775ace63c30a4fe457715e9ac84",
                Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testFoo() {
        TrieImpl trie = new TrieImpl(null);
        trie.update("foo", "bar");
        trie.update("food", "bat");
        trie.update("food", "bass");

        assertEquals(
                "17beaa1648bafa633cda809c90c04af50fc8aed3cb40d16efbddee6fdf63c4c3",
                Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testSmallValues() {
        TrieImpl trie = new TrieImpl(null);

        trie.update("be", "e");
        trie.update("dog", "puppy");
        trie.update("bed", "d");
        assertEquals(
                "3f67c7a47520f79faa29255d2d3c084a7a6df0453116ed7232ff10277a8be68b",
                Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDogs() {
        TrieImpl trie = new TrieImpl(null);
        trie.update("doe", "reindeer");
        assertEquals(
                "11a0327cfcc5b7689b6b6d727e1f5f8846c1137caaa9fc871ba31b7cce1b703e",
                Hex.toHexString(trie.getRootHash()));

        trie.update("dog", "puppy");
        assertEquals(
                "05ae693aac2107336a79309e0c60b24a7aac6aa3edecaef593921500d33c63c4",
                Hex.toHexString(trie.getRootHash()));

        trie.update("dogglesworth", "cat");
        assertEquals(
                "8aad789dff2f538bca5d8ea56e8abe10f4c7ba3a5dea95fea4cd6e7c3a1168d3",
                Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testTrieUndo() {
        TrieImpl trie = new TrieImpl(mockDb);
        trie.update("doe", "reindeer");
        assertEquals(
                "11a0327cfcc5b7689b6b6d727e1f5f8846c1137caaa9fc871ba31b7cce1b703e",
                Hex.toHexString(trie.getRootHash()));
        trie.sync();

        trie.update("dog", "puppy");
        assertEquals(
                "05ae693aac2107336a79309e0c60b24a7aac6aa3edecaef593921500d33c63c4",
                Hex.toHexString(trie.getRootHash()));

        trie.undo();
        assertEquals(
                "11a0327cfcc5b7689b6b6d727e1f5f8846c1137caaa9fc871ba31b7cce1b703e",
                Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDeleteMultipleItems1() {
        String ROOT_HASH_BEFORE =
                "3a784eddf1936515f0313b073f99e3bd65c38689021d24855f62a9601ea41717";
        String ROOT_HASH_AFTER1 =
                "60a2e75cfa153c4af2783bd6cb48fd6bed84c6381bc2c8f02792c046b46c0653";
        String ROOT_HASH_AFTER2 =
                "a84739b4762ddf15e3acc4e6957e5ab2bbfaaef00fe9d436a7369c6f058ec90d";
        TrieImpl trie = new TrieImpl(mockDb);

        trie.update(cat, dog);
        assertEquals(dog, new String(trie.get(cat)));

        trie.update(ca, dude);
        assertEquals(dude, new String(trie.get(ca)));

        trie.update(doge, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(doge)));

        trie.update(dog, test);
        assertEquals(test, new String(trie.get(dog)));

        trie.update(test, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(test)));
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(dog);
        assertEquals("", new String(trie.get(dog)));
        assertEquals(ROOT_HASH_AFTER1, Hex.toHexString(trie.getRootHash()));

        trie.delete(test);
        assertEquals("", new String(trie.get(test)));
        assertEquals(ROOT_HASH_AFTER2, Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDeleteMultipleItems2() {
        String ROOT_HASH_BEFORE =
                "cf1ed2b6c4b6558f70ef0ecf76bfbee96af785cb5d5e7bfc37f9804ad8d0fb56";
        String ROOT_HASH_AFTER1 =
                "f586af4a476ba853fca8cea1fbde27cd17d537d18f64269fe09b02aa7fe55a9e";
        String ROOT_HASH_AFTER2 =
                "c59fdc16a80b11cc2f7a8b107bb0c954c0d8059e49c760ec3660eea64053ac91";

        TrieImpl trie = new TrieImpl(mockDb);
        trie.update(c, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(c)));

        trie.update(ca, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(ca)));

        trie.update(cat, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(cat)));
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(ca);
        assertEquals("", new String(trie.get(ca)));
        assertEquals(ROOT_HASH_AFTER1, Hex.toHexString(trie.getRootHash()));

        trie.delete(cat);
        assertEquals("", new String(trie.get(cat)));
        assertEquals(ROOT_HASH_AFTER2, Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDeleteLongString1() {
        String ROOT_HASH_BEFORE =
                "318961a1c8f3724286e8e80d312352f01450bc4892c165cc7614e1c2e5a0012a";
        String ROOT_HASH_AFTER = "63356ecf33b083e244122fca7a9b128cc7620d438d5d62e4f8b5168f1fb0527b";
        TrieImpl trie = new TrieImpl(mockDb);

        trie.update(cat, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(cat)));

        trie.update(dog, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(dog)));
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(dog);
        assertEquals("", new String(trie.get(dog)));
        assertEquals(ROOT_HASH_AFTER, Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDeleteLongString2() {
        String ROOT_HASH_BEFORE =
                "e020de34ca26f8d373ff2c0a8ac3a4cb9032bfa7a194c68330b7ac3584a1d388";
        String ROOT_HASH_AFTER = "334511f0c4897677b782d13a6fa1e58e18de6b24879d57ced430bad5ac831cb2";
        TrieImpl trie = new TrieImpl(mockDb);

        trie.update(ca, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(ca)));

        trie.update(cat, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(cat)));
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(cat);
        assertEquals("", new String(trie.get(cat)));
        assertEquals(ROOT_HASH_AFTER, Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDeleteLongString3() {
        String ROOT_HASH_BEFORE =
                "e020de34ca26f8d373ff2c0a8ac3a4cb9032bfa7a194c68330b7ac3584a1d388";
        String ROOT_HASH_AFTER = "63356ecf33b083e244122fca7a9b128cc7620d438d5d62e4f8b5168f1fb0527b";
        TrieImpl trie = new TrieImpl(mockDb);

        trie.update(cat, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(cat)));

        trie.update(ca, LONG_STRING);
        assertEquals(LONG_STRING, new String(trie.get(ca)));
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(ca);
        assertEquals("", new String(trie.get(ca)));
        assertEquals(ROOT_HASH_AFTER, Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void storageHashCalc_1() {

        byte[] key1 =
                Hex.decode("0000000000000000000000000000000000000000000000000000000000000010");
        byte[] key2 =
                Hex.decode("0000000000000000000000000000000000000000000000000000000000000014");
        byte[] key3 =
                Hex.decode("0000000000000000000000000000000000000000000000000000000000000016");
        byte[] key4 =
                Hex.decode("0000000000000000000000000000000000000000000000000000000000000017");

        byte[] val1 = Hex.decode("947e70f9460402290a3e487dae01f610a1a8218fda");
        byte[] val2 = Hex.decode("40");
        byte[] val3 = Hex.decode("94412e0c4f0102f3f0ac63f0a125bce36ca75d4e0d");
        byte[] val4 = Hex.decode("01");

        TrieImpl storage = new TrieImpl(new MockDB("Test"));
        storage.update(key1, val1);
        storage.update(key2, val2);
        storage.update(key3, val3);
        storage.update(key4, val4);

        String hash = Hex.toHexString(storage.getRootHash());

        System.out.println(hash);
        Assert.assertEquals(
                "517eaccda568f3fa24915fed8add49d3b743b3764c0bc495b19a47c54dbc3d62", hash);
    }

    @Test
    public void testTesty() {
        TrieImpl trie = new TrieImpl(mockDb);

        trie.update("test", "test");
        assertEquals(
                "85d106d4edff3b7a4889e91251d0a87d7c17a1dda648ebdba8c6060825be23b8",
                Hex.toHexString(trie.getRootHash()));

        trie.update("te", "testy");
        assertEquals(
                "8452568af70d8d140f58d941338542f645fcca50094b20f3c3d8c3df49337928",
                Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDeleteShortString1() {
        String ROOT_HASH_BEFORE =
                "a9539c810cc2e8fa20785bdd78ec36cc1dab4b41f0d531e80a5e5fd25c3037ee";
        String ROOT_HASH_AFTER = "fc5120b4a711bca1f5bb54769525b11b3fb9a8d6ac0b8bf08cbb248770521758";
        TrieImpl trie = new TrieImpl(mockDb);

        trie.update(cat, dog);
        assertEquals(dog, new String(trie.get(cat)));

        trie.update(ca, dude);
        assertEquals(dude, new String(trie.get(ca)));
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(ca);
        assertEquals("", new String(trie.get(ca)));
        assertEquals(ROOT_HASH_AFTER, Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDeleteShortString2() {
        String ROOT_HASH_BEFORE =
                "a9539c810cc2e8fa20785bdd78ec36cc1dab4b41f0d531e80a5e5fd25c3037ee";
        String ROOT_HASH_AFTER = "b25e1b5be78dbadf6c4e817c6d170bbb47e9916f8f6cc4607c5f3819ce98497b";
        TrieImpl trie = new TrieImpl(mockDb);

        trie.update(ca, dude);
        assertEquals(dude, new String(trie.get(ca)));

        trie.update(cat, dog);
        assertEquals(dog, new String(trie.get(cat)));
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(cat);
        assertEquals("", new String(trie.get(cat)));
        assertEquals(ROOT_HASH_AFTER, Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testDeleteShortString3() {
        String ROOT_HASH_BEFORE =
                "778ab82a7e8236ea2ff7bb9cfa46688e7241c1fd445bf2941416881a6ee192eb";
        String ROOT_HASH_AFTER = "05875807b8f3e735188d2479add82f96dee4db5aff00dc63f07a7e27d0deab65";
        TrieImpl trie = new TrieImpl(mockDb);

        trie.update(cat, dude);
        assertEquals(dude, new String(trie.get(cat)));

        trie.update(dog, test);
        assertEquals(test, new String(trie.get(dog)));
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(dog);
        assertEquals("", new String(trie.get(dog)));
        assertEquals(ROOT_HASH_AFTER, Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testSingleItem() {
        TrieImpl trie = new TrieImpl(mockDb);
        trie.update("A", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        assertEquals(
                "d23786fb4a010da3ce639d66d5e904a11dbc02746d1ce25029e53290cabf28ab",
                Hex.toHexString(trie.getRootHash()));
    }

    @Test
    public void testSecureTrie() {

        Trie trie = new SecureTrie(mockDb);

        byte[] k1 = "do".getBytes();
        byte[] v1 = "verb".getBytes();

        byte[] k2 = "ether".getBytes();
        byte[] v2 = "wookiedoo".getBytes();

        byte[] k3 = "horse".getBytes();
        byte[] v3 = "stallion".getBytes();

        byte[] k4 = "shaman".getBytes();
        byte[] v4 = "horse".getBytes();

        byte[] k5 = "doge".getBytes();
        byte[] v5 = "coin".getBytes();

        byte[] k6 = "ether".getBytes();

        byte[] k7 = "dog".getBytes();
        byte[] v7 = "puppy".getBytes();

        byte[] k8 = "shaman".getBytes();

        trie.update(k1, v1);
        trie.update(k2, v2);
        trie.update(k3, v3);
        trie.update(k4, v4);
        trie.update(k5, v5);
        trie.delete(k6);
        trie.update(k7, v7);
        trie.delete(k8);

        byte[] root = trie.getRootHash();

        System.out.println("root: " + Hex.toHexString(root));

        Assert.assertEquals(
                "29b235a58c3c25ab83010c327d5932bcf05324b7d6b1185e650798034783ca9d",
                Hex.toHexString(root));
    }

    @Test
    public void testDeleteAll() {
        String ROOT_HASH_BEFORE =
                "a84739b4762ddf15e3acc4e6957e5ab2bbfaaef00fe9d436a7369c6f058ec90d";
        TrieImpl trie = new TrieImpl(null);
        assertEquals(ROOT_HASH_EMPTY, Hex.toHexString(trie.getRootHash()));

        trie.update(ca, dude);
        trie.update(cat, dog);
        trie.update(doge, LONG_STRING);
        assertEquals(ROOT_HASH_BEFORE, Hex.toHexString(trie.getRootHash()));

        trie.delete(ca);
        trie.delete(cat);
        trie.delete(doge);
        assertEquals(ROOT_HASH_EMPTY, Hex.toHexString(trie.getRootHash()));
    }
}
