package org.aion.zero.impl.config.dynamic;

import org.aion.mcf.config.Cfg;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
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
        Map<String, Pair<Function<Cfg,?>, IDynamicConfigApplier>> gettersAppliers = new HashMap<>() {{
            put("myKey1", ImmutablePair.of(func1, new TestApplier()));
            put("myKey2", ImmutablePair.of(func2, new TestApplier()));
        }};
        DynamicConfigKeyRegistry unit = new DynamicConfigKeyRegistry(gettersAppliers);

        assertThat(unit.getBoundKeys().size(), is(2));
        assertThat(unit.getBoundKeys().contains("myKey1"), is(true));
        assertThat(unit.getBoundKeys().contains("myKey2"), is(true));
        assertThat(unit.getGetter("myKey1"), is(func1));
        assertThat(unit.getGetter("myKey2"), is(func2));
        assertThat(unit.getApplier("myKey1") instanceof TestApplier, is(true));
        assertThat(unit.getApplier("myKey2") instanceof TestApplier, is(true));
    }

    @Test
    public void testKeyNotDynamic() throws InFlightConfigChangeException {
        DynamicConfigKeyRegistry unit = new DynamicConfigKeyRegistry();
        int exceptionsSeen = 0;
        try {
            unit.getApplier("aion.sync").apply(mock(Cfg.class), mock(Cfg.class));
        } catch (InFlightConfigChangeNotAllowedException ex) {
            exceptionsSeen += 1;
        }
        try {
            unit.getApplier("aion.sync").undo(mock(Cfg.class), mock(Cfg.class));
        } catch (InFlightConfigChangeNotAllowedException ex) {
            exceptionsSeen += 1;
        }
        assertThat(exceptionsSeen, is(2));
    }

    @Test
    public void testBind() {
        Function<Cfg,?> func1 = cfg -> cfg.getApi();
        DynamicConfigKeyRegistry unit = new DynamicConfigKeyRegistry(new HashMap<>());
        unit.bind("myKey", func1, new TestApplier());
        assertThat(unit.getBoundKeys().size(), is(1));
        assertThat(unit.getGetter("myKey"), is(func1));
        assertThat(unit.getApplier("myKey") instanceof TestApplier, is(true));

    }
}