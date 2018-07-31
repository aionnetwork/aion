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
import static org.aion.rlp.RLPSpecTest.assertDecodeBigInteger;
import static org.aion.rlp.RLPSpecTest.assertDecodeInt;
import static org.aion.rlp.RLPSpecTest.assertDecodeLong;
import static org.aion.rlp.RLPSpecTest.assertEncodeBigInteger;
import static org.aion.rlp.RLPSpecTest.assertEncodeInt;
import static org.aion.rlp.RLPSpecTest.assertEncodeLong;
import static org.aion.rlp.RLPSpecTest.assertEncodeShort;
import static org.aion.rlp.Utils.asUnsignedByteArray;

import java.math.BigInteger;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.junit.Test;

/**
 * Tests for the RLP implementation that are complementary to RLP specification tests from Ethereum
 * implemented in {@link RLPSpecTest}.
 *
 * @author Alexandra Roatis
 */
public class RLPSpecExtraTest {

    // ENCODING

    @Test
    public void testEncodeShort3() {
        short input = 32767;
        byte[] expected = Hex.decode("827fff");

        assertEncodeShort(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("82"));
    }

    @Test
    public void testEncodeShort4() {
        short input = 120;
        byte[] expected = Hex.decode("78");

        assertEncodeShort(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(0);
    }

    @Test
    public void testEncodeShort5() {
        short input = 30303;
        byte[] expected = Hex.decode("82765f");

        assertEncodeShort(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("82"));
    }

    @Test
    public void testEncodeShort6() {
        short input = 20202;
        byte[] expected = Hex.decode("824eea");

        assertEncodeShort(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("82"));
    }

    @Test
    public void testEncodeInt2() {
        int input = 32768;
        byte[] expected = Hex.decode("828000");

        assertEncodeInt(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("82"));
    }

    @Test
    public void testEncodeInt3() {
        int input = 2147483647;
        byte[] expected = Hex.decode("847fffffff");

        assertEncodeInt(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("84"));
    }

    @Test
    public void testEncodeLong1() {
        long input = 2147483648L;
        byte[] expected = Hex.decode("8480000000");

        assertEncodeLong(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("84"));
    }

    @Test
    public void testEncodeLong2() {
        long input = 4294967295L;
        byte[] expected = Hex.decode("84ffffffff");

        assertEncodeLong(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("84"));
    }

    @Test
    public void testEncodeLong3() {
        long input = 4294967296L;
        byte[] expected = Hex.decode("850100000000");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeLong(input);
        assertThat(actual).isEqualTo(Hex.decode("880000000100000000"));

        BigInteger inputBI = BigInteger.valueOf(input);

        actual = RLP.encode(inputBI);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeBigInteger(inputBI);
        assertThat(actual).isEqualTo(expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("85"));
    }

    @Test
    public void testEncodeLong4() {
        long input = 4295000060L;
        byte[] expected = Hex.decode("850100007ffc");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeLong(input);
        assertThat(actual).isEqualTo(Hex.decode("880000000100007ffc"));

        BigInteger inputBI = BigInteger.valueOf(input);

        actual = RLP.encode(inputBI);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeBigInteger(inputBI);
        assertThat(actual).isEqualTo(expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("85"));
    }

    @Test
    public void testEncodeLong5() {
        long input = 72057594037927935L;
        byte[] expected = Hex.decode("87ffffffffffffff");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeLong(input);
        assertThat(actual).isEqualTo(Hex.decode("8800ffffffffffffff"));

        BigInteger inputBI = BigInteger.valueOf(input);

        actual = RLP.encode(inputBI);
        assertThat(actual).isEqualTo(expected);

        actual = RLP.encodeBigInteger(inputBI);
        assertThat(actual).isEqualTo(expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("87"));
    }

    @Test
    public void testEncodeLong6() {
        long input = 72057594037927936L;
        byte[] expected = Hex.decode("880100000000000000");

        assertEncodeLong(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("88"));
    }

    @Test
    public void testEncodeLong7() {
        long input = 9223372036854775807L;
        byte[] expected = Hex.decode("887fffffffffffffff");

        assertEncodeLong(input, expected);

        byte[] inputAsBytes =
                input == 0
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(BigInteger.valueOf(input));
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("88"));
    }

    @Test
    public void testEncodeBigInt4() {
        BigInteger input = new BigInteger("9223372036854775808", 10);
        byte[] expected = Hex.decode("888000000000000000");

        assertEncodeBigInteger(input, expected);

        byte[] inputAsBytes =
                input.equals(BigInteger.ZERO)
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(input);
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("88"));
    }

    @Test
    public void testEncodeBigInt5() {
        BigInteger input =
                new BigInteger(
                        "8069310865484966410942242126479002031594628301973179630276682693877502501814741239079205017247892462230036091241820433244370452243766979557247714691108723",
                        10);
        byte[] expected =
                Hex.decode(
                        "b8409a11f84bc53681cc0a2982765fb840d8d60c2580fa795cfc0313efdeba869d2194e79e7cb2b522f782ffa0392cbbab8d1bac301208b137e0de4998334f3bcf73");

        assertEncodeBigInteger(input, expected);

        byte[] inputAsBytes =
                input.equals(BigInteger.ZERO)
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(input);
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(2);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("b840"));
    }

    @Test
    public void testEncodeBigInt6() {
        BigInteger input =
                new BigInteger(
                        "9650128800487972697726795438087510101805200020100629942070155319087371611597658887860952245483247188023303607186148645071838189546969115967896446355306572",
                        10);
        byte[] expected =
                Hex.decode(
                        "b840b840d8d60c2580fa795cfc0313efdeba869d2194e79e7cb2b522f782ffa0392cbbab8d1bac301208b137e0de4998334f3bcf73fa117ef213f87417089feaf84c");

        assertEncodeBigInteger(input, expected);

        byte[] inputAsBytes =
                input.equals(BigInteger.ZERO)
                        ? ByteUtil.EMPTY_BYTE_ARRAY
                        : asUnsignedByteArray(input);
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(2);

        assertThat(RLP.encodeLongElementHeader(inputAsBytes.length)).isEqualTo(Hex.decode("b840"));
    }

    @Test
    public void testEncodeByteArray1() {
        byte[] input = new byte[] {1};
        byte[] expected = Hex.decode("01");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        assertThat(RLP.encodeElement(input)).isEqualTo(expected);

        // as value
        actual = RLP.encode(new Value(input));
        assertThat(actual).isEqualTo(expected);

        assertThat(RLP.calcElementPrefixSize(input)).isEqualTo(0);
    }

    @Test
    public void testEncodeByteArray2() {
        byte[] input = new byte[] {1, 2, 3};
        byte[] expected = Hex.decode("83010203");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        assertThat(RLP.encodeElement(input)).isEqualTo(expected);

        // as value
        actual = RLP.encode(new Value(input));
        assertThat(actual).isEqualTo(expected);

        assertThat(RLP.calcElementPrefixSize(input)).isEqualTo(1);

        assertThat(RLP.encodeLongElementHeader(input.length)).isEqualTo(Hex.decode("83"));
    }

    @Test
    public void testEncodeByteList1() {
        Object[] input = new Object[] {(byte) 1};
        byte[] expected = Hex.decode("c101");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        assertThat(RLP.encodeList(RLP.encode((byte) 1))).isEqualTo(expected);

        // as value
        actual = RLP.encode(new Value(input));
        assertThat(actual).isEqualTo(expected);

        byte[] inputAsBytes = new Value(input).getData();
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);
    }

    @Test
    public void testEncodeByteList2() {
        Object[] input = new Object[] {(byte) 1, (byte) 2, (byte) 3};
        byte[] expected = Hex.decode("c3010203");

        byte[] actual = RLP.encode(input);
        assertThat(actual).isEqualTo(expected);

        assertThat(RLP.encodeList(RLP.encode((byte) 1), RLP.encode((byte) 2), RLP.encode((byte) 3)))
                .isEqualTo(expected);

        // as value
        actual = RLP.encode(new Value(input));
        assertThat(actual).isEqualTo(expected);

        byte[] inputAsBytes = new Value(input).getData();
        assertThat(RLP.calcElementPrefixSize(inputAsBytes)).isEqualTo(1);
    }

    @Test(expected = RuntimeException.class)
    public void testEncodeObject_wException() {
        Object[] input = new Object[] {'a'};

        RLP.encode(input);
    }

    @Test
    public void encodeLongElementHeader_wZeroLength() {
        assertThat(RLP.encodeLongElementHeader(0)).isEqualTo(Hex.decode("80"));
    }

    // DECODING

    @Test
    public void testDecodeShort3() {
        byte[] input = Hex.decode("827fff");
        short expected = 32767;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeShort4() {
        byte[] input = Hex.decode("78");
        short expected = 120;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeShort5() {
        byte[] input = Hex.decode("82765f");
        short expected = 30303;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeShort6() {
        byte[] input = Hex.decode("824eea");
        short expected = 20202;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeInt2() {
        byte[] input = Hex.decode("828000");
        int expected = 32768;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeInt3() {
        byte[] input = Hex.decode("847fffffff");
        int expected = 2147483647;

        assertDecodeInt(input, expected);
    }

    @Test
    public void testDecodeLong1() {
        byte[] input = Hex.decode("8480000000");
        long expected = 2147483648L;

        assertDecodeLong(input, expected);
    }

    @Test
    public void testDecodeLong2() {
        byte[] input = Hex.decode("84ffffffff");
        long expected = 4294967295L;

        assertDecodeLong(input, expected);
    }

    @Test
    public void testDecodeLong3() {
        byte[] input = Hex.decode("850100000000");
        long expected = 4294967296L;

        assertDecodeLong(input, expected);

        input = Hex.decode("880000000100000000");
        assertDecodeLong(input, expected);
    }

    @Test
    public void testDecodeLong4() {
        byte[] input = Hex.decode("850100007ffc");
        long expected = 4295000060L;

        assertDecodeLong(input, expected);

        input = Hex.decode("880000000100007ffc");

        assertDecodeLong(input, expected);
    }

    @Test
    public void testDecodeLong5() {
        byte[] input = Hex.decode("87ffffffffffffff");
        long expected = 72057594037927935L;

        assertDecodeLong(input, expected);

        input = Hex.decode("8800ffffffffffffff");

        assertDecodeLong(input, expected);
    }

    @Test
    public void testDecodeLong6() {
        byte[] input = Hex.decode("880100000000000000");
        long expected = 72057594037927936L;

        assertDecodeLong(input, expected);
    }

    @Test
    public void testDecodeLong7() {
        byte[] input = Hex.decode("887fffffffffffffff");
        long expected = 9223372036854775807L;

        assertDecodeLong(input, expected);
    }

    @Test
    public void testDecodeBigInt4() {
        byte[] input = Hex.decode("888000000000000000");
        BigInteger expected = new BigInteger("9223372036854775808", 10);

        assertDecodeBigInteger(input, expected);
    }

    @Test
    public void testDecodeBigInt5() {
        byte[] input =
                Hex.decode(
                        "b8409a11f84bc53681cc0a2982765fb840d8d60c2580fa795cfc0313efdeba869d2194e79e7cb2b522f782ffa0392cbbab8d1bac301208b137e0de4998334f3bcf73");
        BigInteger expected =
                new BigInteger(
                        "8069310865484966410942242126479002031594628301973179630276682693877502501814741239079205017247892462230036091241820433244370452243766979557247714691108723",
                        10);

        assertDecodeBigInteger(input, expected);
    }

    @Test
    public void testDecodeBigInt6() {
        byte[] input =
                Hex.decode(
                        "b840b840d8d60c2580fa795cfc0313efdeba869d2194e79e7cb2b522f782ffa0392cbbab8d1bac301208b137e0de4998334f3bcf73fa117ef213f87417089feaf84c");
        BigInteger expected =
                new BigInteger(
                        "9650128800487972697726795438087510101805200020100629942070155319087371611597658887860952245483247188023303607186148645071838189546969115967896446355306572",
                        10);

        assertDecodeBigInteger(input, expected);
    }

    @Test
    public void testDecodeByteArray1() {
        byte[] input = Hex.decode("01");
        byte[] expected = new byte[] {1};

        DecodeResult result = RLP.decode(input, 0);

        byte[] actual = (byte[]) result.getDecoded();
        assertThat(actual).isEqualTo(expected);

        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        assertThat(result.toString()).isEqualTo(ByteUtil.toHexString(list.get(0).getRLPData()));
        System.out.println();

        RLPElement elm = RLP.decode2OneItem(input, 0);
        assertThat(elm.getRLPData()).isEqualTo(new byte[] {1});
    }

    @Test
    public void testDecodeByteArray2() {
        byte[] input = Hex.decode("83010203");
        byte[] expected = new byte[] {1, 2, 3};

        DecodeResult result = RLP.decode(input, 0);

        byte[] actual = (byte[]) result.getDecoded();
        assertThat(actual).isEqualTo(expected);

        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(actual);

        RLPList.recursivePrint(list);
        assertThat(result.toString()).isEqualTo(ByteUtil.toHexString(list.get(0).getRLPData()));
        System.out.println();

        RLPElement elm = RLP.decode2OneItem(input, 1);
        assertThat(elm.getRLPData()).isEqualTo(new byte[] {1});

        elm = RLP.decode2OneItem(input, 2);
        assertThat(elm.getRLPData()).isEqualTo(new byte[] {2});

        elm = RLP.decode2OneItem(input, 3);
        assertThat(elm.getRLPData()).isEqualTo(new byte[] {3});
    }

    @Test
    public void testDecodeByteList1() {
        byte[] input = Hex.decode("c101");
        Object[] expected = new Object[] {(byte) 1};

        DecodeResult result = RLP.decode(input, 0);
        assertThat(result.toString()).isEqualTo("01");

        Object[] actual = (Object[]) result.getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);
        assertThat(byteArrayToInt((byte[]) actual[0])).isEqualTo(expected[0]);

        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPList.recursivePrint(list);
        System.out.println();

        RLPElement elm = RLP.decode2OneItem(input, 1);
        assertThat(elm.getRLPData()).isEqualTo(new byte[] {1});
    }

    @Test
    public void testDecodeByteList2() {

        byte[] input = Hex.decode("c3010203");
        Object[] expected = new Object[] {(byte) 1, (byte) 2, (byte) 3};

        DecodeResult result = RLP.decode(input, 0);
        assertThat(result.toString()).isEqualTo("010203");

        Object[] actual = (Object[]) result.getDecoded();
        assertThat(actual.length).isEqualTo(expected.length);
        assertThat(byteArrayToInt((byte[]) actual[0])).isEqualTo(expected[0]);

        RLPList list = RLP.decode2(input);
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).getRLPData()).isEqualTo(input);

        RLPList.recursivePrint(list);
        System.out.println();

        RLPElement elm = RLP.decode2OneItem(input, 1);
        assertThat(elm.getRLPData()).isEqualTo(new byte[] {1});

        elm = RLP.decode2OneItem(input, 2);
        assertThat(elm.getRLPData()).isEqualTo(new byte[] {2});

        elm = RLP.decode2OneItem(input, 3);
        assertThat(elm.getRLPData()).isEqualTo(new byte[] {3});
    }

    @Test(expected = RuntimeException.class)
    public void testDecodeInt_wException() {
        // incorrect encoding
        byte[] input =
                Hex.decode(
                        "b840b840d8d60c2580fa795cfc0313efdeba869d2194e79e7cb2b522f782ffa0392cbbab8d1bac301208b137e0de4998334f3bcf73fa117ef213f87417089feaf84c");

        RLP.decodeInt(input, 0);
    }

    @Test(expected = RuntimeException.class)
    public void testDecodeBigInt_wException() {
        // incorrect encoding
        byte[] input =
                Hex.decode(
                        "c140b840d8d60c2580fa795cfc0313efdeba869d2194e79e7cb2b522f782ffa0392cbbab8d1bac301208b137e0de4998334f3bcf73fa117ef213f87417089feaf84c");

        RLP.decodeBigInteger(input, 0);
    }

    @Test
    public void testDecode_ToNull() {
        assertThat(RLP.decode(null, 0)).isNull();

        assertThat(RLP.decode(new byte[0], 0)).isNull();

        assertThat(RLP.decode2(null)).isEmpty();

        assertThat(RLP.decode2(new byte[0])).isEmpty();
    }

    @Test(expected = RuntimeException.class)
    public void testDecode_wException() {
        RLP.decode(new byte[] {-1}, 0);
    }
}
