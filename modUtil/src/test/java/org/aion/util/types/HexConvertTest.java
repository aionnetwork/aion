package org.aion.util.types;

import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HexConvertTest {

    @Test
    public void bytesToHexStringReturnsNullForNullInput() {
        assertNull(HexConvert.bytesToHexString(null));
    }
}
