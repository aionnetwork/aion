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
package org.aion.mcf.core;

/**
 * Common functionality for externally owned accounts {@link AccountState} and contracts (to be
 * added soon).
 *
 * @author Alexandra Roatis
 */
public abstract class AbstractState {

    /** The RLP encoding of this account state. */
    protected byte[] rlpEncoded = null;

    /** Flag indicating whether the account has been deleted. */
    protected boolean deleted = false;
    /** Flag indicating whether the state of the account has been changed. */
    protected boolean dirty = false;

    /**
     * Checks whether the state of the account has been changed during execution.
     *
     * <p>The dirty status is set internally by the object when its stored values have been
     * modified.
     *
     * @return {@code true} if the account state has been modified (including if the account is
     *     newly created), {@code false} if the account state is the same as in the database
     */
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * Sets the dirty flag to true signaling that the object state has been changed.
     *
     * @apiNote Once the account has been modified (by setting the flag to {@code true}) it is
     *     <b>considered dirty</b> even if its state reverts to the initial values by applying
     *     subsequent changes.
     * @implNote Method called internally by the account object when its state has been modified.
     *     Resets the stored RLP encoding.
     */
    protected void makeDirty() {
        this.rlpEncoded = null;
        this.dirty = true;
    }

    /**
     * Deletes the current account.
     *
     * @apiNote Once the account has been deleted (by setting the flag to {@code true}) <b>it cannot
     *     be recovered</b>.
     */
    public void delete() {
        this.deleted = true;
        makeDirty();
    }

    /**
     * Checks if the state of the account has been deleted.
     *
     * @return {@code true} if the account was deleted, {@code false} otherwise
     */
    public boolean isDeleted() {
        return this.deleted;
    }

    /**
     * Retrieves the RLP encoding of this object.
     *
     * @return a {@code byte} array representing the RLP encoding of the account state.
     * @implNote For performance reasons, this encoding is stored when available and recomputed only
     *     if the object has been modified during execution.
     */
    public abstract byte[] getEncoded();

    /**
     * Checks if the account state stores any meaningful information.
     *
     * @return {@code true} if the account state is empty, {@code false} otherwise
     * @apiNote Empty accounts should not be stored in the database.
     * @implNote Empty accounts do not have associated code (or by extension storage), i.e. cannot
     *     be contract accounts.
     */
    public abstract boolean isEmpty();
}
