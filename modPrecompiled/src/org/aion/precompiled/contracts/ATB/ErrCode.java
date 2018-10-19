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

// utility helpers
public enum ErrCode {
    NO_ERROR(0x0),
    NOT_OWNER(0x1),
    NOT_NEW_OWNER(0x2),
    RING_LOCKED(0x3),
    RING_NOT_LOCKED(0x4),
    RING_MEMBER_EXISTS(0x5),
    RING_MEMBER_NOT_EXISTS(0x6),
    NOT_RING_MEMBER(0x7),
    NOT_ENOUGH_SIGNATURES(0x8),
    INVALID_SIGNATURE_BOUNDS(0x9),
    INVALID_TRANSFER(0xA),
    NOT_RELAYER(0xB),
    PROCESSED(0xC),
    UNCAUGHT_ERROR(0x1337);

    private final int errCode;

    ErrCode(int i) {
        this.errCode = i;
    }
}
