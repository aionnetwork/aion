package org.aion.cli;

import org.aion.base.type.Address;
import org.aion.mcf.account.Keystore;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.zero.impl.config.CfgAion;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import org.aion.zero.impl.cli.Cli;;

public class CliTest {

    private Cli cli;

    @Before
    public void setup() {
        cli = Mockito.spy(new Cli());
        doReturn("password").when(cli).readPassword(any());
    }

    @Test
    public void testHelp() {
        String args[] = {"-h"};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    @Test
    public void testCreateAccount() {
        String args[] = {"-a", "create"};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    @Test
    public void testListAccounts() {
        String args[] = {"-a", "list"};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    @Test
    public void testExportPrivateKey() {
        String account = Keystore.create("password");

        String[] args = {"-a", "export", account};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    @Test
    public void testImportPrivateKey() {
        ECKey key = ECKeyFac.inst().create();

        String[] args = {"-a", "import", Hex.toHexString(key.getPrivKeyBytes())};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    @Test
    public void testImportPrivateKey2() {
        ECKey key = ECKeyFac.inst().create();
        System.out.println("Original address    : " + Hex.toHexString(key.getAddress()));
        System.out.println("Original public key : " + Hex.toHexString(key.getPubKey()));
        System.out.println("Original private key: " + Hex.toHexString(key.getPrivKeyBytes()));

        String[] args = {"-a", "import", Hex.toHexString(key.getPrivKeyBytes())};
        assertEquals(0, cli.call(args, CfgAion.inst()));

        ECKey key2 = Keystore.getKey(Hex.toHexString(key.getAddress()), "password");
        System.out.println("Imported address    : " + Hex.toHexString(key2.getAddress()));
        System.out.println("Imported public key : " + Hex.toHexString(key2.getPubKey()));
        System.out.println("Imported private key: " + Hex.toHexString(key2.getPrivKeyBytes()));
    }

    @Test
    public void testImportPrivateKeyWrong() {
        String[] args = {"-a", "import", "hello"};
        assertEquals(1, cli.call(args, CfgAion.inst()));
    }
}
