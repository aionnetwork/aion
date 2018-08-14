package org.aion.rlp;

import static com.google.common.truth.Truth.assertThat;

import org.aion.base.util.ByteUtil;
import org.junit.Test;

public class RLPElementTest {

    @Test(expected = RuntimeException.class)
    public void testRLPList_wRuntimeException() {
        RLPList.recursivePrint(null);
    }

    @Test
    public void testRLPItem_wNull() {
        RLPItem item = new RLPItem(null);
        assertThat(item.getRLPData()).isEqualTo(ByteUtil.EMPTY_BYTE_ARRAY);
    }
}
