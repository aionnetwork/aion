package org.aion.crypto;

import org.aion.base.util.ByteUtil;
import org.junit.Test;
import java.util.Optional;
import static org.junit.Assert.assertEquals;

public class ChecksumTest {

    @Test
    public void testComputeA0Address() {
        byte[] input = HashUtil.h256(Integer.toHexString(0).getBytes());
        String input_address = ByteUtil.toHexString((HashUtil.h256(input)));
        String output_address = ByteUtil.toHexString(AddressSpecs.computeA0Address(input));
        assertEquals(output_address.substring(2), input_address.substring(2));
    }

    @Test
    public void testChecksum() {
        String input = "0xa08896b9366f09e5efb1fa2ed9f3820b865ae97adbc6f364d691eb17784c9b1b";
        String expected = "A08896B9366f09e5EfB1fA2ed9f3820B865AE97ADBc6f364D691eB17784c9b1b";
        assertEquals(Optional.of(expected), (AddressSpecs.checksummedAddress(input)));
    }

    @Test
    public void testChecksum0x() {
        String input = "a0x896b9366f09e5efb1fa2ed9f3820b865ae97adbc6f364d691eb17784c9b1b"; //0x is not removed, h == null
        assertEquals(Optional.empty(), (AddressSpecs.checksummedAddress(input)));
    }

    @Test
    public void testChecksumnull() {
        String input = "0xa~8896b9366f09e5efb1fa2ed9f3820b865ae97adbc6f364d691eb17784c9b1b";
        assertEquals(Optional.empty(), (AddressSpecs.checksummedAddress(input)));
    }
}
