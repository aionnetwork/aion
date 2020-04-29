package org.aion.zero.impl.valid;

import java.util.List;
import org.aion.crypto.vrf.VRF_Ed25519;
import org.aion.mcf.blockchain.BlockHeader;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.types.StakingBlockHeader;

/**
 * @implNote Check the proof of the current staking block can be verified with the parent staking block
 * (the grandParent block)
 */
public class VRFProofRule implements GrandParentDependantBlockHeaderRule {
    @Override
    public boolean validate(
            BlockHeader parent,
            BlockHeader grandParent,
            BlockHeader current,
            List<RuleError> errors) {

        byte[] proof = ((StakingBlockHeader)current).getSeedOrProof();
        if (proof.length != StakingBlockHeader.PROOF_LENGTH) {
            BlockHeaderValidatorUtil.addError(
                "vrf verify failed, invalid proof length:"
                    + proof.length
                    + ", data:"
                    + ByteUtil.toHexString(proof),
                this.getClass(),
                errors);
            return false;
        } else {
            byte[] message = ((StakingBlockHeader)grandParent).getSeedOrProof();
            byte[] publicKey = ((StakingBlockHeader)current).getSigningPublicKey();

            boolean isValid = VRF_Ed25519.verify(message, proof, publicKey);
            if (!isValid) {
                BlockHeaderValidatorUtil.addError(
                    "vrf verify failed, msg:"
                        + ByteUtil.toHexString(message)
                        + ", proof:"
                        + ByteUtil.toHexString(proof)
                        + ", pubKey:"
                        + ByteUtil.toHexString(publicKey),
                    this.getClass(),
                    errors);
            }

            return isValid;
        }
    }
}
