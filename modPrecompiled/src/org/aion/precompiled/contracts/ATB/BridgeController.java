package org.aion.precompiled.contracts.ATB;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.vm.types.DataWord;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * Contains the functional components of the Aion Token Bridge, this class is removed
 * from concerns regarding communicate with outside world (external) and communicating
 * with the database. See {@link BridgeSerializationConnector} and {@link BridgeStorageConnector}
 * respectively for information on those layers of components
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

        this.connector.setMemberCount(members.length);

        int thresh = Math.max((members.length * 2) / 3, 1);
        this.connector.setMinThresh(thresh);

        for (byte[] m : members) {
            this.connector.setActiveMember(m, true);
        }
        this.connector.setInitialized(true);
        return ErrCode.NO_ERROR;
    }

    public ErrCode addMember(@Nonnull final byte[] caller,
                             @Nonnull final byte[] address) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;

        if (!isRingLocked())
            return ErrCode.RING_NOT_LOCKED;

        if (isRingMember(address))
            return ErrCode.RING_MEMBER_EXISTS;

        int memberCount = this.connector.getMemberCount();
        int thresh = Math.min((this.connector.getMinThresh() * 2) / 3, 1);
        this.connector.setActiveMember(address, true);
        this.connector.setMinThresh(thresh);

        return ErrCode.NO_ERROR;
    }

    // bridge

    // utility helpers
    public enum ErrCode {
        NO_ERROR(0x0),
        NOT_OWNER(0x1),
        NOT_NEW_OWNER(0x2),
        RING_LOCKED(0x3),
        RING_NOT_LOCKED(0x4),
        RING_MEMBER_EXISTS(0x5),
        UNCAUGHT_ERROR(0x1337);

        private final int errCode;
        private ErrCode(int i) {
            this.errCode = i;
        }
    }
}
