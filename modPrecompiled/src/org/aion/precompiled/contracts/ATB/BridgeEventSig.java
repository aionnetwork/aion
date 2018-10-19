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

import static org.aion.precompiled.contracts.ATB.BridgeUtilities.toEventSignature;

import javax.annotation.Nonnull;

enum BridgeEventSig {
    CHANGE_OWNER("ChangedOwner(address)"),
    ADD_MEMBER("AddMember(address)"),
    REMOVE_MEMBER("RemoveMember(address)"),
    PROCESSED_BUNDLE("ProcessedBundle(bytes32,bytes32)"),
    DISTRIBUTED("Distributed(bytes32,address,uint128)"),
    SUCCESSFUL_TXHASH("SuccessfulTxHash(bytes32)");

    private final byte[] hashed;

    BridgeEventSig(@Nonnull final String eventSignature) {
        this.hashed = toEventSignature(eventSignature);
    }

    public byte[] getHashed() {
        return this.hashed;
    }
}
