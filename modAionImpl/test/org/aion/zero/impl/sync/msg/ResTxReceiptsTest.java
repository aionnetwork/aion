package org.aion.zero.impl.sync.msg;

import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;

import java.util.List;

import static org.aion.base.util.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResTxReceiptsTest {
    @Test
    public void testBaseCtor() {
        AionTxInfo r1 = mock(AionTxInfo.class);
        AionTxInfo r2 = mock(AionTxInfo.class);
        AionTxInfo r3 = mock(AionTxInfo.class);
        ResTxReceipts unit = new ResTxReceipts(List.of(r1, r2, r3));
        assertThat(unit.getTxInfo().size(), is(3));
        assertThat(unit.getTxInfo().containsAll(
                List.of(r1, r2, r3)
        ), is(true));
        assertThat(unit.getHeader().getAction(), is(Act.RES_TX_RECEIPT_HEADERS));
        assertThat(unit.getHeader().getCtrl(), is(Ctrl.SYNC));
        assertThat(unit.getHeader().getVer(), is(Ver.V0));
    }

    @Test
    public void testDecodingCtor() {
        AionTxReceipt r1 = new AionTxReceipt();
        AionTxReceipt r2 = new AionTxReceipt();
        r1.setError("");
        r1.setExecutionResult(HashUtil.h256(EMPTY_BYTE_ARRAY));
        r2.setError("");
        r2.setExecutionResult(HashUtil.h256(EMPTY_BYTE_ARRAY));
        AionTxInfo inf1 = new AionTxInfo(r1);
        AionTxInfo inf2 = new AionTxInfo(r2);

        byte[] encodedRequest = new ResTxReceipts(List.of(inf1, inf2)).encode();
        ResTxReceipts unit = new ResTxReceipts(encodedRequest);
        assertThat(unit.getTxInfo().size(), is(2));
        assertThat(unit.getTxInfo().containsAll(
                List.of(inf1, inf2)
        ), is(true));
        assertThat(unit.getHeader().getAction(), is(Act.RES_TX_RECEIPT_HEADERS));
        assertThat(unit.getHeader().getCtrl(), is(Ctrl.SYNC));
        assertThat(unit.getHeader().getVer(), is(Ver.V0));
    }

    @Test
    public void testEncode() {
        byte[] txBytes1 = ByteUtil.hexStringToBytes("C0FFEE");
        byte[] txBytes2 = ByteUtil.hexStringToBytes("DECAF");
        AionTxInfo r1 = mock(AionTxInfo.class);
        AionTxInfo r2 = mock(AionTxInfo.class);
        when(r1.getEncoded()).thenReturn(txBytes1);
        when(r2.getEncoded()).thenReturn(txBytes2);

        ResTxReceipts unit = new ResTxReceipts(List.of(r1, r2));
        assertThat(unit.encode(), is(RLP.encodeList(txBytes1, txBytes2)));
    }
}