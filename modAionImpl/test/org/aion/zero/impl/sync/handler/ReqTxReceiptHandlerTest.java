package org.aion.zero.impl.sync.handler;

import org.aion.base.util.ByteUtil;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.msg.ResTxReceipts;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ReqTxReceiptHandlerTest {
    private IP2pMgr p2pMgr;
    private IAionBlockchain bc;

    @Before
    public void before() {
        p2pMgr = mock(IP2pMgr.class);
        bc = mock(IAionBlockchain.class);
    }

    @Test
    public void testReceive() {
        int id = 54321;
        String displayId = "321";

        byte[] b1 = ByteUtil.hexStringToBytes( "" +
                "01020304050a0b0c" +
                "05040302010a0b0c" +
                "00000000000d0e0f" +
                "05040302010a0b0c"); // 32 bytes
        byte[] b2 = ByteUtil.hexStringToBytes( "" +
                "01000000000f0f0f" +
                "02000000000a0b0c" +
                "03000000000d0e0f" +
                "04000000000a0b0c"); // 32 bytes
        byte[] request = ByteBuffer.allocate(64).put(b1).put(b2).array();

        AionTxInfo txInfo1 = mock(AionTxInfo.class);
        AionTxReceipt txr1 = mock(AionTxReceipt.class);
        when(bc.getTransactionInfo(b1)).thenReturn(txInfo1);
        when(txInfo1.getReceipt()).thenReturn(txr1);
        AionTxInfo txInfo2 = mock(AionTxInfo.class);
        AionTxReceipt txr2 = mock(AionTxReceipt.class);
        when(bc.getTransactionInfo(b2)).thenReturn(txInfo2);
        when(txInfo2.getReceipt()).thenReturn(txr2);

        ReqTxReceiptHandler unit = new ReqTxReceiptHandler(p2pMgr, bc);
        unit.receive(id, displayId, request);

        ArgumentCaptor<ResTxReceipts> receipts = ArgumentCaptor.forClass(ResTxReceipts.class);
        verify(p2pMgr).send(eq(id), eq(displayId), receipts.capture());
        ResTxReceipts receiptsSent = receipts.getValue();
        assertThat(receiptsSent.getTxInfo().size(), is(2));
        assertThat(receiptsSent.getTxInfo().contains(txr1), is(true));
        assertThat(receiptsSent.getTxInfo().contains(txr2), is(true));
    }

    @Test
    public void testReceiveWhenDecodingError() {
        int id = 54321;
        String displayId = "321";
        byte[] badRequest = new byte[] { (byte) 0xc0, (byte) 0xff, (byte) 0xee } ;

        ReqTxReceiptHandler unit = new ReqTxReceiptHandler(p2pMgr, bc);
        unit.receive(id, displayId, badRequest);

        verifyZeroInteractions(p2pMgr);
        verifyZeroInteractions(bc);
    }
}
