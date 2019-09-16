package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.StakingBlockHeader;

public class StakingSeedRule extends DependentBlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors) {

        if (!(header instanceof StakingBlockHeader)) {
            throw new IllegalStateException("Invalid header input");
        }

        if (!(dependency instanceof StakingBlockHeader)) {
            throw new IllegalStateException("Invalid parent header input");
        }

        byte[] oldSeed = ((StakingBlockHeader) dependency).getSeed();
        byte[] newSeed = ((StakingBlockHeader) header).getSeed();
        byte[] pk = ((StakingBlockHeader)header).getSigningPublicKey();

        if (!ECKeyEd25519.verify(oldSeed, newSeed, pk)) {
            addError(formatError(oldSeed, newSeed, pk), errors);
            return false;
        }

        return true;
    }

    @Override
    public boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors,
        Object arg) {
        return validate(header, dependency, errors);
    }

    private static String formatError(byte[] seed, byte[] parentSeed, byte[] pubkey) {
        return "block seed output ("
                + ByteUtil.toHexString(seed)
                + ") violates seed ( parentSeed:"
                + ByteUtil.toHexString(parentSeed)
                + ") and public key condition ( publicKey:"
                + ByteUtil.toHexString(pubkey)
                + ")";
    }
}
