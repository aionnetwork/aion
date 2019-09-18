package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.StakingBlockHeader;

public class SignatureRule implements BlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, List<RuleError> errors) {

        if (!(header instanceof StakingBlockHeader)) {
            BlockHeaderValidatorUtil.addError("Invalid header type", this.getClass(), errors);
            return false;
        }

        StakingBlockHeader stakingBlockHeader = (StakingBlockHeader) header;
        byte[] mineHash = header.getMineHash();
        byte[] pk = stakingBlockHeader.getSigningPublicKey();
        byte[] sig = stakingBlockHeader.getSignature();

        if (!ECKeyEd25519.verify(mineHash, sig, pk)) {
            BlockHeaderValidatorUtil.addError(
                    formatError(mineHash, ByteUtil.merge(pk, sig)), this.getClass(), errors);
            return false;
        }

        return true;
    }

    private static String formatError(byte[] hash, byte[] sig) {
        return "block hash output ("
                + ByteUtil.toHexString(hash)
                + ") violates signature condition ( signature:"
                + ByteUtil.toHexString(sig)
                + ")";
    }
}
