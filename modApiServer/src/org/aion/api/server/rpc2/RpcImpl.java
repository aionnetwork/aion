package org.aion.api.server.rpc2;

import org.aion.api.server.rpc2.autogen.Rpc;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.api.RpcException;
import org.aion.api.server.rpc2.autogen.errors.NullReturnRpcException;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.types.StakingBlockHeader;
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
    public byte[] submitseed(byte[] newSeed, byte[] pubKey) throws RpcException {
        if (! ac.getAionHub().getBlockchain().isUnityForkEnabled()) {
            throw new IllegalStateException("UnityForkNotEnabled!");
        }

        if (newSeed == null || pubKey == null) {
            throw new NullPointerException();
        }

        if (newSeed.length != StakingBlockHeader.SEED_LENGTH
            || pubKey.length != StakingBlockHeader.PUBKEY_LENGTH) {
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
                throw new NullReturnRpcException("GetStakingBlockTemplate failed!");
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

        if (signature.length != StakingBlockHeader.SIG_LENGTH
            || sealhash.length != 32) {
            throw new IllegalArgumentException("Invalid arguments length");
        }

        StakingBlock block = (StakingBlock) ac.getBlockchain().getCachingStakingBlockTemplate(sealhash);
        if (block == null) {
            return false;
        }

        block.seal(signature, block.getHeader().getSigningPublicKey());
        ac.getBlockchain().putSealedNewStakingBlock(block);

        return true;
    }
}
