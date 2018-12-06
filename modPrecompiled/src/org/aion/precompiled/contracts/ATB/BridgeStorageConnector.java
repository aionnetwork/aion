/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */

package org.aion.precompiled.contracts.ATB;

import java.math.BigInteger;
import java.util.Arrays;
import javax.annotation.Nonnull;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.DoubleDataWord;
import org.aion.precompiled.PrecompiledUtilities;

/**
 * Storage layout mapping as the following:
 *
 * <p>------------------------------------------------------------ owner DWORD 0x0 current owner of
 * the contract newOwner DWORD 0x1 proposed new owner of the contract
 *
 * <p>memberCount WORD 0x2 the total amount of members minThresh WORD 0x3 the minimum amount of
 * votes required ringLocked WORD 0x4 if the ring is locked or not
 *
 * <p>There are two mapping fields: memberMap map[WORD] => boolean {@code TRUE} if member is active
 * bundleMap map[WORD] => boolean {@code TRUE} if bundle has been processed
 *
 * <p>Maps are prepended with respective offsets, their keys are derived as the following:
 *
 * <p>{@code h256(concat(MAP_OFFSET, key))}
 *
 * <p>Handles all loading/unloading, disguises itself like a simple POJO from the outside.
 *
 * <p>Impl Detail Notes (Contracts):
 *
 * <p>[C1] The storage does not distinguish between empty byte array (16 bytes 0) and nulls. Any
 * time it detects that the response is a 16-byte 0, a null is returned instead.
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

        private final DataWord offset;

        S_OFFSET(DataWord offset) {
            this.offset = offset;
        }
    }

    private enum M_ID {
        BUNDLE_MAP((byte) 0x1),
        ACTIVE_MAP((byte) 0x2);

        private final byte[] id;

        M_ID(byte id) {
            this.id = new byte[] {id};
        }
    }

    private final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track;
    private final AionAddress contractAddress;

    public BridgeStorageConnector(
            @Nonnull final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track,
            @Nonnull final AionAddress contractAddress) {
        this.track = track;
        this.contractAddress = contractAddress;
    }

    public void setInitialized(final boolean initialized) {
        DataWord init = initialized ? new DataWord(1) : new DataWord(0);
        this.setWORD(S_OFFSET.INITIALIZED.offset, init);
    }

    public boolean getInitialized() {
        byte[] word = this.getWORD(S_OFFSET.INITIALIZED.offset);
        return word != null && (word[15] & 0x1) == 1;
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
        return this.getDWORD(S_OFFSET.NEW_OWNER.offset);
    }

    public void setRelayer(@Nonnull final byte[] address) {
        assert address.length == 32 : "address length must be 32 bytes";
        this.setDWORD(S_OFFSET.RELAYER.offset, address);
    }

    public byte[] getRelayer() {
        return this.getDWORD(S_OFFSET.RELAYER.offset);
    }

    public void setMemberCount(int amount) {
        assert amount >= 0 : "amount must be positive";
        this.setWORD(S_OFFSET.MEMBER_COUNT.offset, new DataWord(amount));
    }

    public int getMemberCount() {
        byte[] countWord = this.getWORD(S_OFFSET.MEMBER_COUNT.offset);
        if (countWord == null) return 0;
        return new BigInteger(1, countWord).intValueExact();
    }

    public void setMinThresh(int amount) {
        assert amount >= 0 : "amount must be positive";
        this.setWORD(S_OFFSET.MIN_THRESH.offset, new DataWord(amount));
    }

    public int getMinThresh() {
        // C1 covered by getWORD
        byte[] threshWord = this.getWORD(S_OFFSET.MIN_THRESH.offset);
        if (threshWord == null) return 0;
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
        if (lockedWord == null) return false;
        // this may be redundant
        return (lockedWord[15] & 0x01) == 1;
    }

    // TODO: this can be optimized
    public void setActiveMember(@Nonnull final byte[] key, final boolean value) {
        assert key.length == 32;
        byte[] h = ByteUtil.chop(HashUtil.h256(ByteUtil.merge(M_ID.ACTIVE_MAP.id, key)));
        DataWord hWord = new DataWord(h);
        DataWord b = value ? new DataWord(1) : new DataWord(0);
        this.setWORD(hWord, b);
    }

    public boolean getActiveMember(@Nonnull final byte[] key) {
        assert key.length == 32;
        byte[] h = ByteUtil.chop(HashUtil.h256(ByteUtil.merge(M_ID.ACTIVE_MAP.id, key)));
        DataWord hWord = new DataWord(h);

        // C1 covered by getWORD
        byte[] activeMemberWord = this.getWORD(hWord);
        return activeMemberWord != null && (activeMemberWord[15] & 0x01) == 1;
    }

    /**
     * @implNote ATB-4 changes, we have a new requirement in the contract to store the value
     *     (transactionHash) of when the bundle was set into the block.
     *     <p>Therefore, where previously we were checking whether the bundle was valid based on a
     *     {@code true/false} assumption, we now check whether the bundle is valid based on whether
     *     the returned address equates to a zero word.
     *     <p>Documentation on the change can be found as part of v0.0.4 changes.
     */
    public void setBundle(@Nonnull final byte[] key, @Nonnull final byte[] value) {
        assert key.length == 32;
        assert value.length == 32;

        byte[] h = ByteUtil.chop(HashUtil.h256(ByteUtil.merge(M_ID.BUNDLE_MAP.id, key)));
        DataWord hWord = new DataWord(h);
        this.setDWORD(hWord, value);
    }

    /**
     * @implNote changed as part of ATB-4, see {@link #setBundle(byte[], byte[])} above for more
     *     information.
     * @implNote note here that we return an EMPTY_WORD, this is checked in the controller layer and
     *     equates to a false.
     * @param key bundleHash
     * @return {@code EMPTY_WORD (32)} if no blockHash is found, {@code transactionHash} of the
     *     input transaction otherwise.
     */
    public byte[] getBundle(@Nonnull final byte[] key) {
        assert key.length == 32;
        byte[] h = ByteUtil.chop(HashUtil.h256(ByteUtil.merge(M_ID.BUNDLE_MAP.id, key)));
        DataWord hWord = new DataWord(h);
        byte[] bundleDoubleWord = this.getDWORD(hWord);
        if (bundleDoubleWord == null) return ByteUtil.EMPTY_WORD;

        // paranoid, this should typically never happen
        if (bundleDoubleWord.length < 32)
            bundleDoubleWord = PrecompiledUtilities.pad(bundleDoubleWord, 32);

        return bundleDoubleWord;
    }

    // DWORD helpers

    private byte[] getWORD(@Nonnull final DataWord key) {
        IDataWord word = this.track.getStorageValue(contractAddress, key);
        // C1
        if (word == null || Arrays.equals(word.getData(), ByteUtil.EMPTY_HALFWORD)) return null;
        return word.getData();
    }

    private void setWORD(@Nonnull final DataWord key, @Nonnull final DataWord word) {
        this.track.addStorageRow(contractAddress, key, word);
    }

    private void setDWORD(@Nonnull final DataWord key, @Nonnull final byte[] dword) {
        assert dword.length > 16;
        this.track.addStorageRow(contractAddress, key, new DoubleDataWord(dword));
    }

    private byte[] getDWORD(@Nonnull final DataWord key) {
        IDataWord word = this.track.getStorageValue(contractAddress, key);
        if (word == null) return null;

        if (word.isZero()) return null;
        return word.getData();
    }
}
