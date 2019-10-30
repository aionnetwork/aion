package org.aion.api.server.rpc3;

import java.math.BigInteger;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.types.AionTxInfo;

public interface ChainHolder {

    Block getBlockByNumber(long block);

    Block getBestBlock();

    Block getBlockByHash(byte[] hash);

    BigInteger getTotalDifficultyByHash(byte[] hash);

    AionTxInfo getTransactionInfo(byte[] transactionHash);

    BigInteger calculateReward(Long number);

    boolean isUnityForkEnabled();

    boolean submitSignature(byte[] signature, byte[] sealHash);

    byte[] submitSeed(byte[] newSeed, byte[] signingPublicKey, byte[] coinBase);

    byte[] getSeed();
}
