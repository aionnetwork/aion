package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.zero.impl.types.BlockHeader;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.StakingBlockHeader;

public class StakingSeedRule implements GreatGrandParentDependantBlockHeaderRule {

    private boolean validateInner(
            StakingBlockHeader header, StakingBlockHeader dependency, List<RuleError> errors) {
        byte[] oldSeed = dependency.getSeedOrProof();
        byte[] newSeed = header.getSeedOrProof();
        byte[] pk = header.getSigningPublicKey();

        if (!ECKeyEd25519.verify(oldSeed, newSeed, pk)) {
            BlockHeaderValidatorUtil.addError(
                    formatError(oldSeed, newSeed, pk), this.getClass(), errors);
            return false;
        }

        return true;
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

    @Override
    public boolean validate(BlockHeader grandParent, BlockHeader greatGrandParent,
        BlockHeader current, List<RuleError> errors) {
        return validateInner((StakingBlockHeader) current, (StakingBlockHeader) grandParent, errors);
    }
}
