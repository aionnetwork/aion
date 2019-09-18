package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.StakingBlockHeader;

public class StakingSeedRule implements DependentBlockHeaderRule {

    private boolean validateInner(
            StakingBlockHeader header, StakingBlockHeader dependency, List<RuleError> errors) {
        byte[] oldSeed = dependency.getSeed();
        byte[] newSeed = header.getSeed();
        byte[] pk = header.getSigningPublicKey();

        if (!ECKeyEd25519.verify(oldSeed, newSeed, pk)) {
            BlockHeaderValidatorUtil.addError(
                    formatError(oldSeed, newSeed, pk), this.getClass(), errors);
            return false;
        }

        return true;
    }

    @Override
    public boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors) {
        if (!(header instanceof StakingBlockHeader)) {
            BlockHeaderValidatorUtil.addError("Invalid header type", this.getClass(), errors);
            return false;
        }

        if (!(dependency instanceof StakingBlockHeader)) {
            BlockHeaderValidatorUtil.addError(
                    "Invalid parent header type", this.getClass(), errors);
            return false;
        }

        return validateInner((StakingBlockHeader) header, (StakingBlockHeader) dependency, errors);
    }

    @Override
    public boolean validate(
            BlockHeader header, BlockHeader dependency, List<RuleError> errors, Object arg) {
        if (!(header instanceof StakingBlockHeader)) {
            BlockHeaderValidatorUtil.addError("Invalid header type", this.getClass(), errors);
            return false;
        }

        if (!(dependency instanceof StakingBlockHeader)) {
            BlockHeaderValidatorUtil.addError(
                    "Invalid parent header type", this.getClass(), errors);
            return false;
        }

        return validateInner((StakingBlockHeader) header, (StakingBlockHeader) dependency, errors);
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
