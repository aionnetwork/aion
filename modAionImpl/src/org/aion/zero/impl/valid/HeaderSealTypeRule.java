package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.zero.impl.types.BlockHeader;
import org.aion.zero.impl.types.BlockHeader.Seal;
import org.aion.zero.impl.types.MiningBlockHeader;
import org.aion.zero.impl.types.StakingBlockHeader;

public class HeaderSealTypeRule implements BlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {
        if (header instanceof MiningBlockHeader) {
            if (header.getSealType() != Seal.PROOF_OF_WORK) {
                BlockHeaderValidatorUtil.addError(
                        "Invalid header sealtype for the PoW block header, found sealType "
                                + header.getSealType(),
                        this.getClass(),
                        errors);
                return false;
            }
        } else if (header instanceof StakingBlockHeader) {
            if (header.getSealType() != Seal.PROOF_OF_STAKE) {
                BlockHeaderValidatorUtil.addError(
                        "Invalid header sealtype for the PoS block header, found sealType "
                                + header.getSealType(),
                        this.getClass(),
                        errors);
                return false;
            }
        } else {
            BlockHeaderValidatorUtil.addError("Invalid header instance", this.getClass(), errors);
            return false;
        }

        return true;
    }
}
