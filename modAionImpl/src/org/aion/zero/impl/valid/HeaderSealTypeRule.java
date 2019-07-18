package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.StakedBlockHeader;

public class HeaderSealTypeRule extends BlockHeaderRule {

    public boolean validate(BlockHeader header, List<RuleError> errors) {

        if (header instanceof A0BlockHeader) {
            if (header.getSealType() != 0x01) {
                addError(
                        "Invalid header sealtype for the PoW block header, found sealType "
                                + header.getSealType(),
                        errors);
                return false;
            }
        } else if (header instanceof StakedBlockHeader) {
            if (header.getSealType() != 0x01) {
                addError(
                        "Invalid header sealtype for the PoS block header, found sealType "
                                + header.getSealType(),
                        errors);
                return false;
            }
        } else {
            addError("Invalid header instance", errors);
            return false;
        }

        return true;
    }
}
