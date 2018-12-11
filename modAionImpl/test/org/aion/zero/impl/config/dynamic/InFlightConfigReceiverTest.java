package org.aion.zero.impl.config.dynamic;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Function;
import javax.xml.stream.XMLStreamException;
import org.aion.mcf.config.Cfg;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

public class InFlightConfigReceiverTest {
    private LinkedHashMap<String, Pair<Function<Cfg, ?>, Optional<IDynamicConfigApplier>>>
            registryMap;
    private DynamicConfigKeyRegistry registry;
    private TestApplier successfulApplier;
    private TestApplier failingApplier;
    private TestApplier throwingApplier;
    private Cfg oldCfg;
    private Cfg newCfg;

    @Before
    public void before() {
        successfulApplier = new TestApplier(TestApplier.Behaviour.SUCCEED);
        failingApplier = new TestApplier(TestApplier.Behaviour.FAIL);
        throwingApplier = new TestApplier(TestApplier.Behaviour.THROW);
        registryMap = new LinkedHashMap<>();
        registry = new DynamicConfigKeyRegistry(registryMap);
        oldCfg = mock(Cfg.class);
        newCfg = mock(Cfg.class);
    }

    @Test
    public void testApplyNewConfigSuccessful() throws Exception {
        registryMap.put(
                "good.key.one",
                ImmutablePair.of(cfg -> cfg.getId(), Optional.of(successfulApplier)));
        registryMap.put(
                "good.key.two",
                ImmutablePair.of(cfg -> cfg.getId(), Optional.of(successfulApplier)));
        registryMap.put(
                "no-op.key",
                ImmutablePair.of(cfg -> cfg.getBasePath(), Optional.of(successfulApplier)));
        when(oldCfg.getId()).thenReturn("old");
        when(oldCfg.getBasePath()).thenReturn("same");
        when(newCfg.getId()).thenReturn("new");
        when(newCfg.getBasePath()).thenReturn("same");

        InFlightConfigReceiver unit = new InFlightConfigReceiver(oldCfg, registry);
        ConfigProposalResult result = unit.applyNewConfig(newCfg);

        assertThat(successfulApplier.timesApplied, is(2));
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    public void testApplyNewConfigUnsuccessfulApplierThenRollback() throws Exception {
        registryMap.put(
                "good.key", ImmutablePair.of(cfg -> cfg.getId(), Optional.of(successfulApplier)));
        registryMap.put(
                "bad.key", ImmutablePair.of(cfg -> cfg.getId(), Optional.of(failingApplier)));
        when(oldCfg.getId()).thenReturn("old");
        when(newCfg.getId()).thenReturn("new");

        InFlightConfigReceiver unit = new InFlightConfigReceiver(oldCfg, registry);
        ConfigProposalResult result = unit.applyNewConfig(newCfg);

        assertThat(successfulApplier.timesApplied, is(0));
        assertThat(result.isSuccess(), is(false));
    }

    @Test
    public void testApplyNewConfigApplierThrowsThenRollback() throws Exception {
        registryMap.put(
                "good.key", ImmutablePair.of(cfg -> cfg.getId(), Optional.of(successfulApplier)));
        registryMap.put(
                "bad.key", ImmutablePair.of(cfg -> cfg.getId(), Optional.of(throwingApplier)));
        when(oldCfg.getId()).thenReturn("old");
        when(newCfg.getId()).thenReturn("new");

        InFlightConfigReceiver unit = new InFlightConfigReceiver(oldCfg, registry);
        ConfigProposalResult result = unit.applyNewConfig(newCfg);

        assertThat(successfulApplier.timesApplied, is(0));
        assertThat(result.isSuccess(), is(false));
    }

    @Test(expected = RollbackException.class)
    public void testApplyNewConfigUnsuccessfulApplierThenRollbackUnsuccessful() throws Exception {
        TestApplier cannotRollbackApplier =
                new TestApplier(TestApplier.Behaviour.THROW_ON_UNDO_ONLY);

        registryMap.put(
                "good.key",
                ImmutablePair.of(cfg -> cfg.getId(), Optional.of(cannotRollbackApplier)));
        registryMap.put(
                "bad.key", ImmutablePair.of(cfg -> cfg.getId(), Optional.of(failingApplier)));
        when(oldCfg.getId()).thenReturn("old");
        when(newCfg.getId()).thenReturn("new");

        InFlightConfigReceiver unit = new InFlightConfigReceiver(oldCfg, registry);
        unit.applyNewConfig(newCfg);
    }

    @Test
    public void testProposeUnparseableXml() throws Exception {
        String notXml = "< not/XML";
        InFlightConfigReceiver unit = new InFlightConfigReceiver(oldCfg, registry);
        ConfigProposalResult result = unit.propose(notXml);
        assertThat(result.isSuccess(), is(false));
        assertThat(result.getErrorCause() instanceof XMLStreamException, is(true));
    }

    @Test
    public void testProposeXmlCausesNumberFormatError() throws Exception {
        String xml =
                "<aion><something/><sync><blocks-queue-max>ShouldBeAnInt</blocks-queue-max></sync></aion>";
        InFlightConfigReceiver unit = new InFlightConfigReceiver(oldCfg, registry);
        ConfigProposalResult result = unit.propose(xml);
        assertThat(result.isSuccess(), is(false));
    }

    /**
     * Applier used for testing. Its apply operation increments the counter {@link #timesApplied}
     */
    private static class TestApplier implements IDynamicConfigApplier {
        public int timesApplied;

        enum Behaviour {
            SUCCEED,
            FAIL,
            THROW,
            THROW_ON_UNDO_ONLY
        }

        private Behaviour behaviour;

        public TestApplier(Behaviour behaviour) {
            this.timesApplied = 0;
            this.behaviour = behaviour;
        }

        @Override
        public InFlightConfigChangeResult apply(Cfg oldCfg, Cfg newCfg)
                throws InFlightConfigChangeException {
            switch (behaviour) {
                case SUCCEED:
                case THROW_ON_UNDO_ONLY:
                    timesApplied += 1;
                    return new InFlightConfigChangeResult(true, this);
                case FAIL:
                    return new InFlightConfigChangeResult(false, this);
                case THROW:
                    throw new InFlightConfigChangeException("error");
                default:
                    fail("Test error"); // test has a bug
                    return null;
            }
        }

        @Override
        public InFlightConfigChangeResult undo(Cfg oldCfg, Cfg newCfg)
                throws InFlightConfigChangeException {
            switch (behaviour) {
                case SUCCEED:
                    timesApplied -= 1;
                    return new InFlightConfigChangeResult(true, this);
                case FAIL:
                    return new InFlightConfigChangeResult(false, this);
                case THROW:
                case THROW_ON_UNDO_ONLY:
                    throw new InFlightConfigChangeException("error");
                default:
                    fail("Test error"); // test has a bug
                    return null;
            }
        }
    }
}
