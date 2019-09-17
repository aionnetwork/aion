package org.aion.zero.impl.valid;

import java.util.List;

import org.aion.mcf.blockchain.BlockHeader;

public class AionExtraDataRule implements BlockHeaderRule {

    private final int maximumExtraDataSize;

    public AionExtraDataRule(int maximumExtraDataSize) {
        if (maximumExtraDataSize < 0)
            throw new IllegalArgumentException("extra data size must be >= 0");

        this.maximumExtraDataSize = maximumExtraDataSize;
    }

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {
        if (header.getExtraData() != null
                && header.getExtraData().length > this.maximumExtraDataSize) {
            BlockHeaderValidatorUtil.addError(
                    String.format(
                            "extraData (%d) > MAXIMUM_EXTRA_DATA_SIZE (%d)",
                            header.getExtraData().length, this.maximumExtraDataSize),
                    this.getClass(),
                    errors);
            return false;
        }
        return true;
    }
}
