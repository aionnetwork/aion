package org.aion.crypto;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.junit.Test;

public class ChecksumTest {

    @Test
    public void testChecksum() {
        System.out.println(AddressSpecs.checksummedAddress("a08896b9366f09e5efb1fa2ed9f3820b865ae97adbc6f364d691eb17784c9b1b"));
    }
}
