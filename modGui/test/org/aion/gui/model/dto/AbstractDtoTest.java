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

package org.aion.gui.model.dto;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

import org.aion.gui.model.KernelConnection;
import org.junit.Test;

public class AbstractDtoTest {

    @Test
    public void testLoadFromApi() {
        class TestImpl extends AbstractDto {
            public boolean loadCalled = false;

            public TestImpl() {
                super(mock(KernelConnection.class), null);
            }

            @Override
            protected void loadFromApiInternal() {
                loadCalled = true;
            }
        }

        AbstractDto unit = new TestImpl();
        unit.loadFromApi();
        assertThat(((TestImpl) unit).loadCalled, is(true));
    }
}
