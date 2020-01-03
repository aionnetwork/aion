package org.aion.zero.impl.vm;

import org.aion.avm.stub.AvmVersion;
import org.junit.Assert;
import org.junit.Test;

public class AvmVersionTest {

    @Test
    public void testFromNumber() {
        for (AvmVersion version : AvmVersion.values()) {
            Assert.assertEquals(version, AvmVersion.fromNumber(version.number));
        }
    }

    @Test
    public void testHighestVersion() {
        boolean witnessedHighestVersion = false;
        for (AvmVersion version : AvmVersion.values()) {
            Assert.assertTrue(version.number <= AvmVersion.highestSupportedVersion());
            if (version.number == AvmVersion.highestSupportedVersion()) {
                witnessedHighestVersion = true;
            }
        }
        Assert.assertTrue(witnessedHighestVersion);
    }
}
