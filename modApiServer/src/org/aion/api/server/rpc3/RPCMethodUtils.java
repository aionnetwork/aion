package org.aion.api.server.rpc3;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.aion.api.server.external.ChainHolder;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TxUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.rpc.types.RPCTypes.BlockDetails;
import org.aion.rpc.types.RPCTypes.ByteArray;
import org.aion.rpc.types.RPCTypes.EthBlock;
import org.aion.rpc.types.RPCTypes.EthTransaction;
import org.aion.rpc.types.RPCTypes.EthTransactionForBlock;
import org.aion.rpc.types.RPCTypes.EthTransactionReceipt;
import org.aion.rpc.types.RPCTypes.EthTxReceiptLogs;
import org.aion.rpc.types.RPCTypes.TransactionUnion;
import org.aion.rpc.types.RPCTypes.TxDetails;
import org.aion.rpc.types.RPCTypes.TxLogDetails;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.StakingBlock;

/**
 * Companion class to {@link RPCMethods} which contains all the static utilities used in creating
 * the RPC responses.
 */
public class RPCMethodUtils {

    static TransactionUnion txUnionFromBlock(final Block block, boolean fullTransactions) {
        if (fullTransactions) {
            List<AionTransaction> transactionsList = block.getTransactionsList();
            return TransactionUnion.wrap(
                    IntStream.range(0, transactionsList.size())
                            .mapToObj(i -> serializeEthTransactionForBlock(transactionsList.get(i), block, i))
                            .toArray(EthTransactionForBlock[]::new));
        } else {
            return TransactionUnion.wrap(
                    block.getTransactionsList().stream()
                            .map(transaction -> ByteArray.wrap(transaction.getTransactionHash()))
                            .toArray(ByteArray[]::new));
        }
    }

    static EthBlock serializeEthBlock(Block block, TransactionUnion txUnion) {
        if (block.getHeader().getSealType().equals(BlockSealType.SEAL_POW_BLOCK)) {
            AionBlock powBlock = ((AionBlock) block);
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
                    null);
        } else {
            StakingBlock posBlock = ((StakingBlock) block);
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
                    ByteArray.wrap(posBlock.getHeader().getSigningPublicKey()));
        }
    }

    static EthTransactionForBlock serializeEthTransactionForBlock(
            AionTransaction transaction, Block block, int transactionIndex) {
        AionAddress contractAddress =
                transaction.getDestinationAddress() == null
                        ? TxUtil.calculateContractAddress(transaction)
                        : null;
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
                block.getNumber());
    }

    static EthTransaction serializeEthTransaction(
            AionTransaction transaction, Block block, int transactionIndex) {
        AionAddress contractAddress =
                transaction.getDestinationAddress() == null
                        ? TxUtil.calculateContractAddress(transaction)
                        : null;
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
                ByteArray.wrap(block.getHash()));
    }

    static EthTransactionReceipt serializeEthTxReceipt(
            AionTxInfo txInfo, Block block, int transactionIndex) {
        AionTransaction transaction = txInfo.getReceipt().getTransaction();
        AionTxReceipt txReceipt = txInfo.getReceipt();
        AionAddress contractAddress =
                transaction.getDestinationAddress() == null
                        ? TxUtil.calculateContractAddress(transaction)
                        : null;
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
                txReceipt.isSuccessful() ? (byte) 1 : (byte) 0,
                serializeReceipt(txInfo, block, transactionIndex));
    }

    static EthTxReceiptLogs[] serializeReceipt(
            AionTxInfo transaction, Block block, int transactionIndex) {
        return IntStream.range(0, transaction.getReceipt().getLogInfoList().size())
                .mapToObj(index -> {
                    Log log = transaction.getReceipt().getLogInfoList().get(index);
                    return new EthTxReceiptLogs(
                        new AionAddress(log.copyOfAddress()),
                        ByteArray.wrap(log.copyOfData()),
                        block.getNumber(),
                        transactionIndex,
                        index,
                        log.copyOfTopics().stream()
                            .map(ByteArray::new)
                            .toArray(ByteArray[]::new));
                })
                .toArray(EthTxReceiptLogs[]::new);
    }

    static BlockDetails serializeBlockDetails(
            Block block,
            BigInteger blkReward,
            BigInteger totalDiff,
            Block previousBlock,
            List<AionTxInfo> txInfoList) {
        if (block == null) {
            return null; // occurs if the requested block does not exist in the db
        } else {
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

    private static TxDetails[] serializeTxDetails(List<AionTxInfo> txInfos, Block block) {
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
                                transaction.getBeaconHash() == null
                                        ? null
                                        : ByteArray.wrap(transaction.getBeaconHash())));
            }
            return transactionDetails.toArray(new TxDetails[0]);
        }
    }

    private static TxLogDetails[] serializeTxLogsDetails(
            AionTxReceipt receipt, int index, long blockNumber) {
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
            return logDetails.toArray(new TxLogDetails[0]);
        }
    }

    static BlockDetails buildBlockDetails(Block block1, ChainHolder chainHolder) {
        if (block1 == null) {
            return null;
        } else {
            return serializeBlockDetails(
                    block1,
                    chainHolder.calculateReward(block1.getNumber()),
                    chainHolder.getTotalDifficultyByHash(block1.getHash()),
                    chainHolder.getBlockByHash(block1.getParentHash()),
                    getTransactionsFromBlock(block1, chainHolder));
        }
    }

    private static List<AionTxInfo> getTransactionsFromBlock(Block block, ChainHolder chainHolder) {
        List<AionTxInfo> txInfoList = new ArrayList<>();
        AionLoggerFactory.getLogger(LogEnum.API.name())
                .debug(
                        "Retrieving transactions for block: {}",
                        "0x" + ByteUtil.toHexString(block.getHash()));
        for (AionTransaction transaction : block.getTransactionsList()) {
            AionTxInfo txInfo = chainHolder.getTransactionInfo(transaction.getTransactionHash());
            txInfoList.add(txInfo);
        }
        return txInfoList;
    }
}
