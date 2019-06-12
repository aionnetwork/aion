package org.aion.zero.impl.db;

import static org.aion.p2p.V1Constants.HASH_SIZE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.aion.mcf.db.InternalVmType;
import org.aion.mcf.ds.Serializer;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.util.types.ByteArrayWrapper;

/**
 * Indexed information about contracts that is not part of consensus. Used to:
 *
 * <ol>
 *   <li>quickly find the block where the contract was created;
 *   <li>determine which virtual machine was used in deploying the contract;
 *   <li>check if the contract information is complete during fast sync.
 * </ol>
 *
 * @author Alexandra Roatis
 */
public class ContractInformation {

    protected Map<ByteArrayWrapper, Information> infoByCodeHash = new HashMap<>();

    private ContractInformation() {};

    /**
     * Constructor.
     *
     * @param code the hash of the code deployed at contract creation used to identify the code
     *     instance among multiple chains
     * @param block the hash of the block in which the contract was created
     * @param vm the VM used at contract deployment
     * @param complete boolean flag used during fast sync to check for contract sync completeness
     */
    public ContractInformation(
            ByteArrayWrapper code, InternalVmType vm, ByteArrayWrapper block, boolean complete) {

        Information info = new Information();
        info.blocks.put(block, complete);
        info.vm = vm;

        infoByCodeHash.put(code, info);
    }

    /**
     * @param code the hash of the code deployed at contract creation used to identify the code
     *     instance among multiple chains
     * @param block the hash of the block in which the contract was created
     * @param vm the VM used at contract deployment
     * @param complete boolean flag used during fast sync to check for contract sync completeness
     */
    public void append(
            ByteArrayWrapper code, InternalVmType vm, ByteArrayWrapper block, boolean complete) {
        // retrieve it if already exists
        Information info = infoByCodeHash.get(code);

        // create new instance otherwise
        if (info == null) {
            info = new Information();
            infoByCodeHash.put(code, info);
        }

        // there may be multiple inception blocks for the same contract
        // overwrites previous setting when complete
        info.blocks.put(block, complete);
        // we do not check for incompatible data, i.e. same key & different VM type
        // because they key used is a hash of the contract code and AVM code != FVM code
        // the same key must apply to the same contract and its corresponding VM
        info.vm = vm;
    }

    public static final Serializer<ContractInformation, byte[]> RLP_SERIALIZER =
            new Serializer<>() {

                /**
                 * Returns an RLP encoding of the given contract information object.
                 *
                 * @return an RLP encoding of the given contract information object.
                 */
                @Override
                public byte[] serialize(ContractInformation info) {
                    byte[][] rlpInfo = new byte[info.infoByCodeHash.size()][];
                    int i = 0;
                    for (Map.Entry<ByteArrayWrapper, Information> e :
                            info.infoByCodeHash.entrySet()) {
                        rlpInfo[i++] =
                                RLP.encodeList(
                                        RLP.encodeElement(e.getKey().toBytes()),
                                        serializeInformation(e.getValue()));
                    }
                    return RLP.encodeList(rlpInfo);
                }

                /**
                 * Decodes a contract information object from the RLP encoding.
                 *
                 * @param rlpEncoded The encoding to be interpreted as contract information.
                 */
                @Override
                public ContractInformation deserialize(byte[] rlpEncoded) {
                    // validity check
                    if (rlpEncoded == null || rlpEncoded.length == 0) return null;

                    RLPElement decoded = RLP.decode2(rlpEncoded).get(0);

                    // validity check
                    if (!(decoded instanceof RLPList)) return null;

                    // create and populate object
                    ContractInformation info = new ContractInformation();
                    RLPList list = (RLPList) decoded;

                    for (RLPElement e : list) {
                        // validity check
                        if (!(e instanceof RLPList)) return null;

                        RLPList pair = (RLPList) e;

                        // validity check
                        if (pair.size() != 2
                                || !(pair.get(1) instanceof RLPList)
                                || pair.get(0).getRLPData().length != HASH_SIZE) return null;

                        Information current = deserializeInformation((RLPList) pair.get(1));

                        // validity check
                        if (current == null) return null;

                        info.infoByCodeHash.put(
                                ByteArrayWrapper.wrap(pair.get(0).getRLPData()), current);
                    }

                    return info;
                }
            };

    private static Information deserializeInformation(RLPList list) {
        // validity check
        if (list.size() != 2) return null;

        Information info = new Information();

        // validity check blocks
        RLPElement code = list.get(0);
        if (!(code instanceof RLPList)) return null;

        for (RLPElement e : ((RLPList) code)) {
            // validity check pair
            if (!(e instanceof RLPList)) return null;

            RLPList pair = (RLPList) e;

            // validity check hash
            if (pair.size() != 2 || pair.get(0).getRLPData().length != 32) return null;

            // validity check completeness status
            byte[] flag = pair.get(1).getRLPData();
            if (flag.length > 1 || (flag.length == 1 && flag[0] != 1)) return null;

            ByteArrayWrapper key = ByteArrayWrapper.wrap(pair.get(0).getRLPData());
            info.blocks.put(key, flag.length == 1); // zero (i.e. false) decodes to empty byte array
        }

        // validity check VM
        byte[] array = list.get(1).getRLPData();
        if (array.length != 1) return null;

        info.vm = InternalVmType.getInstance(array[0]);

        // return correct instance
        return info;
    }

    public static byte[] serializeInformation(Information info) {

        // encode blocks
        byte[][] rlpBlocks = new byte[info.blocks.size()][];
        int i = 0;
        for (ByteArrayWrapper wrapper : info.blocks.keySet()) {
            rlpBlocks[i++] =
                    RLP.encodeList(
                            // encode inception block
                            RLP.encodeElement(wrapper.toBytes()),
                            // encode completeness flag
                            RLP.encodeByte((byte) (info.blocks.get(wrapper) ? 1 : 0)));
        }

        // encode vm
        byte[] rlpVm = RLP.encodeByte(info.vm.getCode());

        // combine values
        return RLP.encodeList(RLP.encodeList(rlpBlocks), rlpVm);
    }

    public InternalVmType getVmUsed(byte[] codeHash) {
        Information info = infoByCodeHash.get(ByteArrayWrapper.wrap(codeHash));
        if (info != null) {
            return info.vm;
        } else {
            return InternalVmType.UNKNOWN;
        }
    }

    public boolean isComplete(byte[] codeHash, byte[] blockHash) {
        Information info = infoByCodeHash.get(ByteArrayWrapper.wrap(codeHash));
        if (info != null) {
            ByteArrayWrapper block = ByteArrayWrapper.wrap(blockHash);
            if (info.blocks.containsKey(block)) {
                return info.blocks.get(block);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public Set<ByteArrayWrapper> getInceptionBlocks(byte[] codeHash) {
        Information info = infoByCodeHash.get(ByteArrayWrapper.wrap(codeHash));
        if (info != null) {
            return info.blocks.keySet();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (Map.Entry<ByteArrayWrapper, Information> e : infoByCodeHash.entrySet()) {
            sb.append("\t>  Code: " + e.getKey() + "\n" + e.getValue() + "\n");
        }

        Object o = new Object();
        return "\n"
                + getClass().getSimpleName()
                + "@"
                + Integer.toHexString(hashCode())
                + "\n"
                + sb.toString();
    }

    private static class Information {
        // used for fast retrieval of the blocks where the contract was created
        // and during fast sync to check for contract sync completeness
        private Map<ByteArrayWrapper, Boolean> blocks = new HashMap<>();
        // used to determine the VM used at contract deployment
        private InternalVmType vm;

        @Override
        public String toString() {

            StringBuilder sb = new StringBuilder();

            for (Map.Entry<ByteArrayWrapper, Boolean> e : blocks.entrySet()) {
                sb.append("\t| Block: " + e.getKey() + " -> " + e.getValue() + "\n");
            }

            return "\t|    VM: " + vm + "\n" + sb.toString();
        }
    }
}
