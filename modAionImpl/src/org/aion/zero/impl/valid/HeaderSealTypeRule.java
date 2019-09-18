package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.zero.impl.types.StakingBlockHeader;

public class HeaderSealTypeRule implements BlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {
        if (header instanceof A0BlockHeader) {
            if (header.getSealType() != BlockSealType.SEAL_POW_BLOCK) {
                BlockHeaderValidatorUtil.addError(
                        "Invalid header sealtype for the PoW block header, found sealType "
                                + header.getSealType(),
                        this.getClass(),
                        errors);
                return false;
            }
        } else if (header instanceof StakingBlockHeader) {
            if (header.getSealType() != BlockSealType.SEAL_POS_BLOCK) {
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
