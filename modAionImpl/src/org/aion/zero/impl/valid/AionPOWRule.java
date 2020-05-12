package org.aion.zero.impl.valid;

import java.math.BigInteger;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.zero.impl.types.BlockHeader;
import org.aion.zero.impl.types.MiningBlockHeader;

/** Checks proof value against its boundary for the block header */
public class AionPOWRule implements BlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {
        MiningBlockHeader minedHeader = (MiningBlockHeader) header;
        return (validate(minedHeader, errors));
    }
    
    public boolean validate(MiningBlockHeader header, List<RuleError> errors) {
        BigInteger boundary = header.getPowBoundaryBI();

        byte[] hdrBytes = header.getMineHash();
        byte[] input = new byte[32 + 32 + 1408]; // H(Hdr) + nonce + solution

        int pos = 0;
        System.arraycopy(hdrBytes, 0, input, pos, hdrBytes.length);
        System.arraycopy(header.getNonce(), 0, input, pos += 32, 32);
        System.arraycopy(header.getSolution(), 0, input, pos += 32, 1408);

        BigInteger hash = new BigInteger(1, HashUtil.h256(input));

        if (hash.compareTo(boundary) >= 0) {
            BlockHeaderValidatorUtil.addError(formatError(hash, boundary), this.getClass(), errors);
            return false;
        }
        return true;
    }

    private static String formatError(BigInteger actual, BigInteger boundary) {
        return "computed output (" + actual + ") violates boundary condition (" + boundary + ")";
    }
}
