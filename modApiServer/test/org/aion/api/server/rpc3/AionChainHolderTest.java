package org.aion.api.server.rpc3;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.aion.zero.impl.types.StakingBlock;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class AionChainHolderTest {

    AionChainHolder ach;
    @Mock
    StakingBlock mockStakingBlock;

    @Before
    public void setup() {
        ach = mock(AionChainHolder.class);
    }
    @Test
    public void TestAddSealedBlockToPool() {
        long period = 1;
        doReturn(false).when(ach).addNewBlock(mockStakingBlock);
        doNothing().when(ach).scheduleTask(mockStakingBlock, period);
        doCallRealMethod().when(ach).addSealedBlockToPool(mockStakingBlock, period);

        assertTrue(ach.addSealedBlockToPool(mockStakingBlock, period));

        period = -1;
        assertFalse(ach.addSealedBlockToPool(mockStakingBlock, period));
    }
}
