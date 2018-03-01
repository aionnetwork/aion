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

package org.aion.api.server.http;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aion.api.server.ApiAion;
import org.aion.api.server.IRpc;
import org.aion.api.server.types.*;
import org.aion.base.type.*;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.apache.commons.collections4.map.LRUMap;
import org.aion.base.util.TypeConverter;
import org.aion.mcf.core.AccountState;
import org.aion.equihash.Solution;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallbackA0;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.mcf.vm.types.Log;
import org.aion.mcf.types.AbstractTxReceipt;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.json.JSONArray;
import org.json.JSONObject;

final class ApiWeb3Aion extends ApiAion implements IRpc {

    ApiWeb3Aion(final IAionChain _ac) {
        super(_ac);
        this.pendingReceipts = Collections.synchronizedMap(new LRUMap<>(FLTRS_MAX, 100));

        IHandler blkHr = this.ac.getAionHub().getEventMgr().getHandler(2);
        if (blkHr != null) {
            blkHr.eventCallback(
                    new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                        public void onBlock(final IBlockSummary _bs) {
                            AionBlockSummary bs = (AionBlockSummary) _bs;
                            IAionBlock b = bs.getBlock();
                            List<AionTransaction> txs = b.getTransactionsList();

                            /*
                             * TODO: fix it If dump empty txs list block to
                             * onBlock filter leads null exception on
                             * getTransactionReceipt
                             */
                            if (txs.size() > 0) {
                                installedFilters.values().forEach((f) -> {
                                    switch (f.getType()) {
                                    case BLOCK:
                                        f.add(new EvtBlk(b));
                                        if (LOG.isDebugEnabled())
                                            LOG.debug("<event-new-block num={} txs={}>", b.getNumber(), txs.size());
                                        break;
                                    case LOG:
                                        List<AionTxReceipt> txrs = bs.getReceipts();
                                        int txIndex = 0;
                                        int lgIndex = 0;
                                        for (AionTxReceipt txr : txrs) {
                                            List<Log> infos = txr.getLogInfoList();
                                            for (Log bi : infos) {
                                                TxRecptLg txLg = new TxRecptLg(bi, b, txIndex, txr.getTransaction(),
                                                        lgIndex);
                                                txIndex++;
                                                lgIndex++;
                                                f.add(new EvtLg(txLg));
                                            }
                                        }
                                        if (LOG.isDebugEnabled())
                                            LOG.debug("<event-new-log num={} txs={}>", b.getNumber(), txs.size());
                                        break;
                                    default:
                                        if (LOG.isDebugEnabled())
                                            LOG.debug("<event-new-", b.getNumber(), txs.size());
                                        break;
                                    }
                                });
                            }
                        }
                    });
        }

        IHandler txHr = this.ac.getAionHub().getEventMgr().getHandler(1);
        if (txHr != null) {
            txHr.eventCallback(
                    new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                        public void onPendingTxUpdate(final ITxReceipt _txRcpt, final EventTx.STATE _state,
                                final IBlock _blk) {
                            ByteArrayWrapper txHashW = new ByteArrayWrapper(
                                    ((AionTxReceipt) _txRcpt).getTransaction().getHash());
                            if (_state.isPending() || _state == EventTx.STATE.DROPPED0) {
                                pendingReceipts.put(txHashW, (AionTxReceipt) _txRcpt);
                            } else {
                                pendingReceipts.remove(txHashW);
                            }
                        }

                        public void onPendingTxReceived(ITransaction _tx) {
                            installedFilters.values().forEach((f) -> {
                                if (f.getType() == Fltr.Type.TRANSACTION) {
                                    f.add(new EvtTx((AionTransaction) _tx));
                                }
                            });
                        }
                    });
        }
    }

    /*
     * Return a reference to the AIONBlock without converting values to hex
     * Requied for the mining pool implementation
     */
    AionBlock getBlockRaw(int bn) {
        // long bn = this.parseBnOrId(_bnOrId);
        AionBlock nb = this.ac.getBlockchain().getBlockByNumber(bn);
        if (nb == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("<get-block-raw bn={} err=not-found>", bn);
            return null;
        } else {
            return nb;
        }
    }

    private JSONObject blockToJson(AionBlock block, BigInteger totalDifficulty, boolean fullTransaction) {
        JSONObject obj = new JSONObject();
        obj.put("number", block.getNumber());
        obj.put("hash", TypeConverter.toJsonHex(block.getHash()));
        obj.put("parentHash", TypeConverter.toJsonHex(block.getParentHash()));
        obj.put("logsBloom", TypeConverter.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", TypeConverter.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", TypeConverter.toJsonHex(block.getStateRoot()));
        obj.put("receiptsRoot",
                TypeConverter.toJsonHex(block.getReceiptsRoot() == null ? new byte[0] : block.getReceiptsRoot()));
        obj.put("difficulty", TypeConverter.toJsonHex(block.getDifficulty()));
        obj.put("totalDifficulty", TypeConverter.toJsonHex(totalDifficulty));

        // TODO: this is coinbase, miner, or minerAddress?
        obj.put("miner", TypeConverter.toJsonHex(block.getCoinbase().toString()));
        obj.put("timestamp", TypeConverter.toJsonHex(block.getTimestamp()));
        obj.put("nonce", TypeConverter.toJsonHex(block.getNonce()));
        obj.put("solution", TypeConverter.toJsonHex(block.getHeader().getSolution()));
        obj.put("gasUsed", TypeConverter.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", TypeConverter.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", TypeConverter.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", TypeConverter.toJsonHex(block.getHeader().getEnergyLimit()));
        //
        obj.put("extraData", TypeConverter.toJsonHex(block.getExtraData()));
        obj.put("size", new NumericalValue(block.getEncoded().length).toHexString());

        JSONArray jsonTxs = new JSONArray();
        List<AionTransaction> _txs = block.getTransactionsList();
        for (AionTransaction _tx : _txs) {
            if (fullTransaction) {
                JSONObject jsonTx = new JSONObject();
                jsonTx.put("address", (_tx.getContractAddress() != null)? TypeConverter.toJsonHex(_tx.getContractAddress().toString()):null);
                jsonTx.put("transactionHash", TypeConverter.toJsonHex(_tx.getHash()));
                jsonTx.put("transactionIndex", getTransactionReceipt(_tx.getHash()).transactionIndex);
                jsonTx.put("value", TypeConverter.toJsonHex(_tx.getValue()));
                jsonTx.put("nrg", _tx.getNrg());
                jsonTx.put("nrgPrice", TypeConverter.toJsonHex(_tx.getNrgPrice()));
                jsonTx.put("nonce", ByteUtil.byteArrayToLong(_tx.getNonce()));
                jsonTx.put("from", TypeConverter.toJsonHex(_tx.getFrom().toString()));
                jsonTx.put("to", TypeConverter.toJsonHex(_tx.getTo().toString()));
                jsonTx.put("timestamp", ByteUtil.byteArrayToLong(_tx.getTimeStamp()));
                jsonTx.put("input", TypeConverter.toJsonHex(_tx.getData()));
                jsonTx.put("blockNumber", block.getNumber());
                jsonTxs.put(jsonTx);
            } else {
                jsonTxs.put(TypeConverter.toJsonHex(_tx.getHash()));
            }
        }
        obj.put("transactions", jsonTxs);
        return obj;
    }

    JSONObject eth_getBlockByHash(String hashString, boolean fullTransactions) {

        byte[] hash = ByteUtil.hexStringToBytes(hashString);
        AionBlock block = this.ac.getBlockchain().getBlockByHash(hash);
        BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(hash);
        if (block == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("<get-block bn={} err=not-found>");
            return null;
        } else {
            try {
                return blockToJson(block, totalDiff, fullTransactions);
            } catch (Exception ex) {
                if (LOG.isDebugEnabled())
                    LOG.debug("<get-block bh={} err=exception>", hashString);
                return null;
            }
        }
    }

    JSONObject eth_getBlock(String _bnOrId, boolean _fullTx) {
        long bn = this.parseBnOrId(_bnOrId);
        AionBlock nb = this.ac.getBlockchain().getBlockByNumber(bn);
        BigInteger totalDiff = this.ac.getAionHub().getBlockStore().getTotalDifficultyForHash(nb.getHash());
        if (nb == null) {
            if (LOG.isDebugEnabled())
                LOG.debug("<get-block bn={} err=not-found>");
            return null;
        } else {
            try {
                return blockToJson(nb, totalDiff, _fullTx);
            } catch (Exception ex) {
                if (LOG.isDebugEnabled())
                    LOG.debug("<get-block bn={} err=exception>", _bnOrId);
                return null;
            }
        }
    }

    String eth_newBlockFilter() {
        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, new FltrBlk());
        return TypeConverter.toJsonHex(id);
    }

    /**
     * this method name is so weird, what does new means
     */
    String eth_newFilter(ArgFltr rf) {
        FltrLg fltrLg = new FltrLg(rf.address, rf.toBlock, rf.topics);
        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, fltrLg);

        long toBlock = this.getBestBlock().getNumber();
        long fromBlock = Math.max(1, toBlock - 5);

        /*
         * Simplify first for now. Preload past 5 blocks
         */
        int lgIndex = 0;
        for (long i = fromBlock; i <= toBlock; i++) {
            AionBlock blk = this.getBlock(i);
            List<AionTransaction> txs = blk.getTransactionsList();
            int txIndex = 0;
            if (txs.size() > 0) {
                for (AionTransaction tx : txs) {
                    Address _contractAddress = tx.getTo() == null ? tx.getContractAddress() : tx.getTo();

                    /*
                     * only check with empty topics string here in case user
                     * bind to all events which should emit constructor events
                     */
                    if (_contractAddress.equals(rf.address)) {
                        @SuppressWarnings("rawtypes")
                        AbstractTxReceipt txr = ac.getBlockchain().getTransactionInfo(tx.getHash()).getReceipt();
                        @SuppressWarnings("unchecked")
                        List<Log> infos = txr.getLogInfoList();
                        for (Log bi : infos) {
                            TxRecptLg txLg = new TxRecptLg(bi, blk, txIndex, txr.getTransaction(), lgIndex);
                            txIndex++;
                            lgIndex++;
                            fltrLg.add(new EvtLg(txLg));
                        }
                    }
                }
            }
        }

        return Long.toHexString(id);

    }

    JSONArray eth_getFilterChanges(final String _id) {
        if (_id == null)
            return new JSONArray();
        long id = TypeConverter.StringHexToBigInteger(_id).longValue();
        Fltr filter = installedFilters.get(id);

        if (filter == null) {
            return new JSONArray();
        }
        Object[] _events = filter.poll();
        int _events_size = _events.length;
        JSONArray events = new JSONArray();

        Fltr.Type t = filter.getType();
        switch (t) {
        case BLOCK:
            if (LOG.isDebugEnabled())
                LOG.debug("<filter-block-poll id={} events={}>", id, _events_size);
            List<Object> blkHashes = new ArrayList<>();
            for (Object _event : _events) {
                blkHashes.add(((EvtBlk) _event).toJSON());
            }
            events = new JSONArray(blkHashes);
            break;

        case TRANSACTION:
            if (LOG.isDebugEnabled())
                LOG.debug("<filter-transaction-poll id={} events={}>", id, _events_size);
            List<Object> txHashes = new ArrayList<>();
            for (Object _event : _events) {
                txHashes.add(((EvtTx) _event).toJSON());
            }
            events = new JSONArray(txHashes);
            break;

        case LOG:
            if (LOG.isDebugEnabled())
                LOG.debug("<filter-log-poll id={} events={}>", id, _events_size);
            List<JSONObject> logs = new ArrayList<>();
            for (Object _event : _events) {
                logs.add(((EvtLg) _event).toJSON());
            }
            events = new JSONArray(logs);
            break;

        default:
            if (LOG.isDebugEnabled())
                LOG.debug("<filter-unknown-poll id={} events={}>", id, _events_size);
            break;
        }
        return events;
    }

    TxRecpt eth_getTransactionReceipt(String txHash) {
        return this.getTransactionReceipt(TypeConverter.StringHexToByteArray(txHash));
    }

    private static final ByteArrayWrapper EMPTY = new ByteArrayWrapper(new byte[0]);
    String eth_getCode(String address) throws Exception {
        Address addr = new Address(address);
        ByteArrayWrapper state = this.ac.getCode(addr).orElse(EMPTY);

        if (state == EMPTY)
            return "";
        return "0x" + state.toString();
    }

    boolean eth_uninstallFilter(String id) {
        return id != null && installedFilters.remove(TypeConverter.StringHexToBigInteger(id).longValue()) != null;
    }

    Tx eth_getTransactionByHash(String txHash) {
        if (txHash.startsWith("0x"))
            txHash = txHash.substring(2, txHash.length());

        byte[] transactionHash = ByteUtil.hexStringToBytes(txHash);

        AionTransaction transaction = this.getTransactionByHash(transactionHash);
        TxRecpt transactionReceipt = this.getTransactionReceipt(ByteUtil.hexStringToBytes(txHash));
        if (transaction != null && transactionReceipt != null)
            return new Tx(transactionReceipt.contractAddress,
                    transactionReceipt.transactionHash,
                    transactionReceipt.blockHash,
                    new NumericalValue(transactionReceipt.txNonce),
                    transactionReceipt.from, transactionReceipt.to,
                    new NumericalValue(transactionReceipt.txTimeStamp),
                    new NumericalValue(transactionReceipt.txValue),
                    transactionReceipt.txData,
                    new NumericalValue(transactionReceipt.blockNumber),
                    new NumericalValue(transaction.getNrg()),
                    new NumericalValue(transaction.getNrgPrice()),
                    new NumericalValue(transaction.getTxIndexInBlock()));
        else
            return null;
    }

    private final AccountState DEFAULT_ACCOUNT = new AccountState();

    /**
     * gets the transaction count given the address
     * 
     * @param address
     *            of the account that query the transaction count (nonce) for
     * @param number
     *            null if latest, otherwise provide the number
     * @return {@code 0} if account not found, {@code nonce} of the account
     *         otherwise
     */
    NumericalValue eth_getTransactionCount(Address address, NumericalValue number) {
        BigInteger nonce;
        if (number == null) {
            nonce = this.ac.getAccountState(address).orElse(DEFAULT_ACCOUNT).getNonce();
        } else {
            nonce = this.ac.getAccountState(address, number.toBigInteger().longValueExact()).orElse(DEFAULT_ACCOUNT)
                    .getNonce();
        }
        return new NumericalValue(nonce);
    }

    // AION Mining Pool
    // TODO Test multiple threads submitting blocks
    synchronized boolean submitBlock(Solution solution) {

        AionBlock block = (AionBlock) solution.getBlock();

        // set the nonce and solution
        block.getHeader().setNonce(solution.getNonce());
        block.getHeader().setSolution(solution.getSolution());

        // This can be improved
        return (AionImpl.inst().addNewMinedBlock(block)).isSuccessful();
    }
}
