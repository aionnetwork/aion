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
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.rpc.errors.RPCExceptions;
import org.aion.rpc.errors.RPCExceptions.InvalidParamsRPCException;
import org.aion.rpc.server.OpsRPC;
import org.aion.rpc.types.RPCTypes.BlockDetails;
import org.aion.rpc.types.RPCTypes.BlockEnum;
import org.aion.rpc.types.RPCTypes.BlockSpecifierUnion;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.TransactionDetails;
import org.aion.rpc.types.RPCTypes.TxLogDetails;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.StakingBlock;
import org.slf4j.Logger;

public class OpsRPCImpl implements OpsRPC {
    private final Logger logger = AionLoggerFactory.getLogger(LogEnum.API.name());
    private final Set<String> methods = listMethods();
    private final ChainHolder chainHolder;

    public OpsRPCImpl(ChainHolder chainHolder) {
        this.chainHolder = chainHolder;
    }

    @Override
    public boolean isExecutable(String s) {
        return methods.contains(s);
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
            return null; //occurs if the requested block does not exist in the db
        } else {
            final BigInteger blkReward = chainHolder.calculateReward(block.getHeader().getNumber());// get the block reward
            final BigInteger totalDiff = chainHolder.getTotalDifficultyByHash(block.getHash());// get the total difficulty

            List<AionTxInfo> txInfoList = new ArrayList<>();
            logger.debug("Retrieving transactions for block: {}",block.getHash());
            for (AionTransaction transaction : block.getTransactionsList()) {
                AionTxInfo txInfo = chainHolder.getTransactionInfo(transaction.getTransactionHash());
                txInfoList.add(txInfo);
            }
            Block previousBlock = chainHolder.getBlockByHash(block.getParentHash());// get the parent block
            final Long previousTimestamp;
            if (previousBlock == null) {
                previousTimestamp = null;
            } else {
                previousTimestamp = previousBlock.getTimestamp();// set the timestamp to be used to calculate the block time
            }
            if (block.getHeader().getSealType().equals(BlockSealType.SEAL_POW_BLOCK))// return a block based on the seal type
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

    private List<TransactionDetails> serializeTxDetails(List<AionTxInfo> txInfos, Block block) {
        if (txInfos == null) {
            return Collections.emptyList();
        } else {
            List<TransactionDetails> transactionDetails = new ArrayList<>();
            for (int i = 0, txInfosSize = txInfos.size(); i < txInfosSize; i++) {
                AionTxInfo info = txInfos.get(i);
                AionTransaction transaction = info.getReceipt().getTransaction();
                AionAddress contractAddress = TxUtil.calculateContractAddress(transaction);
                transactionDetails.add(
                        new TransactionDetails(
                                contractAddress,
                                ByteArray.wrap(transaction.getTransactionHash()),
                                i,
                                transaction.getValueBI(),
                                transaction.getEnergyLimit(),
                                transaction.getEnergyPrice(),
                                transaction.getEnergyLimit(),
                                transaction.getEnergyPrice(),
                                ByteArray.wrap(transaction.getNonce()),
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
                                serializeTxLogs(
                                        info.getReceipt(), i, block.getHeader().getNumber())));
            }
            return Collections.unmodifiableList(transactionDetails);
        }
    }

    private List<TxLogDetails> serializeTxLogs(AionTxReceipt receipt, int index, long blockNumber) {
        List<Log> logs = receipt.getLogInfoList();
        if (logs == null) return Collections.emptyList();
        else {
            List<TxLogDetails> logDetails = new ArrayList<>();
            for (int i = 0; i < logs.size(); i++) {
                Log log = logs.get(i);
                logDetails.add(
                    new TxLogDetails(new AionAddress(log.copyOfAddress()), index, ByteArray.wrap(log.copyOfData()), log.copyOfTopics().stream().map(ByteArray::new).collect(
                        Collectors.toUnmodifiableList()), blockNumber)
                );
            }
            return Collections.unmodifiableList(logDetails);
        }
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
}
