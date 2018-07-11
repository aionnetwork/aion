package org.aion.precompiled.contracts.ATB;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * Contains the functional components of the Aion Token Bridge, this class is removed
 * from concerns regarding communicate with outside world (external) and communicating
 * with the database.
 */
public class BridgeController {

    private BridgeStorageConnector connector;
    private final Address contractAddress;
    private final Address ownerAddress;

    public BridgeController(@Nonnull final BridgeStorageConnector storageConnector,
                            @Nonnull final Address contractAddress,
                            @Nonnull final Address ownerAddress) {
        this.connector = storageConnector;
        this.contractAddress = contractAddress;
        this.ownerAddress = ownerAddress;
    }

    /**
     * Loads in the stored state from the underlying repository
     */
    public void initialize() {
        if (this.connector.getInitialized())
            return;
        // otherwise initialize
        this.connector.setOwner(ownerAddress.toBytes());
    }

    // owner

    // guards/modifiers
    private boolean isOwner(@Nonnull final byte[] address) {
        byte[] owner = this.connector.getOwner();
        if (owner == null)
            return false;
        return Arrays.equals(owner, address);
    }

    private boolean isNewOwner(@Nonnull final byte[] address) {
        byte[] newOwner = this.connector.getNewOwner();
        if (newOwner == null)
            return false;
        return Arrays.equals(newOwner, address);
    }

    // logic
    public ErrCode setNewOwner(@Nonnull final byte[] caller,
                               @Nonnull final byte[] newOwner) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;
        this.connector.setNewOwner(newOwner);
        return ErrCode.NO_ERROR;
    }

    public ErrCode acceptOwnership(@Nonnull final byte[] caller) {
        if (!isNewOwner(caller))
            return ErrCode.NOT_NEW_OWNER;
        this.connector.setOwner(caller);
        this.connector.setNewOwner(ByteUtil.EMPTY_WORD);
        return ErrCode.NO_ERROR;
    }

    // end owner

    // ring

    private static int thresholdRatio(final int in) {
        return Math.max((in * 2) / 3, 1);
    }

    private boolean isRingLocked() {
        return this.connector.getRingLocked();
    }

    private boolean isRingMember(@Nonnull final byte[] address) {
        return this.connector.getActiveMember(address);
    }

    public ErrCode ringInitialize(@Nonnull final byte[] caller,
                                  @Nonnull final byte[][] members) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;

        if (isRingLocked())
            return ErrCode.RING_LOCKED;

        int thresh = thresholdRatio(members.length);

        this.connector.setMemberCount(members.length);
        this.connector.setMinThresh(thresh);

        for (byte[] m : members) {
            this.connector.setActiveMember(m, true);
        }
        this.connector.setRingLocked(true);
        return ErrCode.NO_ERROR;
    }

    public ErrCode ringAddMember(@Nonnull final byte[] caller,
                                 @Nonnull final byte[] address) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;

        if (!isRingLocked())
            return ErrCode.RING_NOT_LOCKED;

        if (isRingMember(address))
            return ErrCode.RING_MEMBER_EXISTS;

        int memberCount = this.connector.getMemberCount() + 1;
        int thresh = thresholdRatio(memberCount);

        this.connector.setActiveMember(address, true);
        this.connector.setMemberCount(memberCount);
        this.connector.setMinThresh(thresh);

        return ErrCode.NO_ERROR;
    }

    public ErrCode ringRemoveMember(@Nonnull final byte[] caller,
                                    @Nonnull final byte[] address) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;

        if (!isRingLocked())
            return ErrCode.RING_NOT_LOCKED;

        if (!isRingMember(address))
            return ErrCode.RING_MEMBER_NOT_EXISTS;

        int memberCount = this.connector.getMemberCount() - 1;
        int thresh = thresholdRatio(memberCount);

        this.connector.setActiveMember(address, false);
        this.connector.setMemberCount(memberCount);
        this.connector.setMinThresh(thresh);

        return ErrCode.NO_ERROR;
    }

    // bridge

    private boolean isEnoughSignatures(int signatureLength) {
        int thresh = this.connector.getMinThresh();
        return signatureLength >= thresh;
    }

    /**
     * Assume bundleHash is not from external source, but rather
     * calculated on our side (on the I/O layer), when {@link BridgeBundle} list
     * was being created.
     *
     * @param bundles
     * @return {@code ErrCode} indicating whether operation was successful
     *
     * @implNote assume the inputs are properly formatted
     *
     * @implNote assumes the bundles will always have positive transfer values
     * so the check is omitted. Technically it is possible to have an account
     * transfer for {@code 0} amount of tokens. Assume that the bridge will
     * take care that does not occur
     *
     * @implNote does not currently place restrictions on size of bundles.
     * Since we're not charging cost for bundles we may want to look into
     * charging these properly.
     */
    public ErrCode processBundles(@Nonnull final byte[] caller,
                                  @Nonnull final BridgeBundle[] bundles,
                                  @Nonnull final byte[][] signatures) {
        if (!isRingLocked())
            return ErrCode.RING_NOT_LOCKED;

        if (!isRingMember(caller))
            return ErrCode.NOT_RING_MEMBER;

        if (!isEnoughSignatures(signatures.length))
            return ErrCode.NOT_ENOUGH_SIGNATURES;

        // verify bundleHash
        byte[] hash = new byte[0];
        for (BridgeBundle b : bundles) {
            hash = HashUtil.h256(ByteUtil.merge(hash, b.recipient, b.transferValue.toByteArray()));
        }

        int signed = 0;
        for (byte[] sigBytes : signatures) {
            ISignature sig = SignatureFac.fromBytes(sigBytes);
            if (SignatureFac.verify(hash, sig) && this.connector.getActiveMember(sig.getAddress())) {
                signed++;
            }
        }

        int minThresh = this.connector.getMinThresh();
        if (signed < minThresh)
            return ErrCode.NOT_ENOUGH_SIGNATURES;

        // otherwise, we're clear to proceed with transfers
        for (BridgeBundle b : bundles) {
            if (!this.connector.transfer(b.recipient, b.transferValue))
                /*
                 * Rationale behind throwing on transfer, one invalid transfer indicates
                 * (to me atleast) a possible serialization or malicious error. The best
                 * we could do is consider whoever sent this batch malicious, and reject.
                 */
                return ErrCode.INVALID_TRANSFER;
        }
        return ErrCode.NO_ERROR;
    }

}
