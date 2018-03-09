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

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.aion.api.server.ApiAion;
import org.aion.api.server.IRpc;
import org.aion.api.server.types.*;
import org.aion.base.type.*;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.zero.impl.db.AionBlockStore;
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
            blkHr.eventCallback(new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                public void onBlock(final IBlockSummary _bs) {
                    AionBlockSummary bs = (AionBlockSummary) _bs;
                    installedFilters.keySet().forEach((k) -> {
                        Fltr f = installedFilters.get(k);
                        if (f.isExpired()) {
                            LOG.debug("<Filter: expired, key={}>", k);
                            installedFilters.remove(k);
                        } else if (f.onBlock(bs)) {
                            LOG.debug("<Filter: append, onBlock type={} blk#={}>", f.getType().name(), bs.getBlock().getNumber());
                        }
                    });
                }
            });
        }

        IHandler txHr = this.ac.getAionHub().getEventMgr().getHandler(1);
        if (txHr != null) {
            txHr.eventCallback(new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {

                public void onPendingTxUpdate(final ITxReceipt _txRcpt, final EventTx.STATE _state, final IBlock _blk) {
                    ByteArrayWrapper txHashW = new ByteArrayWrapper(((AionTxReceipt) _txRcpt).getTransaction().getHash());
                    if (_state.isPending() || _state == EventTx.STATE.DROPPED0) {
                        pendingReceipts.put(txHashW, (AionTxReceipt) _txRcpt);
                    } else {
                        pendingReceipts.remove(txHashW);
                    }
                }

                public void onPendingTxReceived(ITransaction _tx) {
                    // not absolutely neccessary to do eviction on installedFilters here, since we're doing it already
                    // in the onBlock event. eviction done here "just in case ..."
                    installedFilters.keySet().forEach((k) -> {
                        Fltr f = installedFilters.get(k);
                        if (f.isExpired()) {
                            LOG.debug("<Filter: expired, key={}>", k);
                            installedFilters.remove(k);
                        } else if(f.onTransaction(_tx)) {
                            LOG.debug("<Filter: append, onPendingTransaction type={} txHash={}>", f.getType().name(), TypeConverter.toJsonHex(_tx.getHash()));
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

    // ------------------------------------------------------------------------

    String eth_newFilter(ArgFltr rf) {

        FltrLg filter = new FltrLg();
        filter.setTopics(rf.topics);
        filter.setContractAddress((byte[][]) rf.address.toArray());
        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, filter);

        final AionBlock fromBlock = this.ac.getBlockchain().getBlockByNumber(this.parseBnOrId(rf.fromBlock));
        AionBlock toBlock = this.ac.getBlockchain().getBlockByNumber(this.parseBnOrId(rf.toBlock));

        if (fromBlock != null) {
            // need to add historical data
            // this is our own policy: what to do in this case is not defined in the spec
            //
            // policy: add data from earliest to latest, until we can't fill the queue anymore
            //
            // caveat: filling up the events-queue with historical data will cause the following issue means that the user will miss all events generated between
            // the first poll and filter installation.

            toBlock = toBlock == null ? getBestBlock() : toBlock;
            for (long i = fromBlock.getNumber(); i <= toBlock.getNumber(); i++) {
                if (filter.isFull()) break;
                filter.onBlock(this.ac.getBlockchain().getBlockByNumber(i), this.ac.getAionHub().getBlockchain());
            }
        }

        return TypeConverter.toJsonHex(id);
    }

    String eth_newBlockFilter() {
        long id = fltrIndex.getAndIncrement();
        installedFilters.put(id, new FltrBlk());
        return TypeConverter.toJsonHex(id);
    }

    String eth_newPendingTransactionFilter() { return ""; }

    boolean eth_uninstallFilter(String id) {
        return id != null && installedFilters.remove(TypeConverter.StringHexToBigInteger(id).longValue()) != null;
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

    JSONArray eth_getFilterLogs() { return null; }

    // ------------------------------------------------------------------------

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

    JSONArray debug_getBlocksByNumber(String _bnOrId, boolean _fullTransactions) {
        long bn = this.parseBnOrId(_bnOrId);
        List<Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>>> blocks = ((AionBlockStore) this.ac.getAionHub().getBlockchain().getBlockStore()).getBlocksByNumber(bn);
        JSONArray response = new JSONArray();
        for (Map.Entry<AionBlock, Map.Entry<BigInteger, Boolean>> block : blocks) {
            JSONObject b = blockToJson(block.getKey(), block.getValue().getKey(), _fullTransactions);
            b.put("mainchain", block.getValue().getValue());
            response.put(b);
        }
        return response;
    }
}
