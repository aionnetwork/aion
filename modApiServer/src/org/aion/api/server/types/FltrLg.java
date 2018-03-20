/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.api.server.types;

import org.aion.base.type.*;
import org.aion.mcf.vm.types.Log;
import org.aion.mcf.vm.types.Bloom;
import org.aion.zero.impl.core.BloomFilter;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.types.IAionBlock;
import org.aion.zero.impl.core.IAionBlockchain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author chris
 */

// NOTE: only used by web3 api
public final class FltrLg extends Fltr {

    private List<byte[][]> topics = new ArrayList<>();  //  [[addr1, addr2], null, [A, B], [C]]
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
    public boolean onBlock(IBlockSummary bs) {
        List<AionTxReceipt> receipts = ((AionBlockSummary) bs).getReceipts();
        IBlock blk = bs.getBlock();

        if (matchBloom(new Bloom(((IAionBlock) blk).getLogBloom()))) {
            int txIndex = 0;
            for (AionTxReceipt receipt : receipts) {
                ITransaction tx = receipt.getTransaction();
                if (matchesContractAddress(tx.getTo().toBytes())) {
                    if (matchBloom(receipt.getBloomFilter())) {
                        int logIndex = 0;
                        for (Log logInfo : receipt.getLogInfoList()) {
                            if (matchBloom(logInfo.getBloom()) && matchesExactly(logInfo)) {
                                add(new EvtLg(new TxRecptLg(logInfo, blk, txIndex, receipt.getTransaction(), logIndex, true)));
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

    // inelegant (distributing chain singleton ref. into here), tradeoff for efficiency and ease of impl.
    // rationale: this way, we only retrieve logs from DB for transactions that the bloom
    // filter gives a positive match for;
    public boolean onBlock(IAionBlock blk, IAionBlockchain chain) {
        if (matchBloom(new Bloom(blk.getLogBloom()))) {
            int txIndex = 0;
            for (ITransaction txn : blk.getTransactionsList()) {
                if (matchesContractAddress(txn.getTo().toBytes())) {
                    // now that we know that our filter might match with some logs in this transaction, go ahead
                    // and retrieve the txReceipt from the chain
                    AionTxInfo txInfo = chain.getTransactionInfo(txn.getHash());
                    AionTxReceipt receipt = txInfo.getReceipt();
                    if (matchBloom(receipt.getBloomFilter())) {
                        int logIndex = 0;
                        for (Log logInfo : receipt.getLogInfoList()) {
                            if (matchBloom(logInfo.getBloom()) && matchesExactly(logInfo)) {
                                add(new EvtLg(new TxRecptLg(logInfo, blk, txIndex, txn, logIndex, true)));
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

    public boolean matchBloom(Bloom blockBloom) {
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

    public boolean matchesExactly(Log logInfo) {
        initBlooms();
        if (!matchesContractAddress(logInfo.getAddress().toBytes())) return false;
        List<byte[]> logTopics = logInfo.getTopics();
        for (int i = 0; i < this.topics.size(); i++) {
            if (i >= logTopics.size()) return false;
            byte[][] orTopics = topics.get(i);
            if (orTopics != null && orTopics.length > 0) {
                boolean orMatches = false;
                byte[] logTopic = logTopics.get(i);
                for (byte[] orTopic : orTopics) {
                    if (Arrays.equals(orTopic,logTopic)) {
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