package org.aion.api.server.rpc2;

import org.aion.api.RpcException;
import org.aion.api.server.rpc2.autogen.Rpc;
import org.aion.api.server.rpc2.autogen.errors.NullReturnRpcException;
import org.aion.api.server.rpc2.autogen.pod.CallRequest;
import org.aion.api.server.rpc2.autogen.pod.Transaction;
import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.aion.zero.impl.blockchain.AionImpl;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.ImportResult;
import org.aion.zero.impl.types.AionTxInfo;

import java.math.BigInteger;
import org.aion.zero.impl.types.StakingBlock;
import org.aion.zero.impl.types.StakingBlockHeader;

public class RpcImpl implements Rpc {

    private IAionChain ac;

    RpcImpl(final IAionChain aionChain) {
        if (aionChain == null) {
            throw new NullPointerException("RpcImpl construct aionChain is null");
        }

        ac = aionChain;
    }

    @Override
    public byte[] getseed() {
        if (!ac.getAionHub().getBlockchain().isUnityForkEnabled()) {
            return null;
        }

        return ac.getBlockchain().getSeed();
    }

    @Override
    public byte[] submitseed(byte[] newSeed, byte[] signingPublicKey) throws RpcException {
        if (!ac.getAionHub().getBlockchain().isUnityForkEnabled()) {
            throw new NullReturnRpcException("unity fork is not enabled");
        }

        if (newSeed == null) {
            throw new NullReturnRpcException("the giving seed is null");
        }

        if (signingPublicKey == null) {
            throw new NullReturnRpcException("the giving signing public key is null");
        }

        if (newSeed.length != StakingBlockHeader.SEED_LENGTH) {
            throw new NullReturnRpcException("the giving seed length is incorrect");
        }

        if (signingPublicKey.length != StakingBlockHeader.PUBKEY_LENGTH) {
            throw new NullReturnRpcException("the giving signing public key length is incorrect");
        }

        StakingBlock template = ac.getAionHub().getStakingBlockTemplate(newSeed, signingPublicKey);

        if (template == null) {
            throw new NullReturnRpcException("GetStakingBlockTemplate failed!");
        }

        return template.getHeader().getMineHash();
    }

    @Override
    public boolean submitsignature(byte[] signature, byte[] sealhash) throws RpcException {
        if (! ac.getAionHub().getBlockchain().isUnityForkEnabled()) {
            throw new NullReturnRpcException("unity fork is not enabled");
        }

        if (signature == null) {
            throw new NullReturnRpcException("the giving signature is null");
        }

        if (sealhash == null) {
            throw new NullReturnRpcException("the giving sealhash is null");
        }

        if (signature.length != StakingBlockHeader.SIG_LENGTH) {
            throw new NullReturnRpcException("the giving signature length is incorrect");
        }

        if (sealhash.length != StakingBlockHeader.HASH_BYTE_SIZE ) {
            throw new NullReturnRpcException("the giving sealhash length is incorrect");
        }


        StakingBlock block = (StakingBlock) ac.getBlockchain().getCachingStakingBlockTemplate(sealhash);
        if (block == null) {
            return false;
        }

        block.seal(signature, block.getHeader().getSigningPublicKey());
        ImportResult result = ac.getBlockchain().tryToConnect(block);
        return (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST);
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
                        (byte)1,
                        null
                );
        return AionImpl
                .inst()
                .callConstant(
                        tx,
                        AionImpl.inst().getBlockchain().getBestBlock()
                ).getTransactionOutput();
    }
}
