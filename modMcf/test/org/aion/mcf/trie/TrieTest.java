package org.aion.mcf.trie;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.aion.util.bytes.ByteUtil.intToBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.crypto.HashUtil;
import org.aion.db.impl.mockdb.MockDB;
import org.aion.rlp.Value;
import org.aion.vm.api.types.ByteArrayWrapper;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.util.encoders.Hex;

/** @author Alexandra Roatis */
@RunWith(JUnitParamsRunner.class)
public class TrieTest {

    private String[] testKeys =
            new String[] {
                "",
                "k",
                "ky",
                "key",
                "ey",
                "y",
                "other",
                "key-0123456789",
                "key-0123456789abcdefghijklmnopqrstuvwxxzABCEFGHIJKLMNOPQRSTUVWXYZ"
            };

    private String[] testValues =
            new String[] {
                "v", "value", "value-0123456789abcdefghijklmnopqrstuvwxxzABCEFGHIJKLMNOPQRSTUVWXYZ"
            };

    private String[] randomValues =
            new String[] {
                "spinneries",
                "archipenko",
                "prepotency",
                "herniotomy",
                "preexpress",
                "relaxative",
                "insolvably",
                "debonnaire",
                "apophysate",
                "virtuality",
                "cavalryman",
                "utilizable",
                "diagenesis",
                "vitascopic",
                "governessy",
                "abranchial",
                "cyanogenic",
                "gratulated",
                "signalment",
                "predicable",
                "subquality",
                "crystalize",
                "prosaicism",
                "oenologist",
                "repressive",
                "impanelled",
                "cockneyism",
                "bordelaise",
                "compigne",
                "konstantin",
                "predicated",
                "unsublimed",
                "hydrophane",
                "phycomyces",
                "capitalise",
                "slippingly",
                "untithable",
                "unburnable",
                "deoxidizer",
                "misteacher",
                "precorrect",
                "disclaimer",
                "solidified",
                "neuraxitis",
                "caravaning",
                "betelgeuse",
                "underprice",
                "uninclosed",
                "acrogynous",
                "reirrigate",
                "dazzlingly",
                "chaffiness",
                "corybantes",
                "intumesced",
                "intentness",
                "superexert",
                "abstrusely",
                "astounding",
                "pilgrimage",
                "posttarsal",
                "prayerless",
                "nomologist",
                "semibelted",
                "frithstool",
                "unstinging",
                "ecalcarate",
                "amputating",
                "megascopic",
                "graphalloy",
                "platteland",
                "adjacently",
                "mingrelian",
                "valentinus",
                "appendical",
                "unaccurate",
                "coriaceous",
                "waterworks",
                "sympathize",
                "doorkeeper",
                "overguilty",
                "flaggingly",
                "admonitory",
                "aeriferous",
                "normocytic",
                "parnellism",
                "catafalque",
                "odontiasis",
                "apprentice",
                "adulterous",
                "mechanisma",
                "wilderness",
                "undivorced",
                "reinterred",
                "effleurage",
                "pretrochal",
                "phytogenic",
                "swirlingly",
                "herbarized",
                "unresolved",
                "classifier",
                "diosmosing",
                "microphage",
                "consecrate",
                "astarboard",
                "predefying",
                "predriving",
                "lettergram",
                "ungranular",
                "overdozing",
                "conferring",
                "unfavorite",
                "peacockish",
                "coinciding",
                "erythraeum",
                "freeholder",
                "zygophoric",
                "imbitterer",
                "centroidal",
                "appendixes",
                "grayfishes",
                "enological",
                "indiscreet",
                "broadcloth",
                "divulgated",
                "anglophobe",
                "stoopingly",
                "bibliophil",
                "laryngitis",
                "separatist",
                "estivating",
                "bellarmine",
                "greasiness",
                "typhlology",
                "xanthation",
                "mortifying",
                "endeavorer",
                "aviatrices",
                "unequalise",
                "metastatic",
                "leftwinger",
                "apologizer",
                "quatrefoil",
                "nonfouling",
                "bitartrate",
                "outchiding",
                "undeported",
                "poussetted",
                "haemolysis",
                "asantehene",
                "montgomery",
                "unjoinable",
                "cedarhurst",
                "unfastener",
                "nonvacuums",
                "beauregard",
                "animalized",
                "polyphides",
                "cannizzaro",
                "gelatinoid",
                "apologised",
                "unscripted",
                "tracheidal",
                "subdiscoid",
                "gravelling",
                "variegated",
                "interabang",
                "inoperable",
                "immortelle",
                "laestrygon",
                "duplicatus",
                "proscience",
                "deoxidised",
                "manfulness",
                "channelize",
                "nondefense",
                "ectomorphy",
                "unimpelled",
                "headwaiter",
                "hexaemeric",
                "derivation",
                "prelexical",
                "limitarian",
                "nonionized",
                "prorefugee",
                "invariably",
                "patronizer",
                "paraplegia",
                "redivision",
                "occupative",
                "unfaceable",
                "hypomnesia",
                "psalterium",
                "doctorfish",
                "gentlefolk",
                "overrefine",
                "heptastich",
                "desirously",
                "clarabelle",
                "uneuphonic",
                "autotelism",
                "firewarden",
                "timberjack",
                "fumigation",
                "drainpipes",
                "spathulate",
                "novelvelle",
                "bicorporal",
                "grisliness",
                "unhesitant",
                "supergiant",
                "unpatented",
                "womanpower",
                "toastiness",
                "multichord",
                "paramnesia",
                "undertrick",
                "contrarily",
                "neurogenic",
                "gunmanship",
                "settlement",
                "brookville",
                "gradualism",
                "unossified",
                "villanovan",
                "ecospecies",
                "organising",
                "buckhannon",
                "prefulfill",
                "johnsonese",
                "unforegone",
                "unwrathful",
                "dunderhead",
                "erceldoune",
                "unwadeable",
                "refunction",
                "understuff",
                "swaggering",
                "freckliest",
                "telemachus",
                "groundsill",
                "outslidden",
                "bolsheviks",
                "recognizer",
                "hemangioma",
                "tarantella",
                "muhammedan",
                "talebearer",
                "relocation",
                "preemption",
                "chachalaca",
                "septuagint",
                "ubiquitous",
                "plexiglass",
                "humoresque",
                "biliverdin",
                "tetraploid",
                "capitoline",
                "summerwood",
                "undilating",
                "undetested",
                "meningitic",
                "petrolatum",
                "phytotoxic",
                "adiphenine",
                "flashlight",
                "protectory",
                "inwreathed",
                "rawishness",
                "tendrillar",
                "hastefully",
                "bananaquit",
                "anarthrous",
                "unbedimmed",
                "herborized",
                "decenniums",
                "deprecated",
                "karyotypic",
                "squalidity",
                "pomiferous",
                "petroglyph",
                "actinomere",
                "peninsular",
                "trigonally",
                "androgenic",
                "resistance",
                "unassuming",
                "frithstool",
                "documental",
                "eunuchised",
                "interphone",
                "thymbraeus",
                "confirmand",
                "expurgated",
                "vegetation",
                "myographic",
                "plasmagene",
                "spindrying",
                "unlackeyed",
                "foreknower",
                "mythically",
                "albescence",
                "rebudgeted",
                "implicitly",
                "unmonastic",
                "torricelli",
                "mortarless",
                "labialized",
                "phenacaine",
                "radiometry",
                "sluggishly",
                "understood",
                "wiretapper",
                "jacobitely",
                "unbetrayed",
                "stadholder",
                "directress",
                "emissaries",
                "corelation",
                "sensualize",
                "uncurbable",
                "permillage",
                "tentacular",
                "thriftless",
                "demoralize",
                "preimagine",
                "iconoclast",
                "acrobatism",
                "firewarden",
                "transpired",
                "bluethroat",
                "wanderjahr",
                "groundable",
                "pedestrian",
                "unulcerous",
                "preearthly",
                "freelanced",
                "sculleries",
                "avengingly",
                "visigothic",
                "preharmony",
                "bressummer",
                "acceptable",
                "unfoolable",
                "predivider",
                "overseeing",
                "arcosolium",
                "piriformis",
                "needlecord",
                "homebodies",
                "sulphation",
                "phantasmic",
                "unsensible",
                "unpackaged",
                "isopiestic",
                "cytophagic",
                "butterlike",
                "frizzliest",
                "winklehawk",
                "necrophile",
                "mesothorax",
                "cuchulainn",
                "unrentable",
                "untangible",
                "unshifting",
                "unfeasible",
                "poetastric",
                "extermined",
                "gaillardia",
                "nonpendent",
                "harborside",
                "pigsticker",
                "infanthood",
                "underrower",
                "easterling",
                "jockeyship",
                "housebreak",
                "horologium",
                "undepicted",
                "dysacousma",
                "incurrable",
                "editorship",
                "unrelented",
                "peritricha",
                "interchaff",
                "frothiness",
                "underplant",
                "proafrican",
                "squareness",
                "enigmatise",
                "reconciled",
                "nonnumeral",
                "nonevident",
                "hamantasch",
                "victualing",
                "watercolor",
                "schrdinger",
                "understand",
                "butlerlike",
                "hemiglobin",
                "yankeeland"
            };

    /** @return parameters for testing {@link #testInsertUpdateDeleteGet(String, String, String)} */
    @SuppressWarnings("unused")
    private Object keyValue1Value2Parameters() {

        Object[] parameters = new Object[testKeys.length * testValues.length * testValues.length];

        int index = 0;
        for (String key : testKeys) {
            for (String value1 : testValues) {
                for (String value2 : testValues) {
                    // also testing updates with the same value
                    // if (!value1.equals(value2)) {
                    parameters[index] = new Object[] {key, value1, value2};
                    index++;
                    // }
                }
            }
        }

        return parameters;
    }

    /**
     * Tests correct retrieval using {@link TrieImpl#get(String)} after an initial insert with
     * {@link TrieImpl#update(String, String)} and after a sequential update using {@link
     * TrieImpl#update(String, String)}. Uses diverse combinations of keys and values from {@link
     * #keyValue1Value2Parameters()}.
     */
    @Test
    @Parameters(method = "keyValue1Value2Parameters")
    public void testInsertUpdateDeleteGet(String key, String value1, String value2) {
        // create a new trie object without database
        TrieImpl trie = new TrieImpl(null);

        // -------------------------------------------------------------------------------------------------------------
        // insert (key,value1) pair into the trie
        trie.update(key, value1);

        // ensure the correct value is retrieved
        assertThat(value1).isEqualTo(new String(trie.get(key)));

        // -------------------------------------------------------------------------------------------------------------
        // update to (key,value2)
        trie.update(key, value2);

        // ensure correct value retrieval after update
        assertThat(value2).isEqualTo(new String(trie.get(key)));

        // -------------------------------------------------------------------------------------------------------------
        // delete (key,value2)
        trie.delete(key);

        // ensure correct value retrieval after update
        assertThat(value1).isNotEqualTo(new String(trie.get(key)));
        assertThat(value2).isNotEqualTo(new String(trie.get(key)));
    }

    @Test
    @Parameters(method = "keyValue1Value2Parameters")
    public void testRollbackToRootScenarios(String key, String value1, String value2) {
        // create a new trie object with mock database
        TrieImpl trie = new TrieImpl(new MockDB("TestKnownRoot"));

        // -------------------------------------------------------------------------------------------------------------
        // insert (key,value1) pair into the trie
        trie.update(key, value1);
        Object oldRoot = trie.getRoot();

        // check retrieval after new addition
        assertThat(new String(trie.get(key))).isEqualTo(value1);

        // -------------------------------------------------------------------------------------------------------------
        // update to (key,value2)
        trie.update(key, value2);
        Object newRoot = trie.getRoot();

        // check retrieval after new addition
        assertThat(new String(trie.get(key))).isEqualTo(value2);

        // -------------------------------------------------------------------------------------------------------------
        // check old root retrieval
        trie.setRoot(oldRoot);
        assertThat(new String(trie.get(key))).isEqualTo(value1);

        // -------------------------------------------------------------------------------------------------------------
        // delete
        trie.delete(key);
        assertThat(new String(trie.get(key))).isNotEqualTo(value1);

        // check old root retrieval
        trie.setRoot(oldRoot);
        assertThat(new String(trie.get(key))).isEqualTo(value1);

        // -------------------------------------------------------------------------------------------------------------
        // check new root retrieval
        trie.setRoot(newRoot);
        assertThat(new String(trie.get(key))).isEqualTo(value2);

        // -------------------------------------------------------------------------------------------------------------
        // update to (key+value1,value2)
        trie.update(key + value1, value2);
        Object updateRoot = trie.getRoot();

        // check retrieval after new addition
        assertThat(new String(trie.get(key))).isEqualTo(value2);

        // -------------------------------------------------------------------------------------------------------------
        // delete
        trie.delete(key);
        assertThat(new String(trie.get(key))).isNotEqualTo(value1);
        assertThat(new String(trie.get(key))).isNotEqualTo(value2);

        // check new root retrieval
        trie.setRoot(updateRoot);
        assertThat(new String(trie.get(key))).isEqualTo(value2);
    }

    /** @return parameters for testing {@link #testInsertRandomMultipleItems(HashMap)} */
    @SuppressWarnings("unused")
    private Object keyValuePairsParameters() {
        Random generator = new Random();
        Object[] parameters = new Object[10];

        int examples;
        for (int i = 0; i < parameters.length; i++) {
            // the examples must be > 0 to work correctly
            examples = generator.nextInt(1000) + 1;
            Map<String, String> testCase = new HashMap<>();

            String key, value;
            for (int j = 0; j < examples; j++) {
                key = testKeys[generator.nextInt(testKeys.length)] + j;
                value = testValues[generator.nextInt(testValues.length)] + j;
                testCase.put(key, value);
            }

            parameters[i] = new Object[] {testCase};
        }

        return parameters;
    }

    /**
     * @param pairs
     * @implNote By design the keys are distinct due to the use of HashMap.
     */
    @Test
    @Parameters(method = "keyValuePairsParameters")
    public void testInsertRandomMultipleItems(HashMap<String, String> pairs) {
        boolean print = false;

        if (print) {
            System.out.println("Number of pairs = " + pairs.size());
        }

        TrieImpl trie = new TrieImpl(new MockDB("TestInsertRandomMultipleItems"));
        String key, value;

        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();

            if (print) {
                System.out.println("(" + key + "," + value + ")");
            }

            // insert (key,value)
            trie.update(key, value);

            assertThat(new String(trie.get(key))).isEqualTo(value);
        }

        // ensure that everything is still there
        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();

            assertThat(new String(trie.get(key))).isEqualTo(value);
        }
    }

    /**
     * @param pairs
     * @implNote By design the keys are distinct due to the use of HashMap.
     */
    @Test
    @Parameters(method = "keyValuePairsParameters")
    public void testDeleteAll(HashMap<String, String> pairs) {
        boolean print = false;

        if (print) {
            System.out.println("Number of pairs = " + pairs.size());
        }

        TrieImpl trie = new TrieImpl(new MockDB("TestDeleteAll"));

        // empty at start
        assertThat(Hex.toHexString(trie.getRootHash())).isEqualTo(ROOT_HASH_EMPTY);

        String key, value;

        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();

            if (print) {
                System.out.println("(" + key + "," + value + ")");
            }

            // insert (key,value)
            trie.update(key, value);
        }

        // not empty after inserts
        assertThat(Hex.toHexString(trie.getRootHash())).isNotEqualTo(ROOT_HASH_EMPTY);

        // ensure that everything is still there
        for (String deleteKey : pairs.keySet()) {
            trie.delete(deleteKey);
        }

        // empty at end
        assertThat(Hex.toHexString(trie.getRootHash())).isEqualTo(ROOT_HASH_EMPTY);
    }

    @Test
    @Parameters(method = "keyValuePairsParameters")
    public void testTrieRootEquality(HashMap<String, String> pairs) {

        boolean print = false;

        if (print) {
            System.out.println("Number of pairs = " + pairs.size());
        }

        // create a new trie object without database
        TrieImpl trie1 = new TrieImpl(null);
        TrieImpl trie2 = new TrieImpl(null);

        String key, value;

        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();

            if (print) {
                System.out.println("(" + key + "," + value + ")");
            }

            // insert (key,value)
            trie1.update(key, value);
            trie2.update(key, value);

            // ensure equality after each addition
            assertThat(trie1).isEqualTo(trie2);
            assertThat(Hex.toHexString(trie1.getRootHash()))
                    .isEqualTo(Hex.toHexString(trie2.getRootHash()));
        }
        String oldHash = Hex.toHexString(trie1.getRootHash());

        // ensure inequality after new additions
        trie1.update("key for trie1", "value1");
        trie2.update("key for trie2", "value2");

        assertThat(trie1).isNotEqualTo(trie2);
        assertThat(Hex.toHexString(trie1.getRootHash()))
                .isNotEqualTo(Hex.toHexString(trie2.getRootHash()));

        // ensure equality after deletions
        trie1.delete("key for trie1");
        trie2.delete("key for trie2");

        assertThat(trie1).isEqualTo(trie2);
        assertThat(Hex.toHexString(trie1.getRootHash()))
                .isEqualTo(Hex.toHexString(trie2.getRootHash()));
        assertThat(Hex.toHexString(trie1.getRootHash())).isEqualTo(oldHash);

        // ensure inequality after one side deletion
        trie1.delete(pairs.keySet().iterator().next());

        assertThat(trie1).isNotEqualTo(trie2);
        assertThat(Hex.toHexString(trie1.getRootHash()))
                .isNotEqualTo(Hex.toHexString(trie2.getRootHash()));
        assertThat(Hex.toHexString(trie1.getRootHash())).isNotEqualTo(oldHash);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tests from EthJ
    // -----------------------------------------------------------------------------------------------------------------

    private static String LONG_STRING =
            "1234567890abcdefghijklmnopqrstuvwxxzABCEFGHIJKLMNOPQRSTUVWXYZ";
    private static String ROOT_HASH_EMPTY = Hex.toHexString(EMPTY_TRIE_HASH);

    private static String cat = "cat";

    //    @Test
    //    public void TestTrieReset() {
    //        TrieImpl trie = new TrieImpl(new MockDB("TestTrieReset"));
    //
    //        trie.update(cat, LONG_STRING);
    //        assertNotEquals("Expected cached nodes", 0, trie.getCache().getNodes().size());
    //
    //        trie.getCache().undo();
    //
    //        assertEquals("Expected no nodes after undo", 0, trie.getCache().getNodes().size());
    //    }

    @Test
    public void testDeleteCompletellyDiferentItems() {
        TrieImpl trie = new TrieImpl(null);

        String val_1 = "1000000000000000000000000000000000000000000000000000000000000000";
        String val_2 = "2000000000000000000000000000000000000000000000000000000000000000";
        String val_3 = "3000000000000000000000000000000000000000000000000000000000000000";

        trie.update(Hex.decode(val_1), Hex.decode(val_1));
        trie.update(Hex.decode(val_2), Hex.decode(val_2));

        String root1 = Hex.toHexString(trie.getRootHash());

        trie.update(Hex.decode(val_3), Hex.decode(val_3));
        trie.delete(Hex.decode(val_3));
        String root1_ = Hex.toHexString(trie.getRootHash());

        Assert.assertEquals(root1, root1_);
    }

    @Test
    public void testMasiveUpdate() {
        boolean massiveUpdateTestEnabled = false;

        if (massiveUpdateTestEnabled) {
            List<String> randomWords = Arrays.asList(randomValues);
            HashMap<String, String> testerMap = new HashMap<>();

            TrieImpl trie = new TrieImpl(null);
            Random generator = new Random();

            // Random insertion
            for (int i = 0; i < 100000; ++i) {

                int randomIndex1 = generator.nextInt(randomWords.size());
                int randomIndex2 = generator.nextInt(randomWords.size());

                String word1 = randomWords.get(randomIndex1).trim();
                String word2 = randomWords.get(randomIndex2).trim();

                trie.update(word1, word2);
                testerMap.put(word1, word2);
            }

            int half = testerMap.size() / 2;
            for (int r = 0; r < half; ++r) {

                int randomIndex = generator.nextInt(randomWords.size());
                String word1 = randomWords.get(randomIndex).trim();

                testerMap.remove(word1);
                trie.delete(word1);
            }

            // Assert the result now
            Iterator<String> keys = testerMap.keySet().iterator();
            while (keys.hasNext()) {

                String mapWord1 = keys.next();
                String mapWord2 = testerMap.get(mapWord1);
                String treeWord2 = new String(trie.get(mapWord1));

                Assert.assertEquals(mapWord2, treeWord2);
            }
        }
    }

    @Test
    public void testMassiveDelete() {
        TrieImpl trie = new TrieImpl(null);
        byte[] rootHash1 = null;
        for (int i = 0; i < 11000; i++) {
            trie.update(HashUtil.h256(intToBytes(i)), HashUtil.h256(intToBytes(i + 1000000)));
            if (i == 10000) {
                rootHash1 = trie.getRootHash();
            }
        }
        for (int i = 10001; i < 11000; i++) {
            trie.delete(HashUtil.h256(intToBytes(i)));
        }

        byte[] rootHash2 = trie.getRootHash();
        assertArrayEquals(rootHash1, rootHash2);
    }

    @Test
    public void testTrieCopy() {
        TrieImpl trie = new TrieImpl(null);
        trie.update("doe", "reindeer");
        TrieImpl trie2 = trie.copy();
        assertNotEquals(
                trie.hashCode(),
                trie2.hashCode()); // avoid possibility that its just a reference copy
        assertEquals(Hex.toHexString(trie.getRootHash()), Hex.toHexString(trie2.getRootHash()));
        assertTrue(trie.equals(trie2));
    }

    /** Trie updates taken from real blockchain use with sample accounts. */
    private Map<ByteArrayWrapper, byte[]> getSampleTrieUpdates() {
        Map<ByteArrayWrapper, byte[]> data = new HashMap<>();

        ByteArrayWrapper key;
        byte[] value;

        key =
                new ByteArrayWrapper(
                        new byte[] {
                            -96, 29, -93, -108, -55, -59, 67, -121, -3, 10, 44, -128, -127, 73, 90,
                            -26, 3, 62, 55, -60, -82, -11, -96, -54, 106, 114, 42, 70, 46, 31, 37,
                            29
                        });
        value =
                new byte[] {
                    -8, 78, -128, -118, -45, -62, 27, -50, -52, -19, -95, 0, 0, 0, -96, 69, -80,
                    -49, -62, 32, -50, -20, 91, 124, 28, 98, -60, -44, 25, 61, 56, -28, -21, -92,
                    -114, -120, 21, 114, -100, -25, 95, -100, 10, -80, -28, -63, -64, -96, 14, 87,
                    81, -64, 38, -27, 67, -78, -24, -85, 46, -80, 96, -103, -38, -95, -47, -27, -33,
                    71, 119, -113, 119, -121, -6, -85, 69, -51, -15, 47, -29, -88
                };
        data.put(key, value);

        key =
                new ByteArrayWrapper(
                        new byte[] {
                            -96, 44, -96, -3, -97, 10, 112, 111, 28, -32, 44, 18, 101, -106, 51, 6,
                            -107, 0, 24, 13, 50, 81, -84, 68, 125, 110, 118, 97, -109, -96, -30, 107
                        });
        value =
                new byte[] {
                    -8, 78, -128, -118, -45, -62, 27, -50, -52, -19, -95, 0, 0, 0, -96, 69, -80,
                    -49, -62, 32, -50, -20, 91, 124, 28, 98, -60, -44, 25, 61, 56, -28, -21, -92,
                    -114, -120, 21, 114, -100, -25, 95, -100, 10, -80, -28, -63, -64, -96, 14, 87,
                    81, -64, 38, -27, 67, -78, -24, -85, 46, -80, 96, -103, -38, -95, -47, -27, -33,
                    71, 119, -113, 119, -121, -6, -85, 69, -51, -15, 47, -29, -88
                };
        data.put(key, value);

        key =
                new ByteArrayWrapper(
                        new byte[] {
                            -96, 53, 87, -10, 21, -118, 120, -87, 69, -83, 111, 41, -26, -81, 41,
                            -82, -90, -108, -59, -19, -60, 87, -98, -88, 50, -127, 89, -11, -56,
                            114, -113, 27
                        });
        value =
                new byte[] {
                    -8, 78, -128, -118, -45, -62, 27, -50, -52, -19, -95, 0, 0, 0, -96, 69, -80,
                    -49, -62, 32, -50, -20, 91, 124, 28, 98, -60, -44, 25, 61, 56, -28, -21, -92,
                    -114, -120, 21, 114, -100, -25, 95, -100, 10, -80, -28, -63, -64, -96, 14, 87,
                    81, -64, 38, -27, 67, -78, -24, -85, 46, -80, 96, -103, -38, -95, -47, -27, -33,
                    71, 119, -113, 119, -121, -6, -85, 69, -51, -15, 47, -29, -88
                };
        data.put(key, value);

        key =
                new ByteArrayWrapper(
                        new byte[] {
                            -96, -118, 43, 113, -6, -115, 90, 79, -76, -32, -70, -7, -91, -98, 70,
                            111, 32, 97, 96, 55, -119, -89, 64, -34, -92, 94, 1, 58, -61, 63, 102,
                            -92
                        });
        value =
                new byte[] {
                    -8, 78, -128, -118, -45, -62, 27, -50, -52, -19, -95, 0, 0, 0, -96, 69, -80,
                    -49, -62, 32, -50, -20, 91, 124, 28, 98, -60, -44, 25, 61, 56, -28, -21, -92,
                    -114, -120, 21, 114, -100, -25, 95, -100, 10, -80, -28, -63, -64, -96, 14, 87,
                    81, -64, 38, -27, 67, -78, -24, -85, 46, -80, 96, -103, -38, -95, -47, -27, -33,
                    71, 119, -113, 119, -121, -6, -85, 69, -51, -15, 47, -29, -88
                };
        data.put(key, value);

        return data;
    }

    @Test
    public void testGetMissingNodes_wCompleteTrie() {
        MockDB mockDB = new MockDB("temp");
        mockDB.open();
        TrieImpl trie = new TrieImpl(mockDB);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : getSampleTrieUpdates().entrySet()) {
            trie.update(e.getKey().getData(), e.getValue());
        }
        trie.getCache().commit(true);

        byte[] root = trie.getRootHash();

        trie = new TrieImpl(mockDB);
        assertThat(trie.getMissingNodes(root)).isEmpty();
    }

    @Test
    public void testGetMissingNodes_wCompleteTrie_wStartFromValue() {
        MockDB mockDB = new MockDB("temp");
        mockDB.open();
        TrieImpl trie = new TrieImpl(mockDB);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : getSampleTrieUpdates().entrySet()) {
            trie.update(e.getKey().getData(), e.getValue());
        }
        trie.getCache().commit(true);

        byte[] root = trie.getRootHash();
        byte[] value = mockDB.get(root).get();

        trie = new TrieImpl(mockDB);
        assertThat(trie.getMissingNodes(value)).isEmpty();
    }

    @Test
    public void testGetMissingNodes_wIncompleteTrie() {
        MockDB mockDB = new MockDB("temp");
        mockDB.open();
        TrieImpl trie = new TrieImpl(mockDB);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : getSampleTrieUpdates().entrySet()) {
            trie.update(e.getKey().getData(), e.getValue());
        }
        trie.getCache().commit(true);

        byte[] root = trie.getRootHash();
        // System.out.println(trie.getTrieDump(root));

        // removing two of the nodes from the trie
        Set<ByteArrayWrapper> expected = new HashSet<>();
        expected.add(
                new ByteArrayWrapper(
                        Hex.decode(
                                "59c2a26cebd0ed50053bba185a7d13e1ae58314e2c37d46c1f7b885fd93b687a")));
        expected.add(
                new ByteArrayWrapper(
                        Hex.decode(
                                "ddf1b495a3e98e1897a9b1257d4172d59fcbe0dba23b8b87812ca2a55919d9ab")));

        for (ByteArrayWrapper key : expected) {
            mockDB.delete(key.getData());
        }

        trie = new TrieImpl(mockDB);

        Set<ByteArrayWrapper> missing = trie.getMissingNodes(root);
        assertThat(missing).hasSize(2);
        assertThat(missing).isEqualTo(expected);
    }

    @Test
    public void testGetMissingNodes_wIncompleteTrie_wStartFromValue() {
        MockDB mockDB = new MockDB("temp");
        mockDB.open();
        TrieImpl trie = new TrieImpl(mockDB);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : getSampleTrieUpdates().entrySet()) {
            trie.update(e.getKey().getData(), e.getValue());
        }
        trie.getCache().commit(true);

        byte[] root = trie.getRootHash();
        // System.out.println(trie.getTrieDump(root));
        byte[] value = mockDB.get(root).get();

        // removing two of the nodes from the trie
        Set<ByteArrayWrapper> expected = new HashSet<>();
        expected.add(
                new ByteArrayWrapper(
                        Hex.decode(
                                "59c2a26cebd0ed50053bba185a7d13e1ae58314e2c37d46c1f7b885fd93b687a")));
        expected.add(
                new ByteArrayWrapper(
                        Hex.decode(
                                "ddf1b495a3e98e1897a9b1257d4172d59fcbe0dba23b8b87812ca2a55919d9ab")));

        for (ByteArrayWrapper key : expected) {
            mockDB.delete(key.getData());
        }

        trie = new TrieImpl(mockDB);

        Set<ByteArrayWrapper> missing = trie.getMissingNodes(value);
        assertThat(missing).hasSize(2);
        assertThat(missing).isEqualTo(expected);
    }

    @Test
    public void testGetReferencedTrieNodes() {
        MockDB mockDB = new MockDB("temp");
        mockDB.open();
        TrieImpl trie = new TrieImpl(mockDB);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : getSampleTrieUpdates().entrySet()) {
            trie.update(e.getKey().getData(), e.getValue());
        }
        trie.getCache().commit(true);

        byte[] root = trie.getRootHash();
        byte[] value = mockDB.get(root).get();

        trie = new TrieImpl(mockDB);

        // empty for limit <= 0
        assertThat(trie.getReferencedTrieNodes(value, -2)).isEmpty();
        assertThat(trie.getReferencedTrieNodes(value, 0)).isEmpty();

        // partial size
        assertThat(trie.getReferencedTrieNodes(value, 3).size()).isEqualTo(3);
        assertThat(trie.getReferencedTrieNodes(value, 4).size()).isEqualTo(4);

        // full size except for initial root
        int full = trie.getTrieSize(root) - 1;
        assertThat(trie.getReferencedTrieNodes(value, full).size()).isEqualTo(full);
        assertThat(trie.getReferencedTrieNodes(value, 2 * full).size()).isEqualTo(full);
    }

    @Test
    public void testGetReferencedTrieNodes_withStartFromAllNodes() {
        MockDB mockDB = new MockDB("temp");
        mockDB.open();
        TrieImpl trie = new TrieImpl(mockDB);

        for (Map.Entry<ByteArrayWrapper, byte[]> e : getSampleTrieUpdates().entrySet()) {
            trie.update(e.getKey().getData(), e.getValue());
        }
        trie.getCache().commit(true);

        byte[] value, root = trie.getRootHash();
        Set<ByteArrayWrapper> allKeys = trie.getTrieKeys(root);
        trie = new TrieImpl(mockDB);
        Value v;

        for (ByteArrayWrapper key : allKeys) {
            value = mockDB.get(key.getData()).get();
            v = Value.fromRlpEncoded(value);

            // empty for limit <= 0
            assertThat(trie.getReferencedTrieNodes(key.getData(), -2)).isEmpty();
            assertThat(trie.getReferencedTrieNodes(value, -2)).isEmpty();
            assertThat(trie.getReferencedTrieNodes(key.getData(), 0)).isEmpty();
            assertThat(trie.getReferencedTrieNodes(value, 0)).isEmpty();

            // partial size = 1 for non-leafs and 0 for leafs
            assertThat(trie.getReferencedTrieNodes(value, 1).size()).isAtMost(1);
            assertThat(trie.getReferencedTrieNodes(key.getData(), 1).size()).isAtMost(1);

            if (v.isList() && v.asList().size() > 2) {
                // partial size = 4 for branch node
                assertThat(trie.getReferencedTrieNodes(value, 100).size()).isEqualTo(4);
                assertThat(trie.getReferencedTrieNodes(key.getData(), 100).size()).isEqualTo(5);
            } else if (v.isList()) {
                // at most whole list
                assertThat(trie.getReferencedTrieNodes(value, 100).size()).isAtMost(5);
                assertThat(trie.getReferencedTrieNodes(key.getData(), 100).size()).isAtMost(6);
            } else {
                // partial size = 0 for leafs
                assertThat(trie.getReferencedTrieNodes(value, 100).size()).isAtMost(0);
                assertThat(trie.getReferencedTrieNodes(key.getData(), 100).size()).isAtMost(0);
            }
        }
    }
}
