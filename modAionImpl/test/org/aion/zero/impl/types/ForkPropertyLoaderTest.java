package org.aion.zero.impl.types;

import java.io.IOException;
import org.aion.util.bytes.ByteUtil;
import org.junit.Assert;
import org.junit.Test;

public class ForkPropertyLoaderTest {
    String filePath = "./test_resources/fork.properties.json";

    @Test
    public void loaderTest() throws IOException {
        ProtocolUpgradeSettings settings = ForkPropertyLoader.loadJSON(filePath);
        Assert.assertNotNull(settings);

        Assert.assertEquals("1920000", settings.upgrade.getProperty("fork0.3.2"));
        Assert.assertEquals("3346000", settings.upgrade.getProperty("fork0.4.0"));
        Assert.assertEquals("4721900", settings.upgrade.getProperty("fork1.0"));
        Assert.assertEquals("5371168", settings.upgrade.getProperty("fork1.3"));
        Assert.assertEquals("6235961", settings.upgrade.getProperty("fork1.6"));

        Assert.assertEquals(2, settings.rollbackTransactionHash.size());
        Assert.assertArrayEquals(
                ByteUtil.hexStringToBytes(
                        "0xaff350462b99ab827fca062532c782499fd0f0d144c3232cac4044d263b98487"),
                settings.rollbackTransactionHash.get(0));
        Assert.assertArrayEquals(
                ByteUtil.hexStringToBytes(
                        "0x1111111111111111111111111111111111111111111111111111111111111111"),
                settings.rollbackTransactionHash.get(1));
    }
}
