package org.aion.zero.impl.valid;

import static org.aion.crypto.HashUtil.h256;

import java.util.Arrays;
import java.util.List;
import org.aion.crypto.AddressSpecs;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.A0BlockHeader;
import org.aion.zero.impl.types.StakingBlockHeader;

public class StakingSeedCreationRule implements GreatGrandParentDependantBlockHeaderRule {

    private static String formatError(byte[] expectedSeed, byte[] actualSeed) {
        return "block seed output ("
                + ByteUtil.toHexString(actualSeed)
                + ") violates seed rule ( expectedSeed:"
                + ByteUtil.toHexString(expectedSeed)
                + ")";
    }

    @Override
    public boolean validate(BlockHeader stakingParent, BlockHeader miningParent, BlockHeader current, List<RuleError> errors) {
        return validateInner((StakingBlockHeader) stakingParent, (A0BlockHeader) miningParent, (StakingBlockHeader) current, errors);
    }

    public boolean validateInner(StakingBlockHeader stakingParent, A0BlockHeader miningParent, StakingBlockHeader current, List<RuleError> errors) {
        // retrieve components
        byte[] parentSeed = stakingParent.getSeedOrProof();
        byte[] signerAddress = new AionAddress(AddressSpecs.computeA0Address(current.getSigningPublicKey())).toByteArray();
        byte[] powMineHash = miningParent.getMineHash();
        byte[] powNonce = miningParent.getNonce();
        int lastIndex = parentSeed.length + signerAddress.length + powMineHash.length + powNonce.length;
        byte[] concatenated = new byte[lastIndex + 1];
        System.arraycopy(parentSeed, 0, concatenated, 0, parentSeed.length);
        System.arraycopy(signerAddress, 0, concatenated, parentSeed.length, signerAddress.length);
        System.arraycopy(powMineHash, 0, concatenated, parentSeed.length + signerAddress.length, powMineHash.length);
        System.arraycopy(powNonce, 0, concatenated, parentSeed.length + signerAddress.length + powMineHash.length, powNonce.length);

        concatenated[lastIndex] = 0;
        byte[] hash1 = h256(concatenated);
        concatenated[lastIndex] = 1;
        byte[] hash2 = h256(concatenated);

        byte[] expectedSeed = new byte[hash1.length + hash2.length];
        System.arraycopy(hash1, 0, expectedSeed, 0, hash1.length);
        System.arraycopy(hash2, 0, expectedSeed, hash1.length, hash2.length);

        byte[] newSeed = current.getSeedOrProof();

        if (!Arrays.equals(expectedSeed, newSeed)) {
            BlockHeaderValidatorUtil.addError(formatError(expectedSeed, newSeed), this.getClass(), errors);
            return false;
        } else {
            return true;
        }
    }
}
