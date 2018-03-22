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

package org.aion.api.server;

import org.aion.api.server.nrgprice.NrgOracle;
import org.aion.api.server.types.ArgTxCall;
import org.aion.api.server.types.Fltr;
import org.aion.api.server.types.SyncInfo;
import org.aion.api.server.types.TxRecpt;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.TypeConverter;
import org.aion.crypto.ECKey;
import org.aion.evtmgr.IEventMgr;
import org.aion.evtmgr.impl.evt.EventBlock;
import org.aion.evtmgr.impl.evt.EventTx;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.Version;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class ApiAion extends Api {
    // these variables get accessed by the api worker threads.
    // need to guarantee one of:
    // 1. all access to variables protected by some lock
    // 2. underlying datastructure provides concurrency guarntees

    // delegate concurrency to underlying object
    protected NrgOracle nrgOracle;
    protected IAionChain ac; // assumption: blockchainImpl et al. provide concurrency guarantee

    // using java.util.concurrent library objects
    protected AtomicLong fltrIndex = null; // AtomicLong
    protected Map<Long, Fltr> installedFilters = null; // ConcurrentHashMap
    protected Map<ByteArrayWrapper, AionTxReceipt> pendingReceipts; // Collections.synchronizedMap

    // 'safe-publishing' idiom
    protected volatile double reportedHashrate = 0; // volatile, used only for 'publishing'

    // thread safe because value never changing, can be safely read by multiple threads
    protected final String[] compilers = new String[] {"solidity"};
    protected final long DEFAULT_NRG_LIMIT = 500_000L;
    protected final short FLTRS_MAX = 1024;
    protected final String clientVersion = computeClientVersion();

    public ApiAion(final IAionChain _ac) {
        this.ac = _ac;
        this.installedFilters = new ConcurrentHashMap<>();
        this.fltrIndex = new AtomicLong(0);

        // register events
        IEventMgr evtMgr = this.ac.getAionHub().getEventMgr();
        evtMgr.registerEvent(Collections.singletonList(new EventTx(EventTx.CALLBACK.PENDINGTXUPDATE0)));
        evtMgr.registerEvent(Collections.singletonList(new EventBlock(EventBlock.CALLBACK.ONBLOCK0)));
    }

    // General Level
    public byte getApiVersion() {
        return 2;
    }

    // --Commented out by Inspection START (02/02/18 6:57 PM):
    // public int getProtocolVersion() {
    // return 0;
    // }
    // --Commented out by Inspection STOP (02/02/18 6:57 PM)

    public Map<Long, Fltr> getInstalledFltrs() {
        return installedFilters;
    }

    public String getCoinbase() {
        String coinbase = CfgAion.inst().getConsensus().getMinerAddress();
        return TypeConverter.toJsonHex(coinbase);
    }

    @Override
    public AionBlock getBestBlock() {
        return this.ac.getBlockchain().getBestBlock();
    }

    public AionBlock getBlockTemplate() {
        // TODO: Change to follow onBlockTemplate event mode defined in internal
        // miner
        // TODO: Track multiple block templates
        AionBlock bestPendingState = ((AionPendingStateImpl) ac.getAionHub().getPendingState()).getBestBlock();

        AionPendingStateImpl.TransactionSortedSet ret = new AionPendingStateImpl.TransactionSortedSet();
        ret.addAll(ac.getAionHub().getPendingState().getPendingTransactions());

        return ac.getAionHub().getBlockchain().createNewBlock(bestPendingState, new ArrayList<>(ret), false);
    }

    public AionBlock getBlockByHash(byte[] hash) {
        return this.ac.getBlockchain().getBlockByHash(hash);
    }

    @Override
    public AionBlock getBlock(long blkNr) {
        if (blkNr == -1) {
            return this.ac.getBlockchain().getBestBlock();
        } else if (blkNr > 0) {
            return this.ac.getBlockchain().getBlockByNumber(blkNr);
        } else if (blkNr == 0) {
            AionGenesis genBlk = CfgAion.inst().getGenesis();
            return new AionBlock(genBlk.getHeader(), genBlk.getTransactionsList());
        } else {
            LOG.debug("ApiAion.getBlock - incorrect argument");
            return null;
        }
    }

    public SyncInfo getSync() {
        SyncInfo sync = new SyncInfo();
        sync.done = this.ac.isSyncComplete();
        sync.chainStartingBlkNumber = this.ac.getInitialStartingBlockNumber().orElse(0L);
        sync.blksImportMax = CfgAion.inst().getSync().getBlocksImportMax();
        sync.networkBestBlkNumber = this.ac.getNetworkBestBlockNumber().orElse(0L);
        sync.chainBestBlkNumber = this.ac.getLocalBestBlockNumber().orElse(0L);
        return sync;
    }

    public AionTransaction getTransactionByBlockHashAndIndex(byte[] hash, long index) {
        AionBlock pBlk = this.getBlockByHash(hash);
        if (pBlk == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("ApiAion.getTransactionByBlockHashAndIndex - can't find the block by the block hash");
            }
            return null;
        }

        List<AionTransaction> txList = pBlk.getTransactionsList();
        AionTransaction tx = txList.get((int) index);
        if (tx == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Can't find the transaction!");
            }
            return null;
        }

        TxRecpt receipt = this.getTransactionReceipt(tx.getHash());
        // @Jay this should not happen!
        // TODO
        if (receipt == null) {
            throw new NullPointerException();
        }

        tx.setBlockNumber(pBlk.getNumber());
        tx.setBlockHash(pBlk.getHash());
        tx.setTxIndexInBlock(index);
        tx.setNrgConsume(receipt.nrgUsed);
        return tx;
    }

    public AionTransaction getTransactionByBlockNumberAndIndex(long blkNr, long index) {
        AionBlock pBlk = this.getBlock(blkNr);
        if (pBlk == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("ApiAion.getTransactionByBlockNumberAndIndex - can't find the block by the block number");
            }
            return null;
        }

        List<AionTransaction> txList = pBlk.getTransactionsList();
        AionTransaction tx = txList.get((int) index);
        if (tx == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Can't find the transaction by the txIndex");
            }
            return null;
        }

        TxRecpt receipt = this.getTransactionReceipt(tx.getHash());
        // The receipt shouldn't be null!
        if (receipt == null) {
            throw new NullPointerException();
        }

        tx.rlpParse();
        tx.setBlockNumber(pBlk.getNumber());
        tx.setBlockHash(pBlk.getHash());
        tx.setTxIndexInBlock(index);
        tx.setNrgConsume(receipt.nrgUsed);
        return tx;
    }

    public long getBlockTransactionCountByNumber(long blkNr) {
        AionBlock pBlk = this.getBlock(blkNr);
        if (pBlk == null) {
            LOG.error("ApiAion.getTransactionByBlockNumberAndIndex - can't find the block by the block number");
            return -1;
        }

        return pBlk.getTransactionsList().size();
    }

    public long getTransactionCountByHash(byte[] hash) {
        AionBlock pBlk = this.getBlockByHash(hash);
        if (pBlk == null) {
            LOG.error("ApiAion.getTransactionByBlockNumberAndIndex - can't find the block by the block number");
            return -1;
        }
        return pBlk.getTransactionsList().size();
    }

    public long getTransactionCount(Address addr, long blkNr) {
        AionBlock pBlk = this.getBlock(blkNr);
        if (pBlk == null) {
            LOG.error("ApiAion.getTransactionByBlockNumberAndIndex - can't find the block by the block number");
            return -1;
        }
        long cnt = 0;
        List<AionTransaction> txList = pBlk.getTransactionsList();
        for (AionTransaction tx : txList) {
            if (addr.equals(tx.getFrom())) {
                cnt++;
            }
        }
        return cnt;
    }

    public AionTransaction getTransactionByHash(byte[] hash) {
        TxRecpt txRecpt = this.getTransactionReceipt(hash);

        if (txRecpt == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Can't find the transaction receipt by the txhash.");
            }
            return null;
        } else {
            AionTransaction atx = this.getTransactionByBlockNumberAndIndex(txRecpt.blockNumber,
                    txRecpt.transactionIndex);

            if (atx == null) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Can't find the transaction by the blocknumber and the txIndex.");
                }
                return null;
            }

            atx.setNrgConsume(txRecpt.nrgUsed);
            return atx;
        }
    }

    public byte[] getCode(Address addr) {
        return this.ac.getRepository().getCode(addr);
    }

    /* NOTE: only use this if you need receipts for one or small number transactions in a block.
     * (since there is n^2 work happening here to compute cumulative nrg)
     * For use cases where you need all the transaction receipts in a block, please use a different
     * strategy,
     */
    public TxRecpt getTransactionReceipt(byte[] txHash) {
        if (txHash == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("<get-transaction-receipt msg=tx-hash-null>");
            }
            return null;
        }

        AionTxInfo txInfo = this.ac.getAionHub().getBlockchain().getTransactionInfo(txHash);
        if (txInfo == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("<get-transaction-receipt msg=tx-info-null>");
            }
            return null;
        }
        AionBlock block = this.ac.getAionHub().getBlockchain().getBlockByHash(txInfo.getBlockHash());

        if (block == null) {
            if (LOG.isErrorEnabled()) {
                LOG.error("<get-transaction-receipt msg=block-null>");
            }
            return null;
        }

        // need to return txes only from main chain
        AionBlock mainBlock = this.ac.getAionHub().getBlockchain().getBlockByNumber(block.getNumber());
        if (!Arrays.equals(block.getHash(), mainBlock.getHash())) {
            LOG.debug("<get-transaction-receipt msg=hash-not-match>");
            return null;
        }

        // @Jay
        // TODO : think the good way to calculate the cumulated nrg use
        long cumulateNrg = 0L;
        for (AionTransaction atx : block.getTransactionsList()) {

            // @Jay: This should not happen!
            byte[] hash = atx.getHash();
            if (hash == null) {
                throw new NullPointerException();
            }

            AionTxInfo info = this.ac.getAionHub().getBlockchain().getTransactionInfo(hash);

            // @Jay: This should not happen!
            if (info == null) {
                throw new NullPointerException();
            }

            cumulateNrg += info.getReceipt().getEnergyUsed();
            if (Arrays.equals(txHash, hash)) {
                break;
            }
        }

        return new TxRecpt(block, txInfo, cumulateNrg, true);
    }

    public byte[] doCall(ArgTxCall _params) {
        AionTransaction tx = new AionTransaction(_params.getNonce().toByteArray(), _params.getTo(),
                _params.getValue().toByteArray(), _params.getData(), _params.getNrg(), _params.getNrgPrice());
        AionTxReceipt rec = this.ac.callConstant(tx, this.ac.getAionHub().getBlockchain().getBestBlock());
        return rec.getExecutionResult();
    }

    public long estimateGas(ArgTxCall params) {
        AionTransaction tx = new AionTransaction(params.getNonce().toByteArray(), params.getTo(),
                params.getValue().toByteArray(), params.getData(), params.getNrg(), params.getNrgPrice());
        AionTxReceipt receipt = this.ac.callConstant(tx, this.ac.getAionHub().getBlockchain().getBestBlock());
        return receipt.getEnergyUsed();
    }

    public ContractCreateResult createContract(ArgTxCall _params) {

        Address from = _params.getFrom();

        if (from == null || from.equals(Address.EMPTY_ADDRESS())) {
            return null;
        }

        ECKey key = this.getAccountKey(from.toString());

        if (key == null) {
            LOG.debug("ApiAion.createContract - null key");
            return null;
        } else {
            try {
                synchronized (pendingState) {
                    byte[] nonce = !(_params.getNonce().equals(BigInteger.ZERO)) ? _params.getNonce().toByteArray()
                            : pendingState.bestNonce(Address.wrap(key.getAddress())).toByteArray();

                    AionTransaction tx = new AionTransaction(nonce, from, null, _params.getValue().toByteArray(),
                            _params.getData(), _params.getNrg(), _params.getNrgPrice());
                    tx.sign(key);

                    pendingState.addPendingTransaction(tx);

                    ContractCreateResult c = new ContractCreateResult();
                    c.address = tx.getContractAddress();
                    c.transId = tx.getHash();
                    return c;
                }
            } catch (Exception ex) {
                LOG.error("ApiAion.createContract - exception: [{}]", ex.getMessage());

                return null;
            }
        }

    }

    // Transaction Level
    public BigInteger getBalance(String _address) throws Exception {
        return this.ac.getRepository().getBalance(Address.wrap(_address));
    }

    public BigInteger getBalance(Address _address) {
        return this.ac.getRepository().getBalance(_address);
    }

    // TODO: refactor these ad-hoc transaction creations - violates DRY and is messy

    public long estimateNrg(ArgTxCall _params) {
        if (_params == null) {
            throw new NullPointerException();
        }

        Address from = _params.getFrom();

        if (from.equals(Address.EMPTY_ADDRESS())) {
            LOG.error("<send-transaction msg=invalid-from-address>");
            return -1L;
        }

        ECKey key = this.getAccountKey(from.toString());
        if (key == null) {
            LOG.error("<send-transaction msg=account-not-found>");
            return -1L;
        }

        try {
            // Transaction is executed as local transaction, no need to retrieve the real nonce.
            byte[] nonce = BigInteger.ZERO.toByteArray();

            AionTransaction tx = new AionTransaction(nonce, _params.getTo(), _params.getValue().toByteArray(),
                    _params.getData(), _params.getNrg(), _params.getNrgPrice());
            tx.sign(key);

            return this.ac.estimateTxNrg(tx, this.ac.getAionHub().getBlockchain().getBestBlock());
        } catch (Exception ex) {
            return -1L;
        }
    }

    public byte[] sendTransaction(ArgTxCall _params) {

        Address from = _params.getFrom();

        if (from == null || from.equals(Address.EMPTY_ADDRESS())) {
            LOG.error("<send-transaction msg=invalid-from-address>");
            return null;
        }

        ECKey key = this.getAccountKey(from.toString());
        if (key == null) {
            LOG.error("<send-transaction msg=account-not-found>");
            return null;
        }

        try {
            synchronized (pendingState) {
                // TODO : temp set nrg & price to 1
                byte[] nonce = (!_params.getNonce().equals(BigInteger.ZERO)) ? _params.getNonce().toByteArray()
                        : pendingState.bestNonce(Address.wrap(key.getAddress())).toByteArray();

                AionTransaction tx = new AionTransaction(nonce, _params.getTo(), _params.getValue().toByteArray(),
                        _params.getData(), _params.getNrg(), _params.getNrgPrice());
                tx.sign(key);

                pendingState.addPendingTransaction(tx);

                return tx.getHash();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    public byte[] sendTransaction(byte[] signedTx) {
        if (signedTx == null) {
            throw new NullPointerException();
        }

        try {
            AionTransaction tx = new AionTransaction(signedTx);

            pendingState.addPendingTransaction(tx);

            return tx.getHash();
        } catch (Exception ex) {
            return ByteUtil.EMPTY_BYTE_ARRAY;
        }
    }

    // --Commented out by Inspection START (02/02/18 6:58 PM):
    // public String getNodeId() {
    // return CfgAion.inst().getId();
    // }
    // --Commented out by Inspection STOP (02/02/18 6:58 PM)

    public String[] getBootNodes() {
        return CfgAion.inst().getNodes();
    }

//    private synchronized BigInteger getTxNonce(ECKey key) {
//        return pendingState.bestNonce();
//    }

//    private synchronized BigInteger getTxNonce(ECKey key, boolean add) {
//        return add ? nm.getNonceAndAdd(Address.wrap(key.getAddress())) : nm.getNonce(Address.wrap(key.getAddress()));
//    }

    public boolean isMining() {
        return this.ac.getBlockMiner().isMining();
    }

    public int peerCount() {
        return this.ac.getAionHub().getP2pMgr().getActiveNodes().size();
    }

    // follows the ethereum standard for web3 compliance. DO NOT DEPEND ON IT.
    // Will be changed to Aion-defined spec later
    // https://github.com/ethereum/wiki/wiki/Client-Version-Strings
    private String computeClientVersion() {
        try {
            Pattern shortVersion = Pattern.compile("(\\d\\.\\d).*");
            Matcher matcher = shortVersion.matcher(System.getProperty("java.version"));
            matcher.matches();

            return Arrays.asList(
                    "Aion(J)",
                    "v" + Version.KERNEL_VERSION,
                    System.getProperty("os.name"),
                    "Java" + matcher.group(1))
                    .stream()
                    .collect(Collectors.joining("/"));
        }
        catch(Exception e) {
            LOG.debug("client version string generation failed", e);
        }

        return ("Aion(J)/v" + Version.KERNEL_VERSION);
    }

    // create a comma-separated string of supported p2p wire protocol versions
    // mainly to keep compatibility with eth_protocolVersion which returns a String
    public String p2pProtocolVersion() {
        try {
            List<Short> p2pVersions = this.ac.getAionHub().getP2pMgr().versions();
            int i = 0;
            StringBuilder b = new StringBuilder();
            for (Short v : p2pVersions) {
                b.append(ByteUtil.byteArrayToInt(ByteUtil.shortToBytes(v)));
                i++;
                if (i < p2pVersions.size())
                    b.append(",");
            }
            return b.toString();
        } catch (Exception e) {
            LOG.error("p2p protocol versions string generation failed");
            return null;
        }
    }

    public String chainId() {
        return (this.ac.getAionHub().getP2pMgr().chainId() + "");
    }

    public String getHashrate() {
        double hashrate = 0;

        // add the the hashrate computed by the internal CPU miner
        if (isMining()) {
            hashrate += this.ac.getBlockMiner().getHashrate();
        }

        hashrate += reportedHashrate;

        return Double.toString(hashrate);
    }

    // hashrate in sol/s should just be a hexadecimal representation of a BigNumber
    // right now, assuming only one external miner is connected to the kernel
    // this needs to change in the future when this client needs to support multiple external miners
    public boolean setReportedHashrate(String hashrate, String clientId) {
        try {
            reportedHashrate = Double.parseDouble(hashrate);
            return true;
        } catch(Exception e) {
            LOG.debug("api - setReportedHashrate(): bad string supplied", e);
        }

        return false;
    }

    public long getRecommendedNrgPrice() {
        return this.nrgOracle.getNrgPrice();
    }

    public long getDefaultNrgLimit() {
        return DEFAULT_NRG_LIMIT;
    }
}
