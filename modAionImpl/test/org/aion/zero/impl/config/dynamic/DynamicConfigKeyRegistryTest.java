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

import org.aion.mcf.config.Cfg;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class DynamicConfigKeyRegistryTest {
    private static class TestApplier implements IDynamicConfigApplier {

        @Override
        public InFlightConfigChangeResult apply(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException {
            return null;
        }

        @Override
        public InFlightConfigChangeResult undo(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException {
            return null;
        }
    }

    @Test
    public void testCtorAndGetters() {
        Function<Cfg,?> func1 = cfg -> cfg.getApi();
        Function<Cfg,?> func2 = cfg -> cfg.getGui().getCfgGuiLauncher();
        Map<String, Pair<Function<Cfg,?>, Optional<IDynamicConfigApplier>>> gettersAppliers = new HashMap<>() {{
            put("myKey1", ImmutablePair.of(func1, Optional.of(new TestApplier())));
            put("myKey2", ImmutablePair.of(func2, Optional.of(new TestApplier())));
        }};
        DynamicConfigKeyRegistry unit = new DynamicConfigKeyRegistry(gettersAppliers);

        assertThat(unit.getBoundKeys().size(), is(2));
        assertThat(unit.getBoundKeys().contains("myKey1"), is(true));
        assertThat(unit.getBoundKeys().contains("myKey2"), is(true));
        assertThat(unit.getGetter("myKey1"), is(func1));
        assertThat(unit.getGetter("myKey2"), is(func2));
        assertThat(unit.getApplier("myKey1").get() instanceof TestApplier, is(true));
        assertThat(unit.getApplier("myKey2").get() instanceof TestApplier, is(true));
    }

    @Test
    public void testKeyNotDynamic() throws InFlightConfigChangeException {
        DynamicConfigKeyRegistry unit = new DynamicConfigKeyRegistry();
        assertThat( unit.getApplier("aion.sync").isPresent(), is(false));
    }

    @Test
    public void testBind() {
        Function<Cfg,?> func1 = cfg -> cfg.getApi();
        DynamicConfigKeyRegistry unit = new DynamicConfigKeyRegistry(new HashMap<>());
        unit.bind("myKey", func1, new TestApplier());
        assertThat(unit.getBoundKeys().size(), is(1));
        assertThat(unit.getGetter("myKey"), is(func1));
        assertThat(unit.getApplier("myKey").get() instanceof TestApplier, is(true));

    }
}