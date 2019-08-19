package org.aion.api.server.rpc2;

import java.math.BigInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.api.server.rpc2.autogen.Rpc;
import org.aion.api.server.rpc2.autogen.pod.CallRequest;
import org.aion.api.server.rpc2.autogen.pod.Transaction;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.impl.types.StakedBlockHeader;
import org.aion.zero.impl.types.StakingBlock;

public class RpcImpl implements Rpc {

    private IAionChain ac;
    private ReentrantLock blockTemplateLock;

    RpcImpl(final IAionChain _ac, final ReentrantLock _lock) {
        if (_ac == null || _lock == null) {
            throw  new NullPointerException();
        }

        ac = _ac;
        blockTemplateLock = _lock;
    }

    @Override
    public byte[] getseed() {
        if (! ac.getAionHub().getBlockchain().isUnityForkEnabled()) {
            throw new IllegalStateException("UnityForkNotEnabled!");
        }

        return ac.getBlockchain().getSeed();
    }

    @Override
    public byte[] submitseed(byte[] newSeed, byte[] pubKey) throws Exception {
        if (! ac.getAionHub().getBlockchain().isUnityForkEnabled()) {
            throw new IllegalStateException("UnityForkNotEnabled!");
        }

        if (newSeed == null || pubKey == null) {
            throw new NullPointerException();
        }

        if (newSeed.length != StakedBlockHeader.SEED_LENGTH
                || pubKey.length != StakedBlockHeader.PUBKEY_LENGTH) {
            throw new IllegalArgumentException("Invalid arguments length");
        }

        blockTemplateLock.lock();
        try {
            StakingBlock template =
                (StakingBlock)
                    ac.getBlockchain()
                        .createStakingBlockTemplate(
                            ac.getAionHub().getPendingState().getPendingTransactions()
                            , pubKey
                            , newSeed);

            if (template == null) {
                throw new Exception("GetStakingBlockTemplate failed!");
            }

            return template.getHeader().getMineHash();
        } finally {
            blockTemplateLock.unlock();
        }
    }

    @Override
    public boolean submitsignature(byte[] signature, byte[] sealhash) {
        if (! ac.getAionHub().getBlockchain().isUnityForkEnabled()) {
            throw new IllegalStateException("UnityForkNotEnabled!");
        }

        if (signature == null || sealhash == null) {
            throw new NullPointerException();
        }

        if (signature.length != StakedBlockHeader.SIG_LENGTH
            || sealhash.length != 32) {
            throw new IllegalArgumentException("Invalid arguments length");
        }

        StakingBlock block = (StakingBlock) ac.getBlockchain().getCachingStakingBlockTemplate(sealhash);
        if (block == null) {
            return false;
        }

        block.sealHeader(signature, block.getHeader().getPubKey());
        ac.getBlockchain().putSealedNewStakingBlock(block);

        return true;
    }

    @Override
    public Transaction eth_getTransactionByHash2(byte[] var0) {
        AionTxInfo txInfo = AionImpl.inst().aionHub.getBlockchain().getTransactionInfo(var0);
        if(txInfo == null) {
            return null;
        }

        Block b = AionImpl.inst().aionHub.getBlockchain().getBlockByHash(txInfo.getBlockHash());
        if (b == null) {
            throw new RuntimeException("This is an internal error.");
        }

        return new Transaction(
                txInfo.getBlockHash(),
                BigInteger.valueOf(b.getNumber()),
                txInfo.getReceipt().getTransaction().getSenderAddress().toByteArray(),
                BigInteger.valueOf(txInfo.getReceipt().getTransaction().getEnergyLimit()),
                BigInteger.valueOf(txInfo.getReceipt().getTransaction().getEnergyPrice()),
                BigInteger.valueOf(txInfo.getReceipt().getTransaction().getEnergyLimit()),
                BigInteger.valueOf(txInfo.getReceipt().getTransaction().getEnergyPrice()),
                txInfo.getReceipt().getTransaction().getTransactionHash(),
                txInfo.getReceipt().getTransaction().getData(),
                txInfo.getReceipt().getTransaction().getNonceBI(),
                txInfo.getReceipt().getTransaction().getDestinationAddress().toByteArray(),
                BigInteger.valueOf(txInfo.getIndex()),
                txInfo.getReceipt().getTransaction().getValueBI(),
                BigInteger.valueOf(b.getTimestamp())
        );
    }

    @Override
    public byte[] eth_call2(CallRequest var0) {
        AionTransaction tx =
                AionTransaction.createWithoutKey(
                        BigInteger.valueOf(0).toByteArray(),
                        AddressUtils.ZERO_ADDRESS,
                        AddressUtils.wrapAddress(ByteUtil.toHexString(var0.getTo())),
                        var0.getValue().toByteArray(),
                        var0.getData(),
                        CfgAion.inst().getApi().getNrg().getNrgPriceDefault(),
                        Long.MAX_VALUE,
                        (byte)1
                );
        return AionImpl
                .inst()
                .callConstant(
                        tx,
                        AionImpl.inst().getBlockchain().getBestBlock()
                ).getTransactionOutput();
    }
}
