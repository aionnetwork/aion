package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.mcf.valid.DependentBlockHeaderRule;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.types.StakedBlockHeader;

public class StakingSeedRule extends DependentBlockHeaderRule {

    @Override
    public boolean validate(BlockHeader header, BlockHeader dependency, List<RuleError> errors) {

        if (!(header instanceof StakedBlockHeader)) {
            throw new IllegalStateException("Invalid header input");
        }

        if (!(dependency instanceof StakedBlockHeader)) {
            throw new IllegalStateException("Invalid parent header input");
        }

        byte[] oldSeed = ((StakedBlockHeader) dependency).getSeed();
        byte[] newSeed = ((StakedBlockHeader) header).getSeed();
        byte[] pk = ((StakedBlockHeader) header).getPubKey();

        if (!ECKeyEd25519.verify(oldSeed, newSeed, pk)) {
            addError(formatError(oldSeed, newSeed, pk), errors);
            return false;
        }

        return true;
    }

    private static String formatError(byte[] seed, byte[] parentSeed, byte[] pubkey) {
        return "block seed output ("
                + ByteUtil.toHexString(seed)
                + ") violates seed ( parentSeed:"
                + ByteUtil.toHexString(parentSeed)
                + ") and public key condition ( publicKey:"
                + ByteUtil.toHexString(pubkey)
                + ")";
    }
}
