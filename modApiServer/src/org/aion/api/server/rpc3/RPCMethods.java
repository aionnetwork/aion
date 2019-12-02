package org.aion.api.server.rpc3;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TxUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.mcf.db.Repository;
import org.aion.rpc.errors.RPCExceptions;
import org.aion.rpc.errors.RPCExceptions.BlockTemplateNotFoundRPCException;
import org.aion.rpc.errors.RPCExceptions.FailedToSealBlockRPCException;
import org.aion.rpc.errors.RPCExceptions.InvalidParamsRPCException;
import org.aion.rpc.errors.RPCExceptions.UnsupportedUnityFeatureRPCException;
import org.aion.rpc.server.RPCServerMethods;
import org.aion.rpc.types.RPCTypes.AccountState;
import org.aion.rpc.types.RPCTypes.BlockDetails;
import org.aion.rpc.types.RPCTypes.BlockEnum;
import org.aion.rpc.types.RPCTypes.BlockNumberEnumUnion;
import org.aion.rpc.types.RPCTypes.BlockSpecifierUnion;
import org.aion.rpc.types.RPCTypes.BlockTemplate;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.MinerStats;
import org.aion.rpc.types.RPCTypes.OpsTransaction;
import org.aion.rpc.types.RPCTypes.PongEnum;
import org.aion.rpc.types.RPCTypes.SubmissionResult;
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

            List<AionTxInfo> txInfoList = new ArrayList<>();
            logger.debug("Retrieving transactions for block: {}",
                "0x" + ByteUtil.toHexString(block.getHash()));
            for (AionTransaction transaction : block.getTransactionsList()) {
                AionTxInfo txInfo =
                        chainHolder.getTransactionInfo(transaction.getTransactionHash());
                txInfoList.add(txInfo);
            }
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

    private List<TxDetails> serializeTxDetails(List<AionTxInfo> txInfos, Block block) {
        if (txInfos == null) {
            return Collections.emptyList();
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
            return Collections.unmodifiableList(transactionDetails);
        }
    }

    private List<TxLogDetails> serializeTxLogsDetails(AionTxReceipt receipt, int index, long blockNumber) {
        List<Log> logs = receipt.getLogInfoList();
        if (logs == null) return Collections.emptyList();
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
                                        .collect(Collectors.toUnmodifiableList()),
                                blockNumber));
            }
            return Collections.unmodifiableList(logDetails);
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

    private List<TxLog> serializeTxLog(int transactionIndex, AionTxReceipt txReceipt){
        List<TxLog> txLogs = new ArrayList<>();
        for (Log log: txReceipt.getLogInfoList()){
            txLogs.add(
                new TxLog(new AionAddress(log.copyOfAddress()),
                    transactionIndex,
                    ByteArray.wrap(log.copyOfData()),
                    log.copyOfTopics().stream().map(ByteArray::new)
                        .collect(Collectors.toUnmodifiableList())));
        }
        return Collections.unmodifiableList(txLogs);
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
    public BlockTemplate getblocktemplate() {
        BlockContext context = chainHolder.getBlockTemplate();
        AionBlock block = context.block;
        return new BlockTemplate(ByteArray.wrap(block.getParentHash()), block.getNumber(), block.getHeader().getPowBoundaryBI(), ByteArray.wrap(block.getHeader().getMineHash()), context.baseBlockReward, context.transactionFee);
    }

    @Override
    public SubmissionResult submitblock(ByteArray nonce, ByteArray solution, ByteArray headerHash) {
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
    public MinerStats getMinerStats(AionAddress aionAddress) {
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
    public BigInteger eth_getBalance(AionAddress aionAddress,
        BlockNumberEnumUnion blockNumberEnumUnion) {
        if (blockNumberEnumUnion.blockEnum == BlockEnum.LATEST){
            return chainHolder.getAccountBalance(aionAddress);
        } else {
            return chainHolder.getAccountBalance(aionAddress, blockNumberEnumUnion.blockNumber);
        }
    }

    @Override
    public BigInteger eth_getTransactionCount(AionAddress aionAddress,
        BlockNumberEnumUnion blockNumberEnumUnion) {
        if (blockNumberEnumUnion.blockEnum == BlockEnum.LATEST){
            return chainHolder.getAccountNonce(aionAddress);
        } else {
            return chainHolder.getAccountNonce(aionAddress, blockNumberEnumUnion.blockNumber);
        }
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
    public List<AionAddress> personal_listAccounts() {
        return chainHolder.listAccounts();
    }

    @Override
    public boolean isExecutable(String s) {
        return methods.contains(s);
    }
}
