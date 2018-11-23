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

package org.aion.zero.exceptions;

/**
 * Denotes an exception occuring because a field in the header structure was ruled to be invalid.
 * This can range from an invalid length in the header's structure to a missing field when
 * constructing the header.
 *
 * <p>This exception does not cover violating <b>logical</b> rules, for example an incorrectly added
 * transaction, or a incorrect difficulty calculation
 *
 * @implNote extends {@link Exception} so that callers are forced to deal with the case of an
 *     invalid header, if the position is unacceptable then simply wrap the exception in a {@link
 *     RuntimeException}
 */
public class HeaderStructureException extends Exception {

    private final String field;
    private final int offset;

    /**
     * @param field name of the field
     * @param offset offset of the field refer to {@link org.aion.zero.types.A0BlockHeader}
     */
    public HeaderStructureException(String field, int offset, String message) {
        super(String.format("HeaderStructure field: %s/%d violated, %s", field, offset, message));
        this.field = field;
        this.offset = offset;
    }

    public HeaderStructureException(String field, int offset) {
        super(String.format("HeaderStructure field: %s/%d violated", field, offset));
        this.field = field;
        this.offset = offset;
    }

    public HeaderStructureException(String field, int offset, Throwable cause) {
        super(String.format("HeaderStructure field: %s/%d violated", field, offset), cause);

        this.field = field;
        this.offset = offset;
    }

    public String getField() {
        return this.field;
    }

    public int getOffset() {
        return this.offset;
    }
}
