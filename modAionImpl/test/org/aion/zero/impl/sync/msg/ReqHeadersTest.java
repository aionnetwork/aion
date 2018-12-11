package org.aion.zero.impl.sync.msg;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** @author chris */
public class ReqHeadersTest {

    @Test
    public void test() {
        long from = 7571;
        int take = 192;

        ReqBlocksHeaders rh = new ReqBlocksHeaders(from, take);
        ReqBlocksHeaders rhNew = ReqBlocksHeaders.decode(rh.encode());
        assertEquals(from, rhNew.getFromBlock());
        assertEquals(take, rhNew.getTake());
    }
}
