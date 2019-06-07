package org.aion.api.server.types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.interfaces.block.Block;
import org.aion.interfaces.block.BlockSummary;
import org.aion.interfaces.tx.Transaction;
import org.aion.mcf.vm.types.Bloom;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.zero.impl.core.BloomFilter;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.aion.vm.api.interfaces.IBloomFilter;

/** @author chris */

// NOTE: only used by web3 api
public final class FltrLg extends Fltr {

    private List<byte[][]> topics = new ArrayList<>(); //  [[addr1, addr2], null, [A, B], [C]]
    private byte[][] contractAddresses = new byte[0][];
    private Bloom[][] filterBlooms;

    public FltrLg() {
        super(Type.LOG);
    }

    public void setContractAddress(List<byte[]> address) {
        byte[][] t = new byte[address.size()][];
        for (int i = 0; i < address.size(); i++) {
            t[i] = address.get(i);
        }
        contractAddresses = t;
    }

    public void setTopics(List<byte[][]> topics) {
        this.topics = topics;
    }

    // -------------------------------------------------------------------------------

    @Override
    public boolean onBlock(BlockSummary bs) {
        List<AionTxReceipt> receipts = ((AionBlockSummary) bs).getReceipts();
        Block blk = bs.getBlock();

        if (matchBloom(new Bloom(((IAionBlock) blk).getLogBloom()))) {
            int txIndex = 0;
            for (AionTxReceipt receipt : receipts) {
                Transaction tx = receipt.getTransaction();
                if (tx.getDestinationAddress() != null
                        && matchesContractAddress(tx.getDestinationAddress().toByteArray())) {
                    if (matchBloom(receipt.getBloomFilter())) {
                        int logIndex = 0;
                        for (IExecutionLog logInfo : receipt.getLogInfoList()) {
                            if (matchBloom(logInfo.getBloomFilterForLog())
                                    && matchesExactly(logInfo)) {
                                add(
                                        new EvtLg(
                                                new TxRecptLg(
                                                        logInfo,
                                                        blk,
                                                        txIndex,
                                                        receipt.getTransaction(),
                                                        logIndex,
                                                        true)));
                            }
                            logIndex++;
                        }
                    }
                }
                txIndex++;
            }
        }
        return true;
    }

    // inelegant (distributing chain singleton ref. into here), tradeoff for efficiency and ease of
    // impl.
    // rationale: this way, we only retrieve logs from DB for transactions that the bloom
    // filter gives a positive match for;
    public boolean onBlock(IAionBlock blk, IAionBlockchain chain) {
        if (matchBloom(new Bloom(blk.getLogBloom()))) {
            int txIndex = 0;
            for (Transaction txn : blk.getTransactionsList()) {
                if (txn.getDestinationAddress() != null
                        && matchesContractAddress(txn.getDestinationAddress().toByteArray())) {
                    // now that we know that our filter might match with some logs in this
                    // transaction, go ahead
                    // and retrieve the txReceipt from the chain
                    AionTxInfo txInfo = chain.getTransactionInfo(txn.getTransactionHash());
                    AionTxReceipt receipt = txInfo.getReceipt();
                    if (matchBloom(receipt.getBloomFilter())) {
                        int logIndex = 0;
                        for (IExecutionLog logInfo : receipt.getLogInfoList()) {
                            if (matchBloom(logInfo.getBloomFilterForLog())
                                    && matchesExactly(logInfo)) {
                                add(
                                        new EvtLg(
                                                new TxRecptLg(
                                                        logInfo, blk, txIndex, txn, logIndex,
                                                        true)));
                            }
                            logIndex++;
                        }
                    }
                }
                txIndex++;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------------

    private void initBlooms() {
        if (filterBlooms != null) return;

        List<byte[][]> addrAndTopics = new ArrayList<>(topics);
        addrAndTopics.add(contractAddresses);

        filterBlooms = new Bloom[addrAndTopics.size()][];
        for (int i = 0; i < addrAndTopics.size(); i++) {
            byte[][] orTopics = addrAndTopics.get(i);
            if (orTopics == null || orTopics.length == 0) {
                filterBlooms[i] = new Bloom[] {new Bloom()}; // always matches
            } else {
                filterBlooms[i] = new Bloom[orTopics.length];
                for (int j = 0; j < orTopics.length; j++) {
                    filterBlooms[i][j] = BloomFilter.create(orTopics[j]);
                }
            }
        }
    }

    public boolean matchBloom(IBloomFilter blockBloom) {
        initBlooms();
        for (Bloom[] andBloom : filterBlooms) {
            boolean orMatches = false;
            for (Bloom orBloom : andBloom) {
                if (blockBloom.matches(orBloom)) {
                    orMatches = true;
                    break;
                }
            }
            if (!orMatches) return false;
        }
        return true;
    }

    public boolean matchesContractAddress(byte[] toAddr) {
        initBlooms();
        for (byte[] address : contractAddresses) {
            if (Arrays.equals(address, toAddr)) return true;
        }
        return contractAddresses.length == 0;
    }

    public boolean matchesExactly(IExecutionLog logInfo) {
        initBlooms();
        if (!matchesContractAddress(logInfo.getSourceAddress().toByteArray())) return false;
        List<byte[]> logTopics = logInfo.getTopics();
        for (int i = 0; i < this.topics.size(); i++) {
            if (i >= logTopics.size()) return false;
            byte[][] orTopics = topics.get(i);
            if (orTopics != null && orTopics.length > 0) {
                boolean orMatches = false;
                byte[] logTopic = logTopics.get(i);
                for (byte[] orTopic : orTopics) {
                    if (Arrays.equals(orTopic, logTopic)) {
                        orMatches = true;
                        break;
                    }
                }
                if (!orMatches) return false;
            }
        }
        return true;
    }
}
