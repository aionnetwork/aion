package org.aion.precompiled.contracts.ATB;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;

import javax.annotation.Nonnull;

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
 * Handles all loading/unloading, disguises itself like a simple POJO
 * from the outside.
 *
 */
public class BridgeStorageConnector {

    private enum S_OFFSET {
        OWNER(new DataWord(0x0)),
        NEW_OWNER(new DataWord(0x1)),
        MEMBER_COUNT(new DataWord(0x2)),
        MIN_THRESH(new DataWord(0x3)),
        RING_LOCKED(new DataWord(0x4));

        private DataWord offset;

        private S_OFFSET(DataWord offset) {
            this.offset = offset;
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

    public void setOwner(byte[] address) {
        this.setDWORD(S_OFFSET.OWNER.offset, address);
    }

    public byte[] getOwner() {
        byte[] ret = this.getWORD(S_OFFSET.OWNER.offset);
        return BridgeUtilities.getAddress(ret);
    }

    public byte[] getNewOwner() {
        byte[] ret = this.getDWORD(S_OFFSET.NEW_OWNER.offset);
        return BridgeUtilities.getAddress(ret);
    }

    // DWORD helpers

    private byte[] getWORD(@Nonnull final DataWord key) {
        IDataWord word = this.track.getStorageValue(contractAddress, key);
        if (word == null)
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
        byte[] upper = new byte[dword.length - 16];
        byte[] lower = new byte[16];
    }

    private byte[] getDWORD(@Nonnull final DataWord key) {
        IDataWord word = this.track.getStorageValue(contractAddress, key);
        if (word == null)
            return null;

        byte[] upper = word.getData();
        if (upper == null)
            return null;

        DataWord lowerKey = new DataWord(
                HashUtil.blake256(ByteUtil.appendByte(key.getData(), (byte) 0x1)));
        word = this.track.getStorageValue(contractAddress, lowerKey);

        if (word == null)
            return null;

        byte[] lower = word.getData();
        if (lower == null)
            return null;
        return ByteUtil.merge(upper, lower);
    }
}
