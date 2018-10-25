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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class ConfigProposalResultTest {
    @Test
    public void testCtorAndGetter() {
        boolean success = true;
        Exception cause = new IllegalMonitorStateException();
        ConfigProposalResult unit = new ConfigProposalResult(success, cause);
        assertThat(unit.getErrorCause(), is(cause));
        assertThat(unit.isSuccess(), is(success));
    }

    @Test
    public void testToString() {
        boolean success = true;
        Exception cause = new IllegalMonitorStateException();
        ConfigProposalResult unit = new ConfigProposalResult(success, cause);
        assertThat(
                unit.toString(),
                is(
                        "ConfigProposalResult{success=true, errorCause=java.lang.IllegalMonitorStateException}"));
    }
}
