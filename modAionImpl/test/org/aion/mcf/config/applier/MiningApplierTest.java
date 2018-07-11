package org.aion.mcf.config.applier;

import org.aion.equihash.EquihashMiner;
import org.aion.evtmgr.IEventMgr;
import org.aion.mcf.blockchain.IPendingState;
import org.aion.mcf.config.dynamic2.InFlightConfigChangeResult;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.pow.AionPoW;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.IAionBlock;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class MiningApplierTest {
    private EquihashMiner equihashMiner;
    private AionPoW pow;
    private IAionBlockchain aionBlockchain;
    private IPendingState<AionTransaction> pendingState;
    private IEventMgr eventMgr;
    private CfgAion cfgOld;
    private CfgAion cfgNew;

    @Before
    public void before() {
        equihashMiner = mock(EquihashMiner.class);
        pow = mock(AionPoW.class);
        aionBlockchain = mock(IAionBlockchain.class);
        pendingState = mock(IPendingState.class);
        eventMgr = mock(IEventMgr.class);
        cfgOld = new CfgAion();
        cfgNew = new CfgAion();

        when(equihashMiner.getCfg()).thenReturn(cfgOld);
    }

    @Test
    public void testApplyNoDiffMiningAlreadyOn() throws Exception {
        cfgOld.getConsensus().setMining(true);
        cfgNew.getConsensus().setMining(true);
        MiningApplier unit = new MiningApplier(equihashMiner, pow, aionBlockchain, pendingState, eventMgr);

        InFlightConfigChangeResult result = unit.apply(cfgOld, cfgNew);

        assertThat(result.isSuccess(), is(true));
        assertThat(result.getApplier(), is(unit));
        verifyZeroInteractions(equihashMiner);
        verifyZeroInteractions(pow);
        verifyZeroInteractions(aionBlockchain);
        verifyZeroInteractions(pendingState);
        verifyZeroInteractions(eventMgr);
    }

    @Test
    public void testApplyNoDiffMiningAlreadyOff() throws Exception {
        cfgOld.getConsensus().setMining(false);
        cfgNew.getConsensus().setMining(false);
        MiningApplier unit = new MiningApplier(equihashMiner, pow, aionBlockchain, pendingState, eventMgr);

        InFlightConfigChangeResult result = unit.apply(cfgOld, cfgNew);

        assertThat(result.isSuccess(), is(true));
        assertThat(result.getApplier(), is(unit));
        verifyZeroInteractions(equihashMiner);
        verifyZeroInteractions(pow);
        verifyZeroInteractions(aionBlockchain);
        verifyZeroInteractions(pendingState);
        verifyZeroInteractions(eventMgr);
    }

    @Test
    public void testApplyChangeFromOffToOn() throws Exception {
        cfgOld.getConsensus().setMining(false);
        cfgNew.getConsensus().setMining(true);
        MiningApplier unit = new MiningApplier(equihashMiner, pow, aionBlockchain, pendingState, eventMgr);

        InFlightConfigChangeResult result = unit.apply(cfgOld, cfgNew);

        assertThat(result.isSuccess(), is(true));
        assertThat(result.getApplier(), is(unit));
        assertThat(cfgOld.getConsensus().getMining(), is(true));
        verify(equihashMiner).registerCallback();
        verify(pow).init(aionBlockchain, pendingState, eventMgr);
        verify(pow).resume();
        verify(equihashMiner).delayedStartMining(anyInt());
    }

    @Test
    public void testApplyChangeFromOnToOff() throws Exception {
        cfgOld.getConsensus().setMining(true);
        cfgNew.getConsensus().setMining(false);
        MiningApplier unit = new MiningApplier(equihashMiner, pow, aionBlockchain, pendingState, eventMgr);

        InFlightConfigChangeResult result = unit.apply(cfgOld, cfgNew);

        assertThat(result.isSuccess(), is(true));
        assertThat(result.getApplier(), is(unit));
        assertThat(cfgOld.getConsensus().getMining(), is(false));
        verify(equihashMiner).stopMining();
        verify(pow).pause();
    }

    @Test
    public void testUndo() throws Exception {
        cfgOld.getConsensus().setMining(true);
        cfgNew.getConsensus().setMining(false);
        MiningApplier unit = new MiningApplier(equihashMiner, pow, aionBlockchain, pendingState, eventMgr);

        InFlightConfigChangeResult result = unit.undo(cfgOld, cfgNew);

        assertThat(result.isSuccess(), is(true));
        assertThat(result.getApplier(), is(unit));
        assertThat(cfgOld.getConsensus().getMining(), is(true));
        verify(equihashMiner).registerCallback();
        verify(pow).init(aionBlockchain, pendingState, eventMgr);
        verify(pow).resume();
        verify(equihashMiner).delayedStartMining(anyInt());
    }
}