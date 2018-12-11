package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;

public class AionExtraDataRule extends BlockHeaderRule<A0BlockHeader> {

    private final int maximumExtraDataSize;

    public AionExtraDataRule(int maximumExtraDataSize) {
        if (maximumExtraDataSize < 0)
            throw new IllegalArgumentException("extra data size must be >= 0");

        this.maximumExtraDataSize = maximumExtraDataSize;
    }

    @Override
    public boolean validate(A0BlockHeader header, List<RuleError> errors) {
        if (header.getExtraData() != null
                && header.getExtraData().length > this.maximumExtraDataSize) {
            addError(
                    String.format(
                            "extraData (%d) > MAXIMUM_EXTRA_DATA_SIZE (%d)",
                            header.getExtraData().length, this.maximumExtraDataSize),
                    errors);
            return false;
        }
        return true;
    }
}
