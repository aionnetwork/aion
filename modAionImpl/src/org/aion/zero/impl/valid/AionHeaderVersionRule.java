package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;
import org.aion.zero.types.A0BlockHeaderVersion;

public class AionHeaderVersionRule extends BlockHeaderRule<A0BlockHeader> {

    @Override
    public boolean validate(A0BlockHeader header, List<RuleError> errors) {
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
