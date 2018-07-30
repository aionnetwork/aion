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
package org.aion.rlp;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.base.util.ByteUtil.byteArrayToInt;
import static org.aion.base.util.ByteUtil.byteArrayToLong;
import static org.aion.rlp.RLPTest.bytesToAscii;

import java.math.BigInteger;
import org.aion.base.util.Hex;
import org.junit.Test;

/**
 * Tests that the RLP implementation is compliant with the RLP specification from Ethereum <a
 * href="https://github.com/ethereum/pyrlp/blob/master/tests/rlptest.json">pyrlp rlptest.json</a>
 * and <a href="https://github.com/ethereum/tests/blob/develop/RLPTests/rlptest.json">tests
 * rlptest.json</a>.
 *
 * @author Alexandra Roatis
 */
public class RLPSpecTest {

    // ENCODING

    @Test
    public void testEncodeEmptyString() {
        String input = "";
        byte[] expected = Hex.decode("80");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeShortString1() {
        String input = "dog";
        byte[] expected = Hex.decode("83646f67");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeShortString2() {
        String input = "Lorem ipsum dolor sit amet, consectetur adipisicing eli"; // length = 55
        byte[] expected =
                Hex.decode(
                        "b74c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c69");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeLongString1() {
        String input = "Lorem ipsum dolor sit amet, consectetur adipisicing elit"; // length = 56
        byte[] expected =
                Hex.decode(
                        "b8384c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c6974");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeLongString2() {
        String input =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur mauris magna, suscipit sed vehicula non, iaculis faucibus tortor. Proin suscipit ultricies malesuada. Duis tortor elit, dictum quis tristique eu, ultrices at risus. Morbi a est imperdiet mi ullamcorper aliquet suscipit nec lorem. Aenean quis leo mollis, vulputate elit varius, consequat enim. Nulla ultrices turpis justo, et posuere urna consectetur nec. Proin non convallis metus. Donec tempor ipsum in mauris congue sollicitudin. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Suspendisse convallis sem vel massa faucibus, eget lacinia lacus tempor. Nulla quis ultricies purus. Proin auctor rhoncus nibh condimentum mollis. Aliquam consequat enim at metus luctus, a eleifend purus egestas. Curabitur at nibh metus. Nam bibendum, neque at auctor tristique, lorem libero aliquet arcu, non interdum tellus lectus sit amet eros. Cras rhoncus, metus ac ornare cursus, dolor justo ultrices metus, at ullamcorper volutpat";
        byte[] expected =
                Hex.decode(
                        "b904004c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e73656374657475722061646970697363696e6720656c69742e20437572616269747572206d6175726973206d61676e612c20737573636970697420736564207665686963756c61206e6f6e2c20696163756c697320666175636962757320746f72746f722e2050726f696e20737573636970697420756c74726963696573206d616c6573756164612e204475697320746f72746f7220656c69742c2064696374756d2071756973207472697374697175652065752c20756c7472696365732061742072697375732e204d6f72626920612065737420696d70657264696574206d6920756c6c616d636f7270657220616c6971756574207375736369706974206e6563206c6f72656d2e2041656e65616e2071756973206c656f206d6f6c6c69732c2076756c70757461746520656c6974207661726975732c20636f6e73657175617420656e696d2e204e756c6c6120756c74726963657320747572706973206a7573746f2c20657420706f73756572652075726e6120636f6e7365637465747572206e65632e2050726f696e206e6f6e20636f6e76616c6c6973206d657475732e20446f6e65632074656d706f7220697073756d20696e206d617572697320636f6e67756520736f6c6c696369747564696e2e20566573746962756c756d20616e746520697073756d207072696d697320696e206661756369627573206f726369206c756374757320657420756c74726963657320706f737565726520637562696c69612043757261653b2053757370656e646973736520636f6e76616c6c69732073656d2076656c206d617373612066617563696275732c2065676574206c6163696e6961206c616375732074656d706f722e204e756c6c61207175697320756c747269636965732070757275732e2050726f696e20617563746f722072686f6e637573206e69626820636f6e64696d656e74756d206d6f6c6c69732e20416c697175616d20636f6e73657175617420656e696d206174206d65747573206c75637475732c206120656c656966656e6420707572757320656765737461732e20437572616269747572206174206e696268206d657475732e204e616d20626962656e64756d2c206e6571756520617420617563746f72207472697374697175652c206c6f72656d206c696265726f20616c697175657420617263752c206e6f6e20696e74657264756d2074656c6c7573206c65637475732073697420616d65742065726f732e20437261732072686f6e6375732c206d65747573206163206f726e617265206375727375732c20646f6c6f72206a7573746f20756c747269636573206d657475732c20617420756c6c616d636f7270657220766f6c7574706174");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    /**
     * Asserts that the same encoding is obtained by the different encode methods.
     *
     * @param input a {@link Byte} value to be encoded
     * @param expected the expected encoding
     */
    public static void assertEncodeByte(byte input, byte[] expected) {
        // test as byte
        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeByte(input);
        assertThat(actual).isEqualTo(expected);

        // test as short and higher
        assertEncodeShort((short) input, expected);
    }

    /**
     * Asserts that the same encoding is obtained by the different encode methods.
     *
     * @param input a {@link Short} value to be encoded
     * @param expected the expected encoding
     */
    public static void assertEncodeShort(short input, byte[] expected) {
        // test as short
        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeShort(input);
        assertThat(actual).isEqualTo(expected);

        // test as int and higher
        assertEncodeInt((int) input, expected);
    }

    /**
     * Asserts that the same encoding is obtained by the different encode methods.
     *
     * @param input an {@link Integer} value to be encoded
     * @param expected the expected encoding
     */
    public static void assertEncodeInt(int input, byte[] expected) {
        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeInt(input);
        assertThat(actual).isEqualTo(expected);

        // test as long and higher
        assertEncodeLong((long) input, expected);
    }

    /**
     * Asserts that the same encoding is obtained by the different encode methods.
     *
     * @param input a {@link Long} value to be encoded
     * @param expected the expected encoding
     */
    public static void assertEncodeLong(long input, byte[] expected) {
        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeLong(input);
        assertThat(actual).isEqualTo(expected);

        // test as big integer
        assertEncodeBigInteger(BigInteger.valueOf(input), expected);
    }

    /**
     * Asserts that the same encoding is obtained by the different encode methods.
     *
     * @param input a {@link BigInteger} value to be encoded
     * @param expected the expected encoding
     */
    public static void assertEncodeBigInteger(BigInteger input, byte[] expected) {
        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeBigInteger(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeZero() {
        byte input = 0;
        byte[] expected = Hex.decode("80");

        assertEncodeByte(input, expected);
    }

    @Test
    public void testEncodeByte1() {
        byte input = 1;
        byte[] expected = Hex.decode("01");

        assertEncodeByte(input, expected);
    }

    @Test
    public void testEncodeByte2() {
        byte input = 16;
        byte[] expected = Hex.decode("10");

        assertEncodeByte(input, expected);
    }

    @Test
    public void testEncodeByte3() {
        byte input = 79;
        byte[] expected = Hex.decode("4f");

        assertEncodeByte(input, expected);
    }

    @Test
    public void testEncodeByte4() {
        byte input = 127;
        byte[] expected = Hex.decode("7f");

        assertEncodeByte(input, expected);
    }

    @Test
    public void testEncodeShort1() {
        short input = 128;
        byte[] expected = Hex.decode("8180");

        assertEncodeShort(input, expected);
    }

    @Test
    public void testEncodeShort2() {
        short input = 1000;
        byte[] expected = Hex.decode("8203e8");

        assertEncodeShort(input, expected);
    }

    @Test
    public void testEncodeInt1() {
        int input = 100000;
        byte[] expected = Hex.decode("830186a0");

        assertEncodeInt(input, expected);
    }

    @Test
    public void testEncodeBigInt1() {
        BigInteger input = new BigInteger("83729609699884896815286331701780722", 10);
        byte[] expected = Hex.decode("8f102030405060708090a0b0c0d0e0f2");

        assertEncodeBigInteger(input, expected);
    }

    @Test
    public void testEncodeBigInt2() {
        BigInteger input =
                new BigInteger(
                        "105315505618206987246253880190783558935785933862974822347068935681", 10);
        byte[] expected = Hex.decode("9c0100020003000400050006000700080009000a000b000c000d000e01");

        assertEncodeBigInteger(input, expected);
    }

    @Test
    public void testEncodeBigInt3() {
        BigInteger input =
                new BigInteger(
                        "115792089237316195423570985008687907853269984665640564039457584007913129639936",
                        10);
        byte[] expected =
                Hex.decode("a1010000000000000000000000000000000000000000000000000000000000000000");

        assertEncodeBigInteger(input, expected);
    }

    @Test
    public void testEncodeEmptyList() {
        Object[] input = new Object[0];
        byte[] expected = Hex.decode("c0");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeStringList() {
        String[] input = new String[] {"dog", "god", "cat"};
        byte[] expected = Hex.decode("cc83646f6783676f6483636174");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeMultiList() {
        // input: [ "zw", [ 4 ], 1 ]
        Object[] input = new Object[] {"zw", new Object[] {4}, 1};
        byte[] expected = Hex.decode("c6827a77c10401");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeMaxShortList() {
        String[] input =
                new String[] {
                    "asdf", "qwer", "zxcv", "asdf", "qwer", "zxcv", "asdf", "qwer", "zxcv", "asdf",
                    "qwer"
                };
        byte[] expected =
                Hex.decode(
                        "f784617364668471776572847a78637684617364668471776572847a78637684617364668471776572847a78637684617364668471776572");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeLongList1() {
        Object[] input =
                new Object[] {
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"}
                };
        byte[] expected =
                Hex.decode(
                        "f840cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeLongList2() {
        Object[] input =
                new Object[] {
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"}
                };
        byte[] expected =
                Hex.decode(
                        "f90200cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeListOfLists1() {
        // input: [ [ [], [] ], [] ]
        Object[] input = new Object[] {new Object[] {new Object[0], new Object[0]}, new Object[0]};
        byte[] expected = Hex.decode("c4c2c0c0c0");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeListOfLists2() {
        // input: [ [], [[]], [ [], [[]] ] ]
        Object[] input =
                new Object[] {
                    new Object[0], // first item
                    new Object[] {new Object[0]}, // second item
                    new Object[] {new Object[0], new Object[] {new Object[0]}}
                }; // third item
        byte[] expected = Hex.decode("c7c0c1c0c3c0c1c0");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeDictList() {
        Object[] input =
                new Object[] {
                    new String[] {"key1", "val1"},
                    new String[] {"key2", "val2"},
                    new String[] {"key3", "val3"},
                    new String[] {"key4", "val4"}
                };
        byte[] expected =
                Hex.decode(
                        "ecca846b6579318476616c31ca846b6579328476616c32ca846b6579338476616c33ca846b6579348476616c34");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeByteString1() {
        String input = "\u0000";
        byte[] expected = Hex.decode("00");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeByteString2() {
        String input = "\u0001";
        byte[] expected = Hex.decode("01");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testEncodeByteString3() {
        String input = "\u007F";
        byte[] expected = Hex.decode("7f");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);
    }

    // DECODING

    @Test
    public void testDecodeEmptyString() {
        byte[] input = Hex.decode("80");
        String expected = "";

        String actual = (String) RLP.decode(input, 0).getDecoded();
        assertThat(actual).isEqualTo(expected);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData().length).isEqualTo(0);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeShortString1() {
        byte[] input = Hex.decode("83646f67");
        String expected = "dog";

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(bytesToAscii(actual)).isEqualTo(expected);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeShortString2() {
        byte[] input =
                Hex.decode(
                        "b74c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c69");
        String expected = "Lorem ipsum dolor sit amet, consectetur adipisicing eli"; // length = 55

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(bytesToAscii(actual)).isEqualTo(expected);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeLongString1() {
        byte[] input =
                Hex.decode(
                        "b8384c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e7365637465747572206164697069736963696e6720656c6974");
        String expected = "Lorem ipsum dolor sit amet, consectetur adipisicing elit"; // length = 56

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(bytesToAscii(actual)).isEqualTo(expected);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeLongString2() {
        byte[] input =
                Hex.decode(
                        "b904004c6f72656d20697073756d20646f6c6f722073697420616d65742c20636f6e73656374657475722061646970697363696e6720656c69742e20437572616269747572206d6175726973206d61676e612c20737573636970697420736564207665686963756c61206e6f6e2c20696163756c697320666175636962757320746f72746f722e2050726f696e20737573636970697420756c74726963696573206d616c6573756164612e204475697320746f72746f7220656c69742c2064696374756d2071756973207472697374697175652065752c20756c7472696365732061742072697375732e204d6f72626920612065737420696d70657264696574206d6920756c6c616d636f7270657220616c6971756574207375736369706974206e6563206c6f72656d2e2041656e65616e2071756973206c656f206d6f6c6c69732c2076756c70757461746520656c6974207661726975732c20636f6e73657175617420656e696d2e204e756c6c6120756c74726963657320747572706973206a7573746f2c20657420706f73756572652075726e6120636f6e7365637465747572206e65632e2050726f696e206e6f6e20636f6e76616c6c6973206d657475732e20446f6e65632074656d706f7220697073756d20696e206d617572697320636f6e67756520736f6c6c696369747564696e2e20566573746962756c756d20616e746520697073756d207072696d697320696e206661756369627573206f726369206c756374757320657420756c74726963657320706f737565726520637562696c69612043757261653b2053757370656e646973736520636f6e76616c6c69732073656d2076656c206d617373612066617563696275732c2065676574206c6163696e6961206c616375732074656d706f722e204e756c6c61207175697320756c747269636965732070757275732e2050726f696e20617563746f722072686f6e637573206e69626820636f6e64696d656e74756d206d6f6c6c69732e20416c697175616d20636f6e73657175617420656e696d206174206d65747573206c75637475732c206120656c656966656e6420707572757320656765737461732e20437572616269747572206174206e696268206d657475732e204e616d20626962656e64756d2c206e6571756520617420617563746f72207472697374697175652c206c6f72656d206c696265726f20616c697175657420617263752c206e6f6e20696e74657264756d2074656c6c7573206c65637475732073697420616d65742065726f732e20437261732072686f6e6375732c206d65747573206163206f726e617265206375727375732c20646f6c6f72206a7573746f20756c747269636573206d657475732c20617420756c6c616d636f7270657220766f6c7574706174");
        String expected =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur mauris magna, suscipit sed vehicula non, iaculis faucibus tortor. Proin suscipit ultricies malesuada. Duis tortor elit, dictum quis tristique eu, ultrices at risus. Morbi a est imperdiet mi ullamcorper aliquet suscipit nec lorem. Aenean quis leo mollis, vulputate elit varius, consequat enim. Nulla ultrices turpis justo, et posuere urna consectetur nec. Proin non convallis metus. Donec tempor ipsum in mauris congue sollicitudin. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Suspendisse convallis sem vel massa faucibus, eget lacinia lacus tempor. Nulla quis ultricies purus. Proin auctor rhoncus nibh condimentum mollis. Aliquam consequat enim at metus luctus, a eleifend purus egestas. Curabitur at nibh metus. Nam bibendum, neque at auctor tristique, lorem libero aliquet arcu, non interdum tellus lectus sit amet eros. Cras rhoncus, metus ac ornare cursus, dolor justo ultrices metus, at ullamcorper volutpat";

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(bytesToAscii(actual)).isEqualTo(expected);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    /**
     * Asserts that the same decoding is obtained by the different encode methods.
     *
     * @param input the given encoding
     * @param expected the expected {@link Integer} value
     */
    public static void assertDecodeInt(byte[] input, int expected) {
        // test as int
        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(byteArrayToInt(actual)).isEqualTo(expected);
        assertThat(RLP.decodeInt(input, 0)).isEqualTo(expected);

        // test as long and upper
        assertDecodeLong(input, (long) expected);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    /**
     * Asserts that the same decoding is obtained by the different encode methods.
     *
     * @param input the given encoding
     * @param expected the expected {@link Long} value
     */
    public static void assertDecodeLong(byte[] input, long expected) {
        // test as long
        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(byteArrayToLong(actual)).isEqualTo(expected);

        // test as big integer
        assertDecodeBigInteger(input, BigInteger.valueOf(expected));

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    /**
     * Asserts that the same decoding is obtained by the different encode methods.
     *
     * @param input the given encoding
     * @param expected the expected {@link BigInteger} value
     */
    public static void assertDecodeBigInteger(byte[] input, BigInteger expected) {
        // test as big integer
        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(new BigInteger(1, actual)).isEqualTo(expected);
        assertThat(RLP.decodeBigInteger(input, 0)).isEqualTo(expected);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeZero1() {
        byte[] input = Hex.decode("80");

        // expected = "" with the general method
        String actual = (String) RLP.decode(input, 0).getDecoded();
        assertThat(actual).isEqualTo("");

        // expected = 0 with decodeInt & decodeLongInt
        assertThat(RLP.decodeInt(input, 0)).isEqualTo(0);
        assertThat(RLP.decodeBigInteger(input, 0)).isEqualTo(BigInteger.ZERO);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData().length).isEqualTo(0);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeZero2() {
        byte[] input = Hex.decode("00");
        int expected = 0;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeByte1() {
        byte[] input = Hex.decode("01");
        int expected = 1;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeByte2() {
        byte[] input = Hex.decode("10");
        int expected = 16;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeByte3() {
        byte[] input = Hex.decode("4f");
        int expected = 79;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeByte4() {
        byte[] input = Hex.decode("7f");
        int expected = 127;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeShort1() {
        byte[] input = Hex.decode("8180");
        int expected = 128;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeShort2() {
        byte[] input = Hex.decode("8203e8");
        int expected = 1000;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeInt1() {
        byte[] input = Hex.decode("830186a0");
        int expected = 100000;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeBigInt1() {
        byte[] input = Hex.decode("8f102030405060708090a0b0c0d0e0f2");
        BigInteger expected = new BigInteger("83729609699884896815286331701780722", 10);

        assertDecodeBigInteger(input, expected);
    }

    @Test
    public void testDecodeBigInt2() {
        byte[] input = Hex.decode("9c0100020003000400050006000700080009000a000b000c000d000e01");
        BigInteger expected =
                new BigInteger(
                        "105315505618206987246253880190783558935785933862974822347068935681", 10);

        assertDecodeBigInteger(input, expected);
    }

    @Test
    public void testDecodeBigInt3() {
        byte[] input =
                Hex.decode("a1010000000000000000000000000000000000000000000000000000000000000000");
        BigInteger expected =
                new BigInteger(
                        "115792089237316195423570985008687907853269984665640564039457584007913129639936",
                        10);

        assertDecodeBigInteger(input, expected);
    }

    @Test
    public void testDecodeEmptyList() {
        byte[] input = Hex.decode("c0");

        Object[] decodeResult = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(decodeResult.length == 0);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;

        assertThat(elmList.size()).isEqualTo(0);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeStringList() {
        byte[] input = Hex.decode("cc83646f6783676f6483636174");
        String[] expected = new String[] {"dog", "god", "cat"};

        Object[] actual = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {
            assertThat(bytesToAscii((byte[]) actual[i])).isEqualTo(expected[i]);
        }

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;

        assertThat(elmList.size()).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {
            assertThat(bytesToAscii(elmList.get(i).getRLPData())).isEqualTo(expected[i]);
        }

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeMultiList() {
        byte[] input = Hex.decode("c6827a77c10401");
        // expected: [ "zw", [ 4 ], 1 ]
        Object[] expected = new Object[] {"zw", new Object[] {4}, 1};

        Object[] actual = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);

        // first item
        assertThat(bytesToAscii((byte[]) actual[0])).isEqualTo(expected[0]);

        // second item
        assertThat(actual[1] instanceof Object[]).isTrue();
        Object[] expectedList = (Object[]) expected[1];
        Object[] actualList = (Object[]) actual[1];
        assertThat(actualList.length).isEqualTo(expectedList.length);
        assertThat(byteArrayToInt((byte[]) actualList[0])).isEqualTo(expectedList[0]);

        // third item
        assertThat(byteArrayToInt((byte[]) actual[2])).isEqualTo(expected[2]);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;
        assertThat(elmList.size()).isEqualTo(expected.length);

        // first item
        assertThat(bytesToAscii(elmList.get(0).getRLPData())).isEqualTo(expected[0]);

        // second item
        assertThat(elmList.get(1) instanceof RLPList).isTrue();
        RLPList actualList2 = (RLPList) elmList.get(1);
        assertThat(actualList2.size()).isEqualTo(expectedList.length);
        assertThat(byteArrayToInt(actualList2.get(0).getRLPData())).isEqualTo(expectedList[0]);

        // third item
        assertThat(byteArrayToInt(elmList.get(2).getRLPData())).isEqualTo(expected[2]);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeMaxShortList() {
        byte[] input =
                Hex.decode(
                        "f784617364668471776572847a78637684617364668471776572847a78637684617364668471776572847a78637684617364668471776572");
        String[] expected =
                new String[] {
                    "asdf", "qwer", "zxcv", "asdf", "qwer", "zxcv", "asdf", "qwer", "zxcv", "asdf",
                    "qwer"
                };

        Object[] actual = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {
            assertThat(bytesToAscii((byte[]) actual[i])).isEqualTo(expected[i]);
        }

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;

        assertThat(elmList.size()).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {
            assertThat(bytesToAscii(elmList.get(i).getRLPData())).isEqualTo(expected[i]);
        }

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeLongList1() {
        byte[] input =
                Hex.decode(
                        "f840cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376");
        Object[] expected =
                new Object[] {
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"}
                };

        Object[] actual = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {
            assertThat(actual[i] instanceof Object[]).isTrue();

            Object[] expectedList = (Object[]) expected[i];
            Object[] actualList = (Object[]) actual[i];

            assertThat(actualList.length).isEqualTo(expectedList.length);

            for (int j = 0; j < expectedList.length; j++) {
                assertThat(bytesToAscii((byte[]) actualList[j])).isEqualTo(expectedList[j]);
            }
        }

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;
        assertThat(elmList.size()).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {

            assertThat(elmList.get(i) instanceof RLPList).isTrue();

            Object[] expectedList = (Object[]) expected[i];
            RLPList actualList = (RLPList) elmList.get(i);

            assertThat(actualList.size()).isEqualTo(expectedList.length);

            for (int j = 0; j < expectedList.length; j++) {
                assertThat(bytesToAscii(actualList.get(j).getRLPData())).isEqualTo(expectedList[j]);
            }
        }

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeLongList2() {
        byte[] input =
                Hex.decode(
                        "f90200cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376cf84617364668471776572847a786376");
        Object[] expected =
                new Object[] {
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"},
                    new String[] {"asdf", "qwer", "zxcv"}
                };

        Object[] actual = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {
            assertThat(actual[i] instanceof Object[]).isTrue();

            Object[] expectedList = (Object[]) expected[i];
            Object[] actualList = (Object[]) actual[i];

            assertThat(actualList.length).isEqualTo(expectedList.length);

            for (int j = 0; j < expectedList.length; j++) {
                assertThat(bytesToAscii((byte[]) actualList[j])).isEqualTo(expectedList[j]);
            }
        }

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;
        assertThat(elmList.size()).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {

            assertThat(elmList.get(i) instanceof RLPList).isTrue();

            Object[] expectedList = (Object[]) expected[i];
            RLPList actualList = (RLPList) elmList.get(i);

            assertThat(actualList.size()).isEqualTo(expectedList.length);

            for (int j = 0; j < expectedList.length; j++) {
                assertThat(bytesToAscii(actualList.get(j).getRLPData())).isEqualTo(expectedList[j]);
            }
        }

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeListOfLists1() {
        byte[] input = Hex.decode("c4c2c0c0c0");
        // expected: [ [ [], [] ], [] ]
        Object[] expected =
                new Object[] {new Object[] {new Object[0], new Object[0]}, new Object[0]};

        Object[] actual = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);

        // check first item
        assertThat(actual[0] instanceof Object[]).isTrue();

        Object[] expectedList = (Object[]) expected[0];
        Object[] actualList = (Object[]) actual[0];

        assertThat(actualList.length).isEqualTo(expectedList.length);

        for (int j = 0; j < expectedList.length; j++) {
            assertThat(actualList[j] instanceof Object[]).isTrue();
            assertThat(((Object[]) actualList[j]).length).isEqualTo(0);
        }

        // check second item
        assertThat(actual[1] instanceof Object[]).isTrue();

        actualList = (Object[]) actual[1];
        assertThat(actualList.length).isEqualTo(0);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;
        assertThat(elmList.size()).isEqualTo(expected.length);

        // check first item
        assertThat(elmList.get(0) instanceof RLPList).isTrue();
        RLPList actualRlpList = (RLPList) elmList.get(0);

        assertThat(actualRlpList.size()).isEqualTo(expectedList.length);

        for (int j = 0; j < expectedList.length; j++) {
            assertThat(actualRlpList.get(j) instanceof RLPList).isTrue();
            assertThat(((RLPList) actualRlpList.get(j)).size()).isEqualTo(0);
        }

        // check second item
        assertThat(elmList.get(1) instanceof RLPList).isTrue();

        actualRlpList = (RLPList) elmList.get(1);
        assertThat(actualRlpList.size()).isEqualTo(0);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeListOfLists2() {
        byte[] input = Hex.decode("c7c0c1c0c3c0c1c0");
        // expected: [ [], [[]], [ [], [[]] ] ]
        Object[] expected =
                new Object[] {
                    new Object[0], // first item
                    new Object[] {new Object[0]}, // second item
                    new Object[] {new Object[0], new Object[] {new Object[0]}}
                }; // third item

        Object[] actual = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);

        // check first item
        assertThat(actual[0] instanceof Object[]).isTrue();

        Object[] actualList = (Object[]) actual[0];
        assertThat(actualList.length).isEqualTo(0);

        // check second item
        assertThat(actual[1] instanceof Object[]).isTrue();

        Object[] expectedList = (Object[]) expected[1];
        actualList = (Object[]) actual[1];

        assertThat(actualList.length).isEqualTo(expectedList.length);

        // check second item -> sub-item
        assertThat(actualList[0] instanceof Object[]).isTrue();

        actualList = (Object[]) actualList[0];
        assertThat(actualList.length).isEqualTo(0);

        // check third item
        assertThat(actual[2] instanceof Object[]).isTrue();

        expectedList = (Object[]) expected[2];
        actualList = (Object[]) actual[2];

        assertThat(actualList.length).isEqualTo(expectedList.length);

        // check third item -> first sub-item
        assertThat(actualList[0] instanceof Object[]).isTrue();

        assertThat(((Object[]) actualList[0]).length).isEqualTo(0);

        // check third item -> second sub-item
        assertThat(actualList[1] instanceof Object[]).isTrue();

        actualList = (Object[]) actualList[1];
        assertThat(actualList.length).isEqualTo(1);

        assertThat(actualList[0] instanceof Object[]).isTrue();

        actualList = (Object[]) actualList[0];
        assertThat(actualList.length).isEqualTo(0);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;
        assertThat(elmList.size()).isEqualTo(expected.length);

        // check first item
        assertThat(elmList.get(0) instanceof RLPList).isTrue();

        RLPList actualList2 = (RLPList) elmList.get(0);
        assertThat(actualList2.size()).isEqualTo(0);

        // check second item
        assertThat(elmList.get(1) instanceof RLPList).isTrue();

        expectedList = (Object[]) expected[1];
        actualList2 = (RLPList) elmList.get(1);

        assertThat(actualList2.size()).isEqualTo(expectedList.length);

        // check second item -> sub-item
        assertThat(actualList2.get(0) instanceof RLPList).isTrue();

        actualList2 = (RLPList) actualList2.get(0);
        assertThat(actualList2.size()).isEqualTo(0);

        // check third item
        assertThat(elmList.get(2) instanceof RLPList).isTrue();

        expectedList = (Object[]) expected[2];
        actualList2 = (RLPList) elmList.get(2);

        assertThat(actualList2.size()).isEqualTo(expectedList.length);

        // check third item -> first sub-item
        assertThat(actualList2.get(0) instanceof RLPList).isTrue();

        assertThat(((RLPList) actualList2.get(0)).size()).isEqualTo(0);

        // check third item -> second sub-item
        assertThat(actualList2.get(1) instanceof RLPList).isTrue();

        actualList2 = (RLPList) actualList2.get(1);
        assertThat(actualList2.size()).isEqualTo(1);

        assertThat(actualList2.get(0) instanceof RLPList).isTrue();

        actualList2 = (RLPList) actualList2.get(0);
        assertThat(actualList2.size()).isEqualTo(0);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeDictList() {
        byte[] input =
                Hex.decode(
                        "ecca846b6579318476616c31ca846b6579328476616c32ca846b6579338476616c33ca846b6579348476616c34");
        Object[] expected =
                new Object[] {
                    new String[] {"key1", "val1"},
                    new String[] {"key2", "val2"},
                    new String[] {"key3", "val3"},
                    new String[] {"key4", "val4"}
                };

        Object[] actual = (Object[]) RLP.decode(input, 0).getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {
            assertThat(actual[i] instanceof Object[]).isTrue();

            Object[] expectedList = (Object[]) expected[i];
            Object[] actualList = (Object[]) actual[i];

            assertThat(actualList.length).isEqualTo(expectedList.length);

            for (int j = 0; j < expectedList.length; j++) {
                assertThat(bytesToAscii((byte[]) actualList[j])).isEqualTo(expectedList[j]);
            }
        }

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPElement element = list.get(0);
        assertThat(element instanceof RLPList).isTrue();

        RLPList elmList = (RLPList) element;
        assertThat(elmList.size()).isEqualTo(expected.length);

        for (int i = 0; i < expected.length; i++) {

            assertThat(elmList.get(i) instanceof RLPList).isTrue();

            Object[] expectedList = (Object[]) expected[i];
            RLPList actualList = (RLPList) elmList.get(i);

            assertThat(actualList.size()).isEqualTo(expectedList.length);

            for (int j = 0; j < expectedList.length; j++) {
                assertThat(bytesToAscii(actualList.get(j).getRLPData())).isEqualTo(expectedList[j]);
            }
        }

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeByteString1() {
        byte[] input = Hex.decode("00");
        String expected = "\u0000";

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(bytesToAscii(actual)).isEqualTo(expected);
        assertThat(byteArrayToInt(actual)).isEqualTo(0);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeByteString2() {
        byte[] input = Hex.decode("01");
        String expected = "\u0001";

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(bytesToAscii(actual)).isEqualTo(expected);
        assertThat(byteArrayToInt(actual)).isEqualTo(1);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }

    @Test
    public void testDecodeByteString3() {
        byte[] input = Hex.decode("7f");
        String expected = "\u007F";

        byte[] actual = (byte[]) RLP.decode(input, 0).getDecoded();
        assertThat(bytesToAscii(actual)).isEqualTo(expected);
        assertThat(byteArrayToInt(actual)).isEqualTo(127);

        // check decode2
        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        System.out.println();
    }
}
