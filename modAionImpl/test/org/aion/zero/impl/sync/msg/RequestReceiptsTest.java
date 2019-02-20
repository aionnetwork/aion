package org.aion.zero.impl.sync.msg;

import org.aion.base.util.ByteUtil;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Ver;
import org.aion.zero.impl.sync.Act;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class RequestReceiptsTest {
    @Test
    public void testBaseCtor() {
        byte[] b1 = new byte[] {0xa};
        byte[] b2 = new byte[] {0xb};
        List<byte[]> receipts = List.of(b1, b2);

        RequestReceipts unit = new RequestReceipts(receipts);

        assertThat(unit.getTxHashes().size(), is(2));
        assertThat(unit.getTxHashes().containsAll(receipts), is(true));
        assertThat(unit.getHeader().getAction(), is(Act.REQUEST_RECEIPTS));
        assertThat(unit.getHeader().getCtrl(), is(Ctrl.SYNC));
        assertThat(unit.getHeader().getVer(), is(Ver.V0));
    }

    @Test
    public void testDecodingCtor() {
        byte[] b1 =
                ByteUtil.hexStringToBytes(
                        ""
                                + "01020304050a0b0c"
                                + "05040302010a0b0c"
                                + "00000000000d0e0f"
                                + "05040302010a0b0c"); // 32 bytes
        byte[] b2 =
                ByteUtil.hexStringToBytes(
                        ""
                                + "01000000000f0f0f"
                                + "02000000000a0b0c"
                                + "03000000000d0e0f"
                                + "04000000000a0b0c"); // 32 bytes
        ByteBuffer encodedRequest = ByteBuffer.allocate(64).put(b1).put(b2);

        RequestReceipts unit = new RequestReceipts(encodedRequest.array());

        assertThat(unit.getTxHashes().size(), is(2));
        assertThat(unit.getTxHashes().get(0), is(b1));
        assertThat(unit.getTxHashes().get(1), is(b2));
        assertThat(unit.getHeader().getAction(), is(Act.REQUEST_RECEIPTS));
        assertThat(unit.getHeader().getCtrl(), is(Ctrl.SYNC));
        assertThat(unit.getHeader().getVer(), is(Ver.V0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDecodingCtorWhenMsgLengthNotValid() {
        byte[] b1 =
                ByteUtil.hexStringToBytes(
                        ""
                                + "01020304050a0b0c"
                                + "05040302010a0b0c"
                                + "00000000000d0e0f"
                                + "05040302010a0b0c"); // 32 bytes
        byte[] b2 =
                ByteUtil.hexStringToBytes(
                        ""
                                + "01000000000f0f0f"
                                + "02000000000a0b0c"
                                + "03000000000d0e0f"
                                + "04000000000a0b0c"); // 32 bytes
        ByteBuffer brokenEncoding = ByteBuffer.allocate(65).put(b1).put(b2);
        brokenEncoding.put((byte) 0xff);

        new RequestReceipts(brokenEncoding.array());
    }
}
