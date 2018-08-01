package org.aion.precompiled.contracts.ATB;

import java.math.BigInteger;
import java.util.Arrays;
import javax.annotation.Nonnull;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;

/**
 * Storage layout mapping as the following:
 *
 * ------------------------------------------------------------
 * owner            DWORD   0x0     current owner of the contract
 * newOwner         DWORD   0x1     proposed new owner of the contract
 *
 * memberCount      WORD    0x2     the total amount of members
 * minThresh        WORD    0x3     the minimum amount of votes required
 * ringLocked       WORD    0x4     if the ring is locked or not
 *
 * There are two mapping fields:
 * memberMap        map[WORD] => boolean    {@code TRUE} if member is active
 * bundleMap        map[WORD] => boolean    {@code TRUE} if bundle has been processed
 *
 * Maps are prepended with respective offsets, their keys are derived
 * as the following:
 *
 * {@code h256(concat(MAP_OFFSET, key))}
 *
 * Handles all loading/unloading, disguises itself like a simple POJO
 * from the outside.
 *
 * Impl Detail Notes (Contracts):
 *
 * [C1] The storage does not distinguish between empty byte array (16 bytes 0) and nulls.
 * Any time it detects that the response is a 16-byte 0, a null is returned instead.
 *
 */
public class BridgeStorageConnector {

    private enum S_OFFSET {
        OWNER(new DataWord(0x0)),
        NEW_OWNER(new DataWord(0x1)),
        MEMBER_COUNT(new DataWord(0x2)),
        MIN_THRESH(new DataWord(0x3)),
        RING_LOCKED(new DataWord(0x4)),
        RELAYER(new DataWord(0x5)),
        INITIALIZED(new DataWord(0x42));

        private DataWord offset;

        S_OFFSET(DataWord offset) {
            this.offset = offset;
        }
    }

    private enum M_ID {
        BUNDLE_MAP((byte) 0x1),
        ACTIVE_MAP((byte) 0x2);

        private byte[] id;

        M_ID(byte id) {
            this.id = new byte[] {id};
        }
    }

    private final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track;
    private final Address contractAddress;

    public BridgeStorageConnector(
            @Nonnull final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track,
            @Nonnull final Address contractAddress) {
        this.track = track;
        this.contractAddress = contractAddress;
    }

    public void setInitialized(final boolean initialized) {
        DataWord init = initialized ? new DataWord(1) : new DataWord(0);
        this.setWORD(S_OFFSET.INITIALIZED.offset, init);
    }

    public boolean getInitialized() {
        byte[] word = this.getWORD(S_OFFSET.INITIALIZED.offset);
        if (word == null)
            return false;
        return (word[15] & 0x1) == 1;
    }

    public void setOwner(@Nonnull final byte[] address) {
        assert address.length == 32 : "address length must be 32 bytes";
        this.setDWORD(S_OFFSET.OWNER.offset, address);
    }

    public byte[] getOwner() {
        return this.getDWORD(S_OFFSET.OWNER.offset);
    }

    public void setNewOwner(@Nonnull final byte[] address) {
        assert address.length == 32 : "address length must be 32 bytes";
        this.setDWORD(S_OFFSET.NEW_OWNER.offset, address);
    }

    public byte[] getNewOwner() {
        byte[] ret = this.getDWORD(S_OFFSET.NEW_OWNER.offset);
        return BridgeUtilities.getAddress(ret);
    }

    public void setRelayer(@Nonnull final byte[] address) {
        assert address.length == 32 : "address length must be 32 bytes";
        this.setDWORD(S_OFFSET.RELAYER.offset, address);
    }

    public byte[] getRelayer() {
        byte[] ret = this.getDWORD(S_OFFSET.RELAYER.offset);
        return BridgeUtilities.getAddress(ret);
    }

    public void setMemberCount(int amount) {
        assert amount >= 0 : "amount must be positive";
        this.setWORD(S_OFFSET.MEMBER_COUNT.offset, new DataWord(amount));
    }

    public int getMemberCount() {
        byte[] countWord = this.getWORD(S_OFFSET.MEMBER_COUNT.offset);
        if (countWord == null)
            return 0;
        return new BigInteger(1, countWord).intValueExact();
    }

    public void setMinThresh(int amount) {
        assert amount >= 0 : "amount must be positive";
        this.setWORD(S_OFFSET.MIN_THRESH.offset, new DataWord(amount));
    }

    public int getMinThresh() {
        // C1 covere by getWORD
        byte[] threshWord = this.getWORD(S_OFFSET.MIN_THRESH.offset);
        if (threshWord == null)
            return 0;
        return new BigInteger(1, threshWord).intValueExact();
    }

    // TODO: this can be optimized
    public void setRingLocked(boolean value) {
        DataWord lockedDw = value ? new DataWord(1) : new DataWord(0);
        this.setWORD(S_OFFSET.RING_LOCKED.offset, lockedDw);
    }

    public boolean getRingLocked() {
        // C1 covered by getWORD
        byte[] lockedWord = this.getWORD(S_OFFSET.RING_LOCKED.offset);
        if (lockedWord == null)
            return false;
        // this may be redundant
        return (lockedWord[15] & 0x01) == 1;
    }

    // TODO: this can be optimized
    public void setActiveMember(@Nonnull final byte[] key,
                                final boolean value) {
        assert key.length == 32;
        byte[] h = ByteUtil.chop(
                HashUtil.h256(ByteUtil.merge(M_ID.ACTIVE_MAP.id, key)));
        DataWord hWord = new DataWord(h);
        DataWord b = value ? new DataWord(1) : new DataWord(0);
        this.setWORD(hWord, b);
    }

    public boolean getActiveMember(@Nonnull final byte[] key) {
        assert key.length == 32;
        byte[] h = ByteUtil.chop(
                HashUtil.h256(ByteUtil.merge(M_ID.ACTIVE_MAP.id, key)));
        DataWord hWord = new DataWord(h);

        // C1 covered by getWORD
        byte[] activeMemberWord = this.getWORD(hWord);
        if (activeMemberWord == null)
            return false;
        return (activeMemberWord[15] & 0x01) == 1;
    }

    public void setBundle(@Nonnull final byte[] key,
                          final boolean value) {
        assert key.length == 32;
        byte[] h = ByteUtil.chop(HashUtil.h256(ByteUtil.merge(M_ID.BUNDLE_MAP.id, key)));
        DataWord hWord = new DataWord(h);
        DataWord b = value ? new DataWord(1) : new DataWord(0);
        this.setWORD(hWord, b);
    }

    public boolean getBundle(@Nonnull final byte[] key) {
        assert key.length == 32;
        byte[] h = ByteUtil.chop(HashUtil.h256(ByteUtil.merge(M_ID.BUNDLE_MAP.id, key)));
        DataWord hWord = new DataWord(h);
        byte[] bundleWord = this.getWORD(hWord);
        if (bundleWord == null)
            return false;
        return (bundleWord[15] & 0x01) == 1;
    }

    // DWORD helpers

    private byte[] getWORD(@Nonnull final DataWord key) {
        IDataWord word = this.track.getStorageValue(contractAddress, key);
        // C1
        if (word == null || Arrays.equals(word.getData(), ByteUtil.EMPTY_HALFWORD))
            return null;
        return word.getData();
    }

    private void setWORD(@Nonnull final DataWord key,
                         @Nonnull final DataWord word) {
        this.track.addStorageRow(contractAddress, key, word);
    }

    private void setDWORD(@Nonnull final DataWord key,
                          @Nonnull final byte[] dword) {
        assert dword.length > 16;
        this.track.addStorageRow(contractAddress, key, new DoubleDataWord(dword));
    }

    private byte[] getDWORD(@Nonnull final DataWord key) {
        IDataWord word = this.track.getStorageValue(contractAddress, key);
        if (word == null)
            return null;

        if (word.isZero())
            return null;
        return word.getData();
    }
}
