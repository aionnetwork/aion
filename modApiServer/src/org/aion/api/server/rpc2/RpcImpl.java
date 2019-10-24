package org.aion.api.server.rpc2;

import java.util.concurrent.TimeUnit;
import org.aion.api.server.rpc2.autogen.Rpc;
import org.aion.api.server.rpc2.autogen.errors.NullReturnRpcException;
import org.aion.util.time.TimeUtils;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.core.ImportResult;
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
        if (!ac.getAionHub().getBlockchain().isUnityForkEnabledAtNextBlock()) {
            return null;
        }

        return ac.getBlockchain().getSeed();
    }

    @Override
    public byte[] submitseed(byte[] newSeed, byte[] signingPublicKey, byte[] coinbase) throws NullReturnRpcException {
        if (!ac.getAionHub().getBlockchain().isUnityForkEnabledAtNextBlock()) {
            throw new NullReturnRpcException("unity fork is not enabled");
        }

        if (newSeed == null) {
            throw new NullReturnRpcException("the giving seed is null");
        }

        if (signingPublicKey == null) {
            throw new NullReturnRpcException("the giving signing public key is null");
        }

        if (coinbase == null) {
            throw new NullReturnRpcException("the given coinbase is null");
        }

        if (newSeed.length != StakingBlockHeader.SEED_LENGTH) {
            throw new NullReturnRpcException("the giving seed length is incorrect");
        }

        if (signingPublicKey.length != StakingBlockHeader.PUBKEY_LENGTH) {
            throw new NullReturnRpcException("the giving signing public key length is incorrect");
        }

        if (coinbase.length != 32) {
            throw new NullReturnRpcException("the given coinbase has invalid length");
        }

        StakingBlock template = ac.getAionHub().getStakingBlockTemplate(newSeed, signingPublicKey, coinbase);
        
        if (template == null) {
            throw new NullReturnRpcException("GetStakingBlockTemplate failed!");
        }

        return template.getHeader().getMineHash();
    }

    @Override
    public boolean submitsignature(byte[] signature, byte[] sealhash) throws NullReturnRpcException {
        if (! ac.getAionHub().getBlockchain().isUnityForkEnabledAtNextBlock()) {
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

        // Cannot submit a future block to the kernel
        if (block.getTimestamp() > TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + 1) {
            return false;
        }

        block.seal(signature, block.getHeader().getSigningPublicKey());
        ImportResult result = ac.getBlockchain().tryToConnect(block);
        return (result == ImportResult.IMPORTED_BEST || result == ImportResult.IMPORTED_NOT_BEST);
    }
}
