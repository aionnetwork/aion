package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;
import org.aion.zero.impl.types.A0BlockHeaderVersion;

public class AionHeaderVersionRule extends BlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {
        if (!A0BlockHeaderVersion.isActive(header.getSealType().getSealId())) { //TODO: [unity] Revise this rule in the following commits
            addError(
                    "Invalid header version, found version "
                            + header.getSealType()
                            + " expected one of "
                            + A0BlockHeaderVersion.activeVersions(),
                    errors);
            return false;
        }
        return true;
    }
}
