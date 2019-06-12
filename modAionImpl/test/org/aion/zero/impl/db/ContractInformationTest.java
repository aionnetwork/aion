package org.aion.zero.impl.db;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.p2p.V1Constants.HASH_SIZE;

import java.util.Set;
import junitparams.JUnitParamsRunner;
import org.aion.mcf.db.InternalVmType;
import org.aion.rlp.RLP;
import org.aion.util.types.ByteArrayWrapper;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link ContractInformation}.
 *
 * @author Alexandra Roatis
 */
@RunWith(JUnitParamsRunner.class)
public class ContractInformationTest {

    private static final byte[] encoded123 = RLP.encodeElement(new byte[] {1, 2, 3});
    private static final byte[] hash1 =
            new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 0, 1, 2
            };
    private static final byte[] encodedHash1 = RLP.encodeElement(hash1);
    private static final byte[] hash2 =
            new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 0, 3, 4
            };
    private static final byte[] encodedHash2 = RLP.encodeElement(hash2);
    private static final byte[] avm = RLP.encodeByte(InternalVmType.AVM.getCode());
    private static final byte[] fvm = RLP.encodeByte(InternalVmType.FVM.getCode());
    private static final byte[] flag0 = RLP.encodeByte((byte) 0);
    private static final byte[] flag1 = RLP.encodeByte((byte) 1);

    @Test
    public void testDecode_nullMessage() {
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(null)).isNull();
    }

    @Test
    public void testDecode_emptyMessage() {
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(new byte[0])).isNull();
    }

    @Test
    public void testDecode_notAList() {
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoded123)).isNull();
    }

    @Test
    public void testDecode_notASubList() {
        byte[] encoding = RLP.encodeList(encoded123);
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_notAPair_sizeOne() {
        byte[] encoding = RLP.encodeList(RLP.encodeList(encoded123));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_notAPair_sizeThree() {
        byte[] encoding = RLP.encodeList(RLP.encodeList(encoded123, encoded123, encoded123));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationNotAList() {
        byte[] encoding = RLP.encodeList(RLP.encodeList(encodedHash1, encoded123));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationKeyNotAHash() {
        byte[] encoding = RLP.encodeList(RLP.encodeList(encoded123, RLP.encodeList(encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectSizeOne() {
        byte[] encoding = RLP.encodeList(RLP.encodeList(encodedHash1, RLP.encodeList(encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectSizeThree() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeList(
                                encodedHash1, RLP.encodeList(encoded123, encoded123, encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectBlocksSublist() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        encoded123, // TEST: blocks should be a list
                                        encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectBlockPairList() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        RLP.encodeList( // blocks
                                                encoded123), // TEST: entry should be a list
                                        encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectBlockPairSizeOne() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        RLP.encodeList( // blocks
                                                RLP.encodeList(encoded123)), // TEST: size != 2
                                        encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectBlockPairSizeThree() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        RLP.encodeList( // blocks
                                                RLP.encodeList( // TEST: size != 2
                                                        encoded123, encoded123, encoded123)),
                                        encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectBlockHashSize() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        RLP.encodeList( // blocks
                                                RLP.encodeList(
                                                        encoded123, // TEST: hash size != 32
                                                        encoded123)),
                                        encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectBlockFlagSize() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        RLP.encodeList( // blocks
                                                RLP.encodeList(
                                                        encodedHash1,
                                                        encoded123)), // TEST: flag size > 1
                                        encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectBlockFlagValue() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        RLP.encodeList( // blocks
                                                RLP.encodeList(
                                                        encodedHash1,
                                                        RLP.encodeByte( // TEST: flag value != 1
                                                                (byte) 2))),
                                        encoded123)));
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecode_informationIncorrectVMSize() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        RLP.encodeList(
                                                RLP.encodeList(encodedHash1, flag1)), // blocks
                                        encoded123))); // TEST: vm size != 1
        assertThat(ContractInformation.RLP_SERIALIZER.deserialize(encoding)).isNull();
    }

    @Test
    public void testDecodeEncode_correctSingleInstance() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry
                                encodedHash1,
                                RLP.encodeList( // specific info
                                        RLP.encodeList(
                                                RLP.encodeList(encodedHash2, flag1)), // blocks
                                        avm)));
        ContractInformation ci = ContractInformation.RLP_SERIALIZER.deserialize(encoding);
        System.out.println(ci);

        assertThat(ci).isNotNull();

        assertThat(ci.getVmUsed(hash1)).isEqualTo(InternalVmType.AVM);
        assertThat(ci.getVmUsed(new byte[] {1, 2})).isEqualTo(InternalVmType.UNKNOWN);

        assertThat(ci.getInceptionBlocks(hash1)).isEqualTo(Set.of(ByteArrayWrapper.wrap(hash2)));
        assertThat(ci.getInceptionBlocks(hash2).isEmpty()).isTrue();

        assertThat(ci.isComplete(hash1, hash2)).isTrue();
        assertThat(ci.isComplete(hash1, hash1)).isFalse();
        assertThat(ci.isComplete(hash2, hash1)).isFalse();

        // check that the encoding matches
        assertThat(ContractInformation.RLP_SERIALIZER.serialize(ci)).isEqualTo(encoding);

        // constructor version
        ci =
                new ContractInformation(
                        ByteArrayWrapper.wrap(hash1),
                        InternalVmType.AVM,
                        ByteArrayWrapper.wrap(hash2),
                        true);
        assertThat(ContractInformation.RLP_SERIALIZER.serialize(ci)).isEqualTo(encoding);
    }

    @Test
    public void testDecodeEncode_correctMultiInstance() {
        byte[] encoding =
                RLP.encodeList( // contract info
                        RLP.encodeList( // hash map entry 1
                                encodedHash1,
                                RLP.encodeList(
                                        RLP.encodeList(RLP.encodeList(encodedHash2, flag1)), avm)),
                        RLP.encodeList( // hash map entry 2
                                encodedHash2,
                                RLP.encodeList(
                                        RLP.encodeList(
                                                RLP.encodeList(encodedHash1, flag1),
                                                RLP.encodeList(encodedHash2, flag0)),
                                        fvm)));
        ContractInformation ci = ContractInformation.RLP_SERIALIZER.deserialize(encoding);
        System.out.println(ci);

        assertThat(ci).isNotNull();

        assertThat(ci.getVmUsed(hash1)).isEqualTo(InternalVmType.AVM);
        assertThat(ci.getVmUsed(hash2)).isEqualTo(InternalVmType.FVM);
        assertThat(ci.getVmUsed(new byte[] {1, 2})).isEqualTo(InternalVmType.UNKNOWN);

        assertThat(ci.getInceptionBlocks(hash1)).isEqualTo(Set.of(ByteArrayWrapper.wrap(hash2)));
        assertThat(ci.getInceptionBlocks(hash2))
                .isEqualTo(Set.of(ByteArrayWrapper.wrap(hash1), ByteArrayWrapper.wrap(hash2)));
        assertThat(ci.getInceptionBlocks(new byte[HASH_SIZE]).isEmpty()).isTrue();

        assertThat(ci.isComplete(hash1, hash2)).isTrue();
        assertThat(ci.isComplete(hash1, hash1)).isFalse();
        assertThat(ci.isComplete(hash2, hash1)).isTrue();
        assertThat(ci.isComplete(hash2, hash2)).isFalse();
        assertThat(ci.isComplete(new byte[HASH_SIZE], hash2)).isFalse();

        // check that the encoding matches
        // note that this might differ if the hash1 would not be less than hash2
        assertThat(ContractInformation.RLP_SERIALIZER.serialize(ci)).isEqualTo(encoding);

        // constructor version
        ByteArrayWrapper key1 = ByteArrayWrapper.wrap(hash1);
        ByteArrayWrapper key2 = ByteArrayWrapper.wrap(hash2);
        ci = new ContractInformation(key1, InternalVmType.AVM, key2, true);
        ci.append(key2, InternalVmType.FVM, key1, true);
        ci.append(key2, InternalVmType.FVM, key2, false);
        assertThat(ContractInformation.RLP_SERIALIZER.serialize(ci)).isEqualTo(encoding);
    }
}
