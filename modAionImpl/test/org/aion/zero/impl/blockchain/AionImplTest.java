package org.aion.zero.impl.blockchain;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.concurrent.locks.ReentrantLock;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.core.ImportResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class AionImplTest {

    @Mock AionImpl chainImpl;
    @Mock AionHub hub;
    @Mock AionBlockchainImpl blockchain;
    @Mock Block newBlock;
    @Mock Block bestBlock;
    @Mock ReentrantLock lock;

    long blockHeight = 10L;

    @Before
    public void setup() {
        chainImpl = mock(AionImpl.class);
        hub = mock(AionHub.class);
        blockchain = mock(AionBlockchainImpl.class);
        newBlock = mock(Block.class);
        bestBlock = mock(Block.class);
        lock = mock(ReentrantLock.class);

        doReturn(hub).when(chainImpl).getAionHub();
        doReturn(blockchain).when(hub).getBlockchain();
        doReturn(bestBlock).when(blockchain).getBestBlock();
        doReturn(blockHeight).when(bestBlock).getNumber();

        doReturn(lock).when(chainImpl).getLock();
        doNothing().when(lock).lock();
        doNothing().when(lock).unlock();
    }

    @Test
    public void testAddNewBlockSameAsTheBestBlock() {
        doReturn(blockHeight).when(newBlock).getNumber();

        doCallRealMethod().when(chainImpl).addNewBlock(newBlock);
        ImportResult result = chainImpl.addNewBlock(newBlock);
        assertThat(result.equals(ImportResult.INVALID_BLOCK)).isTrue();
    }

    @Test
    public void testAddNewBlockLessThenTheBestBlock() {
        doReturn(blockHeight - 1).when(newBlock).getNumber();

        doCallRealMethod().when(chainImpl).addNewBlock(newBlock);
        ImportResult result = chainImpl.addNewBlock(newBlock);
        assertThat(result.equals(ImportResult.INVALID_BLOCK)).isTrue();
    }
}