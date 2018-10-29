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

package org.aion.zero.impl.config.dynamic;

import java.io.Serializable;

public class ConfigProposalResult implements Serializable {
    private final boolean success;
    private final Throwable errorCause;

    public ConfigProposalResult(boolean success) {
        this(success, null);
    }

    public ConfigProposalResult(boolean success, Throwable errorCause) {
        this.success = success;
        this.errorCause = errorCause;
    }

    public boolean isSuccess() {
        return success;
    }

    public Throwable getErrorCause() {
        return errorCause;
    }

    @Override
    public String toString() {
        return "ConfigProposalResult{" + "success=" + success + ", errorCause=" + errorCause + '}';
    }
}
