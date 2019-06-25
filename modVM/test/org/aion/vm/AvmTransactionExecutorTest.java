package org.aion.vm;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.SideEffects;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

public class AvmTransactionExecutorTest {
    @Test
    public void testBuildTransactionSummary() {
        AionAddress address = new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
        byte[] data = RandomUtils.nextBytes(16);
        List<byte[]> dataList = new ArrayList<>();
        dataList.add(data);

        Log log = Log.topicsAndData(address.toByteArray(), dataList, RandomUtils.nextBytes(24));
        SideEffects sideEffects = new SideEffects();
        sideEffects.addLog(log);

        AionTransaction transaction =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        address,
                        new byte[0],
                        DataWordImpl.ONE.getData(),
                        1_000_000L,
                        1L);

        AvmTransactionResult successfulResult = new AvmTransactionResult(1000L, 1L);
        successfulResult.getSideEffects().merge(sideEffects);
        successfulResult.setReturnData(data);

        AvmTransactionResult failedResult = new AvmTransactionResult(1000L, 1000L);
        failedResult.getSideEffects().merge(sideEffects);
        failedResult.setResultCode(AvmTransactionResult.Code.FAILED);
        failedResult.setReturnData(data);

        AionTxExecSummary successfulTransactionSummary = AvmTransactionExecutor.buildTransactionSummary(transaction, successfulResult, true);
        AionTxExecSummary failedTransactionSummary = AvmTransactionExecutor.buildTransactionSummary(transaction, failedResult, true);

        assertFalse(successfulTransactionSummary.getLogs().isEmpty());
        assertTrue(failedTransactionSummary.getLogs().isEmpty());
    }
}
