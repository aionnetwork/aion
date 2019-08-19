package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.mcf.types.A0BlockHeaderVersion;

public class AionHeaderVersionRule extends BlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {
        if (!A0BlockHeaderVersion.isActive(header.getVersion())) {
            addError(
                    "Invalid header version, found version "
                            + header.getVersion()
                            + " expected one of "
                            + A0BlockHeaderVersion.activeVersions(),
                    errors);
            return false;
        }
        return true;
    }
}
