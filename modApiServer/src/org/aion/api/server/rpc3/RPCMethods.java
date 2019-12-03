package org.aion.api.server.rpc3;

import static org.aion.base.Constants.NRG_CREATE_CONTRACT_DEFAULT;
import static org.aion.base.Constants.NRG_TRANSACTION_DEFAULT;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.aion.api.server.external.ChainHolder;
import org.aion.api.server.external.types.SyncInfo;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TransactionTypes;
import org.aion.base.TxUtil;
import org.aion.crypto.ECKey;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.rpc.errors.RPCExceptions;
import org.aion.rpc.errors.RPCExceptions.BlockTemplateNotFoundRPCException;
import org.aion.rpc.errors.RPCExceptions.FailedToSealBlockRPCException;
import org.aion.rpc.errors.RPCExceptions.InvalidParamsRPCException;
import org.aion.rpc.errors.RPCExceptions.NullReturnRPCException;
import org.aion.rpc.errors.RPCExceptions.TxFailedRPCException;
import org.aion.rpc.errors.RPCExceptions.UnsupportedUnityFeatureRPCException;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes;
import org.aion.rpc.types.RPCTypes.AccountState;
import org.aion.rpc.types.RPCTypes.BlockDetails;
import org.aion.rpc.types.RPCTypes.BlockEnum;
import org.aion.rpc.types.RPCTypes.BlockNumberEnumUnion;
import org.aion.rpc.types.RPCTypes.BlockSpecifierUnion;
import org.aion.rpc.types.RPCTypes.BlockTemplate;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.EthBlock;
import org.aion.rpc.types.RPCTypes.EthTransaction;
import org.aion.rpc.types.RPCTypes.EthTransactionForBlock;
import org.aion.rpc.types.RPCTypes.EthTransactionReceipt;
import org.aion.rpc.types.RPCTypes.EthTxReceiptLogs;
import org.aion.rpc.types.RPCTypes.MinerStats;
import org.aion.rpc.types.RPCTypes.OpsTransaction;
import org.aion.rpc.types.RPCTypes.PongEnum;
import org.aion.rpc.types.RPCTypes.SubmissionResult;
import org.aion.rpc.types.RPCTypes.SyncInfoUnion;
import org.aion.rpc.types.RPCTypes.TransactionUnion;
import org.aion.rpc.types.RPCTypes.TxCall;
import org.aion.rpc.types.RPCTypes.TxDetails;
import org.aion.rpc.types.RPCTypes.TxLog;
import org.aion.rpc.types.RPCTypes.TxLogDetails;
import org.aion.rpc.types.RPCTypes.ValidateAddressResult;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.BlockContext;
import org.aion.zero.impl.types.StakingBlock;
import org.aion.zero.impl.types.TxResponse;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

public class RPCMethods implements RPCServerMethods {

    private final ChainHolder chainHolder;
    private final Logger logger = AionLoggerFactory.getLogger(LogEnum.API.name());
    private final Set<String> methods = Set.copyOf(RPCServerMethods.listMethods());
    private final MinerStatisticsCalculator minerStats;// TODO Invalidate the contents of this class if it is not used
    private static final int MINER_STATS_BLOCK_COUNT=32;// TODO determine if both variables are needed
    private static final int MINER_NUM_BLOCKS_FOR_CALC_COUNT=32;

    public RPCMethods(ChainHolder chainHolder) {
        this.chainHolder = chainHolder;
        this.minerStats= new MinerStatisticsCalculator(chainHolder, MINER_STATS_BLOCK_COUNT, MINER_NUM_BLOCKS_FOR_CALC_COUNT);
    }

    //For testing
    RPCMethods(ChainHolder chainHolder,
        MinerStatisticsCalculator minerStats){
        this.chainHolder = chainHolder;
        this.minerStats = minerStats;
    }

    @Override
    public AionAddress personal_ecRecover(ByteArray dataThatWasSigned, ByteArray signature) {
        logger.debug("Executing personal_ecRecover({},{})", dataThatWasSigned, signature);
        ISignature signature1 = SignatureFac.fromBytes(signature.toBytes());
        if (signature1 == null) {
            throw InvalidParamsRPCException.INSTANCE;
        }
        byte[] pk = signature1.getAddress();
        if (SignatureFac.verify(dataThatWasSigned.toBytes(), signature1)) {
            return new AionAddress(pk);
        } else return null;
    }

    @Override
    public ByteArray getseed() {
        if (!chainHolder.isUnityForkEnabled()) throw UnsupportedUnityFeatureRPCException.INSTANCE;
        byte[] result = chainHolder.getSeed();
        if (result == null) {
            return null;
        } else {
            return ByteArray.wrap(result);
        }
    }

    @Override
    public ByteArray submitseed(
            ByteArray newSeed, ByteArray signingPublicKey, AionAddress coinBase) {
        if (!chainHolder.isUnityForkEnabled()) throw UnsupportedUnityFeatureRPCException.INSTANCE;
        byte[] result =
                chainHolder.submitSeed(newSeed.toBytes(), signingPublicKey.toBytes(), coinBase.toByteArray());
        if (result == null) {
            return null;
        } else {
            return ByteArray.wrap(result);
        }
    }

    @Override
    public Boolean submitsignature(ByteArray signature, ByteArray sealHash) {
        if (!chainHolder.isUnityForkEnabled()) throw UnsupportedUnityFeatureRPCException.INSTANCE;
        if(!chainHolder.canSeal(sealHash.toBytes()))throw BlockTemplateNotFoundRPCException.INSTANCE;
        return chainHolder.submitSignature(signature.toBytes(), sealHash.toBytes());
    }

    public BlockDetails blockDetailsByEnum(BlockEnum block) {
        switch (block) {
            case LATEST:
                return serializeBlockDetails(chainHolder.getBestBlock());
            case EARLIEST:
                return serializeBlockDetails(chainHolder.getBlockByNumber(0L));
            case PENDING:
            default:
                throw RPCExceptions.InvalidParamsRPCException.INSTANCE;
        }
    }

    private BlockDetails serializeBlockDetails(Block block) {
        if (block == null) {
            return null; // occurs if the requested block does not exist in the db
        } else {
            final BigInteger blkReward =
                    chainHolder.calculateReward(
                            block.getHeader().getNumber()); // get the block reward
            final BigInteger totalDiff =
                    chainHolder.getTotalDifficultyByHash(
                            block.getHash()); // get the total difficulty

            List<AionTxInfo> txInfoList = getTransactionsFromBlock(block);
            Block previousBlock =
                    chainHolder.getBlockByHash(block.getParentHash()); // get the parent block
            final Long previousTimestamp;
            if (previousBlock == null) {
                previousTimestamp = null;
            } else {
                previousTimestamp =
                        previousBlock
                                .getTimestamp(); // set the timestamp to be used to calculate the
                                                 // block time
            }
            if (block.getHeader()
                    .getSealType()
                    .equals(BlockSealType.SEAL_POW_BLOCK)) // return a block based on the seal type
            return new BlockDetails(
                        block.getNumber(),
                        ByteArray.wrap(block.getHash()),
                        ByteArray.wrap(block.getParentHash()),
                        ByteArray.wrap(block.getLogBloom()),
                        ByteArray.wrap(block.getTxTrieRoot()),
                        ByteArray.wrap(block.getStateRoot()),
                        ByteArray.wrap(block.getReceiptsRoot()),
                        ByteUtil.bytesToBigInteger(block.getDifficulty()),
                        totalDiff,
                        block.getCoinbase(),
                        block.getTimestamp(),
                        block.getNrgConsumed(),
                        block.getNrgLimit(),
                        block.getNrgConsumed(),
                        block.getNrgLimit(),
                        block.getHeader().getSealType().getSealId(),
                        block.isMainChain(),
                        ByteArray.wrap(block.getHeader().getExtraData()),
                        block.size(),
                        block.getTransactionsList().size(),
                        ByteArray.wrap(block.getTxTrieRoot()),
                        blkReward,
                        serializeTxDetails(txInfoList, block),
                        ByteArray.wrap(((AionBlock) block).getNonce()),
                        ByteArray.wrap(((AionBlock) block).getHeader().getSolution()),
                        null,
                        null,
                        null,
                        previousBlock == null
                                ? null
                                : Math.toIntExact((block.getTimestamp() - previousTimestamp)));
            else
                return new BlockDetails(
                        block.getNumber(),
                        ByteArray.wrap(block.getHash()),
                        ByteArray.wrap(block.getParentHash()),
                        ByteArray.wrap(block.getLogBloom()),
                        ByteArray.wrap(block.getTxTrieRoot()),
                        ByteArray.wrap(block.getStateRoot()),
                        ByteArray.wrap(block.getReceiptsRoot()),
                        ByteUtil.bytesToBigInteger(block.getDifficulty()),
                        totalDiff,
                        block.getCoinbase(),
                        block.getTimestamp(),
                        block.getNrgConsumed(),
                        block.getNrgLimit(),
                        block.getNrgConsumed(),
                        block.getNrgLimit(),
                        block.getHeader().getSealType().getSealId(),
                        block.isMainChain(),
                        ByteArray.wrap(block.getHeader().getExtraData()),
                        block.size(),
                        block.getTransactionsList().size(),
                        ByteArray.wrap(block.getTxTrieRoot()),
                        blkReward,
                        serializeTxDetails(txInfoList, block),
                        null,
                        null,
                        ByteArray.wrap(((StakingBlock) block).getHeader().getSeed()),
                        ByteArray.wrap(((StakingBlock) block).getHeader().getSignature()),
                        ByteArray.wrap(((StakingBlock) block).getHeader().getSigningPublicKey()),
                        previousBlock == null
                                ? null
                                : Math.toIntExact((block.getTimestamp() - previousTimestamp)));
        }
    }

    private List<AionTxInfo> getTransactionsFromBlock(Block block) {
        List<AionTxInfo> txInfoList = new ArrayList<>();
        logger.debug("Retrieving transactions for block: {}",
            "0x" + ByteUtil.toHexString(block.getHash()));
        for (AionTransaction transaction : block.getTransactionsList()) {
            AionTxInfo txInfo =
                    chainHolder.getTransactionInfo(transaction.getTransactionHash());
            txInfoList.add(txInfo);
        }
        return txInfoList;
    }

    private TxDetails[] serializeTxDetails(List<AionTxInfo> txInfos, Block block) {
        if (txInfos == null) {
            return new TxDetails[0];
        } else {
            List<TxDetails> transactionDetails = new ArrayList<>();
            for (int i = 0, txInfosSize = txInfos.size(); i < txInfosSize; i++) {
                AionTxInfo info = txInfos.get(i);
                AionTransaction transaction = info.getReceipt().getTransaction();
                AionAddress contractAddress = TxUtil.calculateContractAddress(transaction);
                transactionDetails.add(
                        new TxDetails(
                                contractAddress,
                                ByteArray.wrap(transaction.getTransactionHash()),
                                i,
                                transaction.getValueBI(),
                                transaction.getEnergyLimit(),
                                transaction.getEnergyPrice(),
                                transaction.getEnergyLimit(),
                                transaction.getEnergyPrice(),
                                transaction.getNonceBI().longValue(),
                                transaction.getSenderAddress(),
                                transaction.getDestinationAddress(),
                                block.getTimestamp(),
                                ByteArray.wrap(transaction.getData()),
                                block.getHeader().getNumber(),
                                ByteArray.wrap(block.getHash()),
                                info.getReceipt().getError(),
                                transaction.getType(),
                                info.getReceipt().getEnergyUsed(),
                                info.getReceipt().getEnergyUsed(),
                                info.hasInternalTransactions(),
                                serializeTxLogsDetails(
                                        info.getReceipt(), i, block.getHeader().getNumber()),
                                transaction.getBeaconHash() == null? null : ByteArray.wrap(transaction.getBeaconHash())));
            }
            return transactionDetails.toArray(new TxDetails[0]);
        }
    }

    private TxLogDetails[] serializeTxLogsDetails(AionTxReceipt receipt, int index, long blockNumber) {
        List<Log> logs = receipt.getLogInfoList();
        if (logs == null) return new TxLogDetails[0];
        else {
            List<TxLogDetails> logDetails = new ArrayList<>();
            for (int i = 0; i < logs.size(); i++) {
                Log log = logs.get(i);
                logDetails.add(
                        new TxLogDetails(
                                new AionAddress(log.copyOfAddress()),
                                index,
                                ByteArray.wrap(log.copyOfData()),
                                log.copyOfTopics().stream()
                                        .map(ByteArray::new)
                                        .toArray(ByteArray[]::new),
                                blockNumber));
            }
            return logDetails.toArray(new TxLogDetails[]{});
        }
    }


    private OpsTransaction serializeOpsTransaction(AionTxInfo transactionInfo, Block block,
        AionTransaction aionTransaction, AionTxReceipt txReceipt) {
        return new OpsTransaction(
            block.getTimestamp(),
            ByteArray.wrap(aionTransaction.getTransactionHash()),
            block.getNumber(),
            ByteArray.wrap(block.getHash()),
            aionTransaction.getNonceBI(),
            aionTransaction.getSenderAddress(),
            aionTransaction.getDestinationAddress(),
            aionTransaction.getValueBI(),
            aionTransaction.getEnergyPrice(),
            txReceipt.getEnergyUsed(),
            ByteArray.wrap(aionTransaction.getData()),
            transactionInfo.getIndex(),
            ByteArray.wrap(aionTransaction.getBeaconHash()),
            serializeTxLog(transactionInfo.getIndex(), txReceipt)
        );
    }

    private TxLog[] serializeTxLog(int transactionIndex, AionTxReceipt txReceipt){
        List<TxLog> txLogs = new ArrayList<>();
        for (Log log: txReceipt.getLogInfoList()){
            txLogs.add(
                new TxLog(new AionAddress(log.copyOfAddress()),
                    transactionIndex,
                    ByteArray.wrap(log.copyOfData()),
                    log.copyOfTopics().stream().map(ByteArray::new)
                        .toArray(ByteArray[]::new)));
        }
        return txLogs.toArray(new TxLog[0]);
    }
    @Override
    public BlockDetails ops_getBlockDetails(BlockSpecifierUnion blockSpecifierUnion) {
        logger.debug("Executing ops_getBlockDetails({})", blockSpecifierUnion.encode());
        if (blockSpecifierUnion.blockNumber != null)
            return serializeBlockDetails(
                    chainHolder.getBlockByNumber(blockSpecifierUnion.blockNumber));
        else if (blockSpecifierUnion.blockEnum != null)
            return blockDetailsByEnum(blockSpecifierUnion.blockEnum);
        else if (blockSpecifierUnion.hash != null)
            return serializeBlockDetails(
                    chainHolder.getBlockByHash(blockSpecifierUnion.hash.toBytes()));
        else throw InvalidParamsRPCException.INSTANCE;
    }

    @Override
    public BlockTemplate getBlockTemplate() {
        BlockContext context = chainHolder.getBlockTemplate();
        AionBlock block = context.block;
        return new BlockTemplate(ByteArray.wrap(block.getParentHash()), block.getNumber(), block.getHeader().getPowBoundaryBI(), ByteArray.wrap(block.getHeader().getMineHash()), context.baseBlockReward, context.transactionFee);
    }

    @Override
    public SubmissionResult submitBlock(ByteArray nonce, ByteArray solution, ByteArray headerHash) {
        if (!chainHolder.canSeal(headerHash.toBytes()))
            throw BlockTemplateNotFoundRPCException.INSTANCE;
        try {
            return new SubmissionResult(chainHolder.submitBlock(nonce.toBytes(), solution.toBytes(), headerHash.toBytes()));
        } catch (Exception e) {
            throw FailedToSealBlockRPCException.INSTANCE;
        }
    }

    @Override
    public ValidateAddressResult validateaddress(AionAddress aionAddress) {
        boolean addressIsMine = chainHolder.addressExists(aionAddress);
        return new ValidateAddressResult(true,//This should always be true since we are using an instance of aionAddress
            aionAddress,
            addressIsMine);
    }

    @Override
    public BigInteger getDifficulty() {
        return chainHolder.getBestPOWBlock().getDifficultyBI();
    }

    @Override
    public MinerStats getMinerStatistics(AionAddress aionAddress) {
        return minerStats.getStats(aionAddress);
    }

    @Override
    public PongEnum ping() {
        return PongEnum.PONG;
    }

    @Override
    public AccountState ops_getAccountState(AionAddress aionAddress) {
        org.aion.base.AccountState accountState = chainHolder.getAccountState(aionAddress);
        return new AccountState(aionAddress,
            chainHolder.blockNumber(),
            accountState.getBalance(),
            accountState.getNonce());
    }

    @Override
    public OpsTransaction ops_getTransaction(ByteArray hash) {
        final AionTxInfo transactionInfo = chainHolder.getTransactionInfo(hash.toBytes());
        if (transactionInfo == null) {
            return null;
        }else {
            final Block block = chainHolder.getBlockByHash(transactionInfo.blockHash.toBytes());
            if (block == null) {
                return null; // We cannot create the response if the block is null
                            // Consider creating a new error class for this
            }
            AionTxReceipt txReceipt = transactionInfo.getReceipt();
            if (txReceipt == null) {
                return null; // We cannot create a response if there is not transaction receipt
            }
            AionTransaction aionTransaction = transactionInfo.getReceipt().getTransaction();
            return serializeOpsTransaction(transactionInfo, block, aionTransaction, txReceipt);
        }
    }

    @Override
    public BlockDetails ops_getBlockDetailsByNumber(Long blockNumber) {
        return serializeBlockDetails(chainHolder.getBlockByNumber(blockNumber));
    }

    @Override
    public BlockDetails ops_getBlockDetailsByHash(ByteArray blockHash) {
        return serializeBlockDetails(chainHolder.getBlockByHash(blockHash.toBytes()));
    }

    @Override
    public BigInteger eth_getBalance(
            AionAddress aionAddress, BlockNumberEnumUnion blockNumberEnumUnion) {
        final BigInteger res;
        if (blockNumberEnumUnion.blockEnum == BlockEnum.LATEST) { // best block
            res = chainHolder.getAccountBalance(aionAddress, chainHolder.blockNumber());
        } else if (blockNumberEnumUnion.blockEnum == BlockEnum.PENDING) { // pending block
            res = chainHolder.getAccountBalance(aionAddress);
        } else if (blockNumberEnumUnion.blockEnum == BlockEnum.EARLIEST) { // genesis block
            res = chainHolder.getAccountBalance(aionAddress, 0L);
        } else {
            res = chainHolder.getAccountBalance(aionAddress, blockNumberEnumUnion.blockNumber);
        }
        return res;
    }

    @Override
    public BigInteger eth_getTransactionCount(
            AionAddress aionAddress, BlockNumberEnumUnion blockNumberEnumUnion) {
        final BigInteger res;
        if (blockNumberEnumUnion.blockEnum == BlockEnum.LATEST) { // best block
            res = chainHolder.getAccountNonce(aionAddress, chainHolder.blockNumber());
        } else if (blockNumberEnumUnion.blockEnum == BlockEnum.PENDING) { // pending block
            res = chainHolder.getAccountNonce(aionAddress);
        } else if (blockNumberEnumUnion.blockEnum == BlockEnum.EARLIEST) { // genesis block
            res = chainHolder.getAccountNonce(aionAddress, 0L);
        } else {
            res = chainHolder.getAccountNonce(aionAddress, blockNumberEnumUnion.blockNumber);
        }
        return res;
    }


    @Override
    public Boolean personal_unlockAccount(AionAddress aionAddress, String password, Integer timeout) {
        return chainHolder.unlockAccount(aionAddress, password, timeout);
    }

    @Override
    public Boolean personal_lockAccount(AionAddress aionAddress, String password) {
        return chainHolder.lockAccount(aionAddress, password);
    }

    @Override
    public AionAddress personal_newAccount(String password) {
        return chainHolder.newAccount(password);
    }

    @Override
    public AionAddress[] personal_listAccounts() {
        return chainHolder.listAccounts().toArray(new AionAddress[]{});
    }

    @Override
    public Long eth_blockNumber() {
        return chainHolder.blockNumber();
    }

    @Override
    public ByteArray eth_call(TxCall txCall, BlockNumberEnumUnion block) {
        AionTransaction transaction = transactionForTxCall(txCall);
        AionTxReceipt receipt = chainHolder.call(transaction,
            blockFromBlockNumEnumUnion(block));

        if (receipt == null) {
            // signal to the client that an issue occurred
            throw new NullReturnRPCException("VM returned null for the transaction call.");
        }else {
            return ByteArray.wrap(receipt.getTransactionOutput());
        }
    }

    @Override
    public SyncInfoUnion eth_syncing() {
        SyncInfo info = chainHolder.getSyncInfo();
        if (info==null){
            throw new NullReturnRPCException("Unable to determine the kernel's current sync state.");
        } else if (info.isDone()){
            return SyncInfoUnion.wrap(false);
        } else {
            return SyncInfoUnion.wrap(new RPCTypes.SyncInfo(info.getChainStartingBlkNumber(), info.getChainBestBlkNumber(), info.getNetworkBestBlkNumber()));
        }
    }

    @Override
    public ByteArray eth_sendRawTransaction(ByteArray transaction) {
        return doSend(TxUtil.decode(transaction.toBytes()));
    }

    @Override
    public ByteArray eth_sendTransaction(TxCall transaction) {
        return doSend(transactionForTxSend(transaction));
    }

    public EthTransaction eth_getTransactionByHash(ByteArray hash) {
        AionTxInfo txInfo = chainHolder.getTransactionInfo(hash.toBytes());
        if (txInfo == null) return null;
        else {
            Block block = chainHolder.getBlockByHash(txInfo.getBlockHash());
            return serializeEthTransaction(
                    txInfo.getReceipt().getTransaction(), block, txInfo.getIndex());
        }
    }

    @Override
    public EthTransactionReceipt eth_getTransactionReceipt(ByteArray hash) {
        AionTxInfo txInfo = chainHolder.getTransactionInfo(hash.toBytes());
        if (txInfo == null) return null;
        else {
            Block block = chainHolder.getBlockByHash(txInfo.getBlockHash());
            return serializeEthTxReceipt(txInfo, block, txInfo.getIndex());
        }
    }

    @Override
    public EthBlock eth_getBlockByNumber(Long block, Boolean fullTransactions) {
        Block dbBlock = chainHolder.getBlockByNumber(block);
        // Safe to use a boxed boolean here since the converter defaults to false
        // if the boolean value is null
        return serializeEthBlock(dbBlock, txUnionFromBlock(dbBlock, fullTransactions));
    }

    @Override
    public EthBlock eth_getBlockByHash(ByteArray block, Boolean fullTransactions) {
        Block dbBlock = chainHolder.getBlockByHash(block.toBytes());
        // Safe to use a boxed boolean here since the converter defaults to false
        // if the boolean value is null
        return serializeEthBlock(dbBlock, txUnionFromBlock(dbBlock, fullTransactions));
    }

    private TransactionUnion txUnionFromBlock(final Block block, boolean fullTransactions){
        if (fullTransactions){
            List<AionTransaction> transactionsList = block.getTransactionsList();
            return TransactionUnion.wrap(IntStream.range(0, transactionsList.size())
                .mapToObj(i ->
                    serializeEthTransactionForBlock(transactionsList.get(i), block, i))
                .toArray(EthTransactionForBlock[]::new));
        }
        else {
            return TransactionUnion.wrap(block.getTransactionsList().stream()
                .map(transaction -> ByteArray.wrap(transaction.getTransactionHash()))
                .toArray(ByteArray[]::new));
        }
    }
    private EthBlock serializeEthBlock(Block block, TransactionUnion txUnion){
        if (block.getHeader().getSealType().equals(BlockSealType.SEAL_POW_BLOCK)){
            AionBlock powBlock = ((AionBlock)block);
            return new EthBlock(
                block.getNumber(),
                ByteArray.wrap(block.getHash()),
                ByteArray.wrap(block.getParentHash()),
                ByteArray.wrap(block.getLogBloom()),
                ByteArray.wrap(block.getTxTrieRoot()),
                ByteArray.wrap(block.getStateRoot()),
                ByteArray.wrap(block.getReceiptsRoot()),
                block.getDifficultyBI(),
                block.getTotalDifficulty(),
                block.getTimestamp(),
                block.getCoinbase(),
                block.getNrgConsumed(),
                block.getNrgLimit(),
                block.getNrgConsumed(),
                block.getNrgLimit(),
                BlockSealType.SEAL_POW_BLOCK.getSealId(),
                block.isMainChain(),
                block.size(),
                txUnion,
                ByteArray.wrap(powBlock.getNonce()),
                ByteArray.wrap(powBlock.getHeader().getSolution()),
                null,
                null,
                null
            );
        } else {
            StakingBlock posBlock = ((StakingBlock)block);
            return new EthBlock(
                block.getNumber(),
                ByteArray.wrap(block.getHash()),
                ByteArray.wrap(block.getParentHash()),
                ByteArray.wrap(block.getLogBloom()),
                ByteArray.wrap(block.getTxTrieRoot()),
                ByteArray.wrap(block.getStateRoot()),
                ByteArray.wrap(block.getReceiptsRoot()),
                block.getDifficultyBI(),
                block.getTotalDifficulty(),
                block.getTimestamp(),
                block.getCoinbase(),
                block.getNrgConsumed(),
                block.getNrgLimit(),
                block.getNrgConsumed(),
                block.getNrgLimit(),
                BlockSealType.SEAL_POW_BLOCK.getSealId(),
                block.isMainChain(),
                block.size(),
                txUnion,
                null,
                null,
                ByteArray.wrap(posBlock.getHeader().getSeed()),
                ByteArray.wrap(posBlock.getHeader().getSignature()),
                ByteArray.wrap(posBlock.getHeader().getSigningPublicKey())
            );
        }
    }

    private EthTransactionForBlock serializeEthTransactionForBlock(AionTransaction transaction, Block block,
        int transactionIndex){
        AionAddress contractAddress = transaction.getDestinationAddress() == null? TxUtil.calculateContractAddress(transaction): null;
        return new EthTransactionForBlock(
            ByteArray.wrap(transaction.getTransactionHash()),
            transactionIndex,
            transaction.getEnergyLimit(),
            transaction.getEnergyPrice(),
            transaction.getEnergyLimit(),
            transaction.getEnergyPrice(),
            contractAddress,
            transaction.getSenderAddress(),
            transaction.getDestinationAddress(),
            block.getTimestamp(),
            ByteArray.wrap(transaction.getData()),
            block.getNumber()
        );
    }

    private EthTransaction serializeEthTransaction(AionTransaction transaction, Block block,
        int transactionIndex){
        AionAddress contractAddress = transaction.getDestinationAddress() == null? TxUtil.calculateContractAddress(transaction): null;
        return new EthTransaction(
            ByteArray.wrap(transaction.getTransactionHash()),
            transactionIndex,
            transaction.getEnergyLimit(),
            transaction.getEnergyPrice(),
            transaction.getEnergyLimit(),
            transaction.getEnergyPrice(),
            contractAddress,
            transaction.getSenderAddress(),
            transaction.getDestinationAddress(),
            block.getTimestamp(),
            ByteArray.wrap(transaction.getData()),
            block.getNumber(),
            ByteArray.wrap(block.getHash())
        );
    }

    private EthTransactionReceipt serializeEthTxReceipt(AionTxInfo txInfo, Block block,
        int transactionIndex){
        AionTransaction transaction = txInfo.getReceipt().getTransaction();
        AionTxReceipt txReceipt = txInfo.getReceipt();
        AionAddress contractAddress = transaction.getDestinationAddress() == null? TxUtil.calculateContractAddress(transaction): null;
        return new EthTransactionReceipt(
            ByteArray.wrap(transaction.getTransactionHash()),
            transactionIndex,
            block.getNumber(),
            ByteArray.wrap(block.getHash()),
            txReceipt.getEnergyUsed(),
            transaction.getEnergyPrice(),
            txReceipt.getEnergyUsed(),
            transaction.getEnergyPrice(),
            transaction.getEnergyLimit(),
            block.getNrgConsumed(),
            block.getNrgConsumed(),
            contractAddress,
            transaction.getSenderAddress(),
            transaction.getDestinationAddress(),
            ByteArray.wrap(txReceipt.getBloomFilter().data),
            ByteArray.wrap(block.getReceiptsRoot()),
            txReceipt.isSuccessful() ? (byte)1 : (byte)0,
            serializeReceipt(txInfo, block, transactionIndex)
        );
    }

    private EthTxReceiptLogs[] serializeReceipt(AionTxInfo transaction, Block block, int transactionIndex){
        return IntStream.range(0,transaction.getReceipt().getLogInfoList().size())
            .mapToObj(index->{
                Log log = transaction.getReceipt().getLogInfoList().get(index);
                return new EthTxReceiptLogs(
                    new AionAddress(log.copyOfAddress()),
                    ByteArray.wrap(log.copyOfData()),
                    block.getNumber(),
                    transactionIndex,
                    index,
                    log.copyOfTopics().stream().map(ByteArray::new).toArray(ByteArray[]::new)
                );
            }).toArray(EthTxReceiptLogs[]::new);
    }

    private ByteArray doSend(AionTransaction transaction){
        Pair<byte[], TxResponse> hashResponseTuple = chainHolder.sendTransaction(transaction);
        if (!hashResponseTuple.getRight().isFail()){
            return ByteArray.wrap(hashResponseTuple.getLeft());
        }
        else {
            throw new TxFailedRPCException(getError(hashResponseTuple.getRight()));
        }
    }

    /**
     *
     * @param rsp the tx response
     * @return a message containing the error message. This will be empty if the transaction succeeded.
     */
    private String getError(TxResponse rsp) {
        switch (rsp) {
            case REPAYTX_POOL_EXCEPTION:
                return "Repaid transaction wasn't found in the pool";
            case INVALID_TX:
                return "Invalid transaction object";
            case INVALID_TX_NRG_PRICE:
                return "Invalid transaction energy price";
            case INVALID_FROM:
                return "Invalid from address provided";
            case INVALID_ACCOUNT:
                return "Account not found, or not unlocked";
            case REPAYTX_LOWPRICE:
                return "Repaid transaction needs to have a higher energy price";
            case DROPPED:
                return "Transaction dropped";
            case EXCEPTION:
            default:
                return "Transaction status unknown";
        }
    }

    AionTransaction transactionForTxCall(TxCall txCall) {
        return AionTransaction.createWithoutKey(
            txCall.nonce.toByteArray(),
            txCall.from,
            txCall.to,
            txCall.value.toByteArray(),
            txCall.data.toBytes(),
            txCall.gas == null? Long.MAX_VALUE: txCall.gas,
            txCall.gasPrice == null? chainHolder.getRecommendedNrg(): txCall.gasPrice,
            txCall.type,
            txCall.beaconHash == null? null: txCall.beaconHash.toBytes()
        );
    }

    AionTransaction transactionForTxSend(TxCall txCall) {
        final long gas;
        if (txCall.gas == null){
            gas = txCall.to== null ? NRG_CREATE_CONTRACT_DEFAULT: NRG_TRANSACTION_DEFAULT;
        }
        else {
            gas = txCall.gas;
        }

        ECKey key = chainHolder.getKey(txCall.from);
        if (key == null) { // the account is not unlocked
            throw new TxFailedRPCException(getError(TxResponse.INVALID_ACCOUNT));
        }
        return AionTransaction.create(
            key,
            txCall.nonce.toByteArray(),
            txCall.to,
            txCall.value.toByteArray(),
            txCall.data.toBytes(),
            gas,
            txCall.gasPrice == null? chainHolder.getRecommendedNrg(): txCall.gasPrice,
            txCall.type,
            txCall.beaconHash == null? null: txCall.beaconHash.toBytes()
        );
    }

    private Block blockFromBlockNumEnumUnion( BlockNumberEnumUnion blockNumberEnumUnion){
        if (blockNumberEnumUnion.blockEnum == BlockEnum.LATEST){
            return chainHolder.getBestBlock();
        }else {
            return chainHolder.getBlockByNumber(blockNumberEnumUnion.blockNumber);
        }
    }

    @Override
    public boolean isExecutable(String s) {
        return methods.contains(s);
    }
}
