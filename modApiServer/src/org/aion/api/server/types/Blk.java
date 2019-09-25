package org.aion.api.server.types;

import static org.aion.util.bytes.ByteUtil.EMPTY_BYTE_ARRAY;

import java.math.BigInteger;
import java.util.List;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.base.TxUtil;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.string.StringUtils;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.StakingBlock;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JSON representation of a block, with more information TODO: one big hack atm to get this out the
 * door. Refactor to make it more OOP
 *
 * @author ali
 */
public class Blk {

    public static Object AionBlockToJson(
            Block block, boolean fullTransaction) {

        if (block == null) {
            return null;
        }

        JSONObject obj = new JSONObject();

        obj.put("number", block.getHeader().getNumber());
        obj.put("hash", StringUtils.toJsonHex(block.getHeader().getHash()));
        obj.put("parentHash", StringUtils.toJsonHex(block.getHeader().getParentHash()));
        obj.put("logsBloom", StringUtils.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", StringUtils.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", StringUtils.toJsonHex(block.getStateRoot()));
        obj.put(
                "receiptsRoot",
                StringUtils.toJsonHex(
                        block.getReceiptsRoot() == null
                                ? new byte[0]
                                : block.getReceiptsRoot()));
        obj.put("difficulty", StringUtils.toJsonHex(block.getHeader().getDifficulty()));
        obj.put("totalDifficulty", StringUtils.toJsonHex(block.getTotalDifficulty()));
        obj.put("timestamp", StringUtils.toJsonHex(block.getHeader().getTimestamp()));
        obj.put("miner", StringUtils.toJsonHex(block.getCoinbase().toString()));
        obj.put("gasUsed", StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("extraData", StringUtils.toJsonHex(block.getHeader().getExtraData()));
        obj.put("sealType", StringUtils.toJsonHex(block.getHeader().getSealType().getSealId()));
        obj.put("mainChain", block.isMainChain() ? "true" : "false");
        obj.put("antiParentHash", block.getAntiparentHash());

        if (block.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            AionBlock miningBlock = (AionBlock) block;
            obj.put("nonce", StringUtils.toJsonHex(miningBlock.getNonce()));
            obj.put("solution", StringUtils.toJsonHex(miningBlock.getHeader().getSolution()));
            obj.put("size", new NumericalValue(miningBlock.size()).toHexString());
        } else if (block.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK){
            StakingBlock stakingBlock = (StakingBlock) block;
            obj.put("seed", StringUtils.toJsonHex(stakingBlock.getHeader().getSeed()));
            obj.put("signature", StringUtils.toJsonHex(stakingBlock.getHeader().getSignature()));
            obj.put("publicKey", StringUtils.toJsonHex(stakingBlock.getHeader().getSigningPublicKey()));
            obj.put("size", new NumericalValue(stakingBlock.size()).toHexString());
        } else {
            throw new IllegalStateException("Invalid block seal type!");
        }

        JSONArray jsonTxs = new JSONArray();
        List<AionTransaction> txs = block.getTransactionsList();
        for (int i = 0; i < txs.size(); i++) {
            AionTransaction tx = txs.get(i);
            if (fullTransaction) {
                JSONObject jsonTx = new JSONObject();
                AionAddress contractAddress = TxUtil.calculateContractAddress(tx);
                jsonTx.put(
                        "contractAddress",
                        (contractAddress != null)
                                ? StringUtils.toJsonHex(contractAddress.toString())
                                : null);
                jsonTx.put("hash", StringUtils.toJsonHex(tx.getTransactionHash()));
                jsonTx.put("transactionIndex", i);
                jsonTx.put("value", StringUtils.toJsonHex(tx.getValue()));
                jsonTx.put("nrg", tx.getEnergyLimit());
                jsonTx.put("nrgPrice", StringUtils.toJsonHex(tx.getEnergyPrice()));
                jsonTx.put("gas", tx.getEnergyLimit());
                jsonTx.put("gasPrice", StringUtils.toJsonHex(tx.getEnergyPrice()));
                jsonTx.put("nonce", ByteUtil.byteArrayToLong(tx.getNonce()));
                jsonTx.put("from", StringUtils.toJsonHex(tx.getSenderAddress().toString()));
                jsonTx.put(
                        "to",
                        StringUtils.toJsonHex(
                                tx.getDestinationAddress() == null
                                        ? EMPTY_BYTE_ARRAY
                                        : tx.getDestinationAddress().toByteArray()));
                jsonTx.put("timestamp", tx.getTimeStampBI());
                jsonTx.put("input", StringUtils.toJsonHex(tx.getData()));
                jsonTx.put("blockNumber", block.getHeader().getNumber());
                jsonTxs.put(jsonTx);
            } else {
                jsonTxs.put(StringUtils.toJsonHex(tx.getTransactionHash()));
            }
        }
        obj.put("transactions", jsonTxs);
        return obj;
    }

    public static JSONObject aionBlockDetailsToJson(Block genericBlock,
        List<AionTxInfo> aionTxInfoList, Long previousTimestamp, BigInteger totalDifficulty,
        BigInteger blockReward){

        JSONObject obj = AionBlockOnlyToJson(genericBlock, totalDifficulty);

        if(obj == null){
            return null;
        }

        if (genericBlock.getNumber() == 0){
            obj.put("blockTime", 0);
        }
        else if (previousTimestamp == null){
            obj.put("blockTime", JSONObject.NULL);
        }
        else {
            obj.put("blockTime", genericBlock.getTimestamp() - previousTimestamp);
        }

        obj.put("txTrieRoot", StringUtils.toJsonHex(genericBlock.getTxTrieRoot()));
        obj.put("blockReward", new NumericalValue(blockReward).toHexString());
        JSONArray transactions = new JSONArray();
        for(AionTxInfo tx: aionTxInfoList){
            transactions.put(Tx.aionTxInfoToDetailsJSON(tx, genericBlock));
        }
        obj.put("transactions", transactions);

        return obj;
    }

    public static JSONObject AionBlockOnlyToJson(Block genericBlock, BigInteger totalDifficulty) {
        if (genericBlock == null) {
            return null;
        }

        JSONObject obj = new JSONObject();
        obj.put("number", genericBlock.getNumber());
        obj.put("hash", StringUtils.toJsonHex(genericBlock.getHash()));
        obj.put("parentHash", StringUtils.toJsonHex(genericBlock.getParentHash()));
        obj.put("logsBloom", StringUtils.toJsonHex(genericBlock.getLogBloom()));
        obj.put("transactionsRoot", StringUtils.toJsonHex(genericBlock.getTxTrieRoot()));
        obj.put("stateRoot", StringUtils.toJsonHex(genericBlock.getStateRoot()));
        obj.put(
            "receiptsRoot",
            StringUtils.toJsonHex(
                genericBlock.getReceiptsRoot() == null ? new byte[0] : genericBlock.getReceiptsRoot()));
        obj.put("difficulty", StringUtils.toJsonHex(genericBlock.getDifficulty()));
        obj.put("totalDifficulty", StringUtils.toJsonHex(totalDifficulty));

        obj.put("miner", StringUtils.toJsonHex(genericBlock.getCoinbase().toString()));
        obj.put("timestamp", StringUtils.toJsonHex(genericBlock.getTimestamp()));
        obj.put("gasUsed", StringUtils.toJsonHex(genericBlock.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", StringUtils.toJsonHex(genericBlock.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", StringUtils.toJsonHex(genericBlock.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", StringUtils.toJsonHex(genericBlock.getHeader().getEnergyLimit()));

        obj.put("sealType", StringUtils.toJsonHex(genericBlock.getHeader().getSealType().getSealId()));
        obj.put("mainChain", genericBlock.isMainChain() ? "true" : "false");
        obj.put("antiParentHash", genericBlock.getAntiparentHash());

        obj.put("extraData", StringUtils.toJsonHex(genericBlock.getExtraData()));
        obj.put("size", genericBlock.size());
        obj.put("numTransactions", genericBlock.getTransactionsList().size());


        if (genericBlock.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            AionBlock block = (AionBlock) genericBlock;
            obj.put("nonce", StringUtils.toJsonHex(block.getHeader().getNonce()));
            obj.put("solution", StringUtils.toJsonHex(block.getHeader().getSolution()));
            obj.put("size", block.size());
        } else if (genericBlock.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK){
            StakingBlock block = (StakingBlock) genericBlock;
            obj.put("seed", StringUtils.toJsonHex(block.getHeader().getSeed()));
            obj.put("signature", StringUtils.toJsonHex(block.getHeader().getSignature()));
            obj.put("publicKey", StringUtils.toJsonHex(block.getHeader().getSigningPublicKey()));
            obj.put("size", block.size());
        } else {
            throw new IllegalStateException("Invalid block seal type!");
        }

        return obj;
    }

    @SuppressWarnings("Duplicates")
    public static JSONObject AionBlockOnlyToJson(Block block) {
        if (block == null) {
            return null;
        }

        JSONObject obj = new JSONObject();

        obj.put("number", block.getHeader().getNumber());
        obj.put("hash", StringUtils.toJsonHex(block.getHeader().getHash()));
        obj.put("parentHash", StringUtils.toJsonHex(block.getHeader().getParentHash()));
        obj.put("logsBloom", StringUtils.toJsonHex(block.getLogBloom()));
        obj.put("transactionsRoot", StringUtils.toJsonHex(block.getTxTrieRoot()));
        obj.put("stateRoot", StringUtils.toJsonHex(block.getStateRoot()));
        obj.put(
                "receiptsRoot",
                StringUtils.toJsonHex(
                        block.getReceiptsRoot() == null
                                ? new byte[0]
                                : block.getReceiptsRoot()));
        obj.put("difficulty", StringUtils.toJsonHex(block.getHeader().getDifficulty()));
        obj.put("totalDifficulty", StringUtils.toJsonHex(block.getTotalDifficulty()));
        obj.put("timestamp", StringUtils.toJsonHex(block.getHeader().getTimestamp()));
        obj.put("gasUsed", StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("gasLimit", StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("nrgUsed", StringUtils.toJsonHex(block.getHeader().getEnergyConsumed()));
        obj.put("nrgLimit", StringUtils.toJsonHex(block.getHeader().getEnergyLimit()));
        obj.put("numTransactions", block.getTransactionsList().size());
        obj.put("extraData", StringUtils.toJsonHex(block.getExtraData()));
        obj.put("miner", StringUtils.toJsonHex(block.getCoinbase().toString()));
        obj.put("sealType", StringUtils.toJsonHex(block.getHeader().getSealType().getSealId()));
        obj.put("mainChain", block.isMainChain() ? "true" : "false");
        obj.put("antiParentHash", block.getAntiparentHash());

        if (block.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
            AionBlock miningBlock = (AionBlock) block;
            obj.put("nonce", StringUtils.toJsonHex(miningBlock.getHeader().getNonce()));
            obj.put("solution", StringUtils.toJsonHex(miningBlock.getHeader().getSolution()));
            obj.put("size", miningBlock.size());
        } else if (block.getHeader().getSealType() == BlockSealType.SEAL_POS_BLOCK){
            StakingBlock stakingBlock = (StakingBlock) block;
            obj.put("seed", StringUtils.toJsonHex(stakingBlock.getHeader().getSeed()));
            obj.put("signature", StringUtils.toJsonHex(stakingBlock.getHeader().getSignature()));
            obj.put("publicKey", StringUtils.toJsonHex(stakingBlock.getHeader().getSigningPublicKey()));
            obj.put("size", stakingBlock.size());
        } else {
            throw new IllegalStateException("Invalid block seal type!");
        }

        return obj;
    }
}
