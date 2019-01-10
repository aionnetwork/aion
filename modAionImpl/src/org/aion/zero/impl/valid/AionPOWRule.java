package org.aion.zero.impl.valid;

import java.math.BigInteger;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.mcf.blockchain.valid.BlockHeaderRule;
import org.aion.zero.types.A0BlockHeader;

/** Checks proof value against its boundary for the block header */
public class AionPOWRule extends BlockHeaderRule<A0BlockHeader> {

    @Override
    public boolean validate(A0BlockHeader header, List<RuleError> errors) {
        BigInteger boundary = header.getPowBoundaryBI();

        byte[] hdrBytes = header.getMineHash();
        byte[] input = new byte[32 + 32 + 1408]; // H(Hdr) + nonce + solution

        int pos = 0;
        System.arraycopy(hdrBytes, 0, input, pos, hdrBytes.length);
        System.arraycopy(header.getNonce(), 0, input, pos += 32, 32);
        System.arraycopy(header.getSolution(), 0, input, pos += 32, 1408);

        BigInteger hash = new BigInteger(1, HashUtil.h256(input));

        if (hash.compareTo(boundary) >= 0) {
            addError(formatError(hash, boundary), errors);
            return false;
        }
        return true;
    }

    private static String formatError(BigInteger actual, BigInteger boundary) {
        return "computed output (" + actual + ") violates boundary condition (" + boundary + ")";
    }
}
