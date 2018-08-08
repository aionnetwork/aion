package org.aion.cli;

import org.aion.db.utils.FileUtils;
import org.aion.mcf.account.Keystore;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.config.CfgAion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import org.aion.zero.impl.cli.Cli;

import java.io.File;

public class CliTest {

    private static final Cli cli = Mockito.spy(new Cli());

    /**
     * Sets up a spy Cli class that returns the String "password" when the cli.readPassword()
     * is called using any two params.
     */
    @Before
    public void setup() {
        doReturn("password").when(cli).readPassword(any(), any());

        // Copies config folder recursively
        String BASE_PATH = Cfg.getBasePath();
        File src = new File(BASE_PATH + "/../config");
        File dst = new File(BASE_PATH + "/config");
        FileUtils.copyRecursively(src, dst);
    }

    @After
    public void shutdown() {
        String BASE_PATH = Cfg.getBasePath();
        File dst = new File(BASE_PATH + "/config");
        FileUtils.deleteRecursively(dst);
    }

    /**
     * Tests the -h argument does not fail.
     */
    @Test
    public void testHelp() {
        String args[] = {"-h"};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    /**
     * Tests the -a create arguments do not fail.
     */
    @Test
    public void testCreateAccount() {
        String args[] = {"-a", "create"};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    /**
     * Tests the -a list arguments do not fail.
     */
    @Test
    public void testListAccounts() {
        String args[] = {"-a", "list"};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    /**
     * Tests the -a export arguments do not fail on a valid account.
     */
    @Test
    public void testExportPrivateKey() {
        String account = Keystore.create("password");

        String[] args = {"-a", "export", account};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    /**
     * Tests the -a export arguments fail when the suupplied account is a proper substring of a
     * valid account.
     */
    @Test
    public void testExportSubstringOfAccount() {
        String account = Keystore.create("password");
        String substrAcc = account.substring(1);

        String[] args = {"-a", "export", substrAcc};
        assertEquals(1, cli.call(args, CfgAion.inst()));
    }

    /**
     * Tests the -a import arguments do not fail on a fail import key.
     */
    @Test
    public void testImportPrivateKey() {
        ECKey key = ECKeyFac.inst().create();

        String[] args = {"-a", "import", Hex.toHexString(key.getPrivKeyBytes())};
        assertEquals(0, cli.call(args, CfgAion.inst()));
    }

    /**
     * Tests the -a import arguments fail when a non-private key is supplied.
     */
    @Test
    public void testImportNonPrivateKey() {
        String account = Keystore.create("password");

        String[] args = {"-a", "import", account};
        assertEquals(1, cli.call(args, CfgAion.inst()));
    }

    /**
     * Tests the -a import arguments do not fail when a valid private key is supplied.
     */
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

    /**
     * Tests the -a import arguments fail given an invalid private key.
     */
    @Test
    public void testImportPrivateKeyWrong() {
        String[] args = {"-a", "import", "hello"};
        assertEquals(1, cli.call(args, CfgAion.inst()));
    }

    /**
     * Test the -n | --network option to see if;
     * 1. Access the correct CONF_FILE_PATH
     * 2. Access the correct GENESIS_FILE_PATH
     * 3. Defaults correctly to "mainnet"
     */
    @Test
    public void testNetwork() {

        String BASE_PATH = Cfg.getBasePath();
        final String[][] networkArgs = new String[][] {
                { "-n" , "" },              // Unspecified
                { "-n" , "{@.@}" },         // Invalid
                { "-n" , "testnet" },       // Default
                { "-n" , "mainnet" },       // Mainnet
                { "-n" , "conquest" }       // Conquest
        };

        Cli cli = new Cli();
        Cfg cfg = CfgAion.inst();

        assertEquals(1, cli.call(networkArgs[0], cfg) );
        assertEquals("mainnet", CfgAion.getNetwork() );
        assertEquals(BASE_PATH + "/config/mainnet/config.xml", CfgAion.getConfFilePath() );
        assertEquals(BASE_PATH + "/config/mainnet/genesis.json", CfgAion.getGenesisFilePath() );

        assertEquals(1, cli.call(networkArgs[1], cfg) );
        assertEquals("mainnet", CfgAion.getNetwork() );
        assertEquals(BASE_PATH + "/config/mainnet/config.xml", CfgAion.getConfFilePath() );
        assertEquals(BASE_PATH + "/config/mainnet/genesis.json", CfgAion.getGenesisFilePath() );

        assertEquals(0, cli.call(networkArgs[2], cfg) );
        assertEquals("mainnet", CfgAion.getNetwork() );
        assertEquals(BASE_PATH + "/config/mainnet/config.xml", CfgAion.getConfFilePath() );
        assertEquals(BASE_PATH + "/config/mainnet/genesis.json", CfgAion.getGenesisFilePath() );

        assertEquals(0, cli.call(networkArgs[3], cfg) );
        assertEquals("mainnet", CfgAion.getNetwork() );
        assertEquals(BASE_PATH + "/config/mainnet/config.xml", CfgAion.getConfFilePath() );
        assertEquals(BASE_PATH + "/config/mainnet/genesis.json", CfgAion.getGenesisFilePath() );

        assertEquals(0, cli.call(networkArgs[4], cfg) );
        assertEquals("conquest", CfgAion.getNetwork() );
        assertEquals(BASE_PATH + "/config/conquest/config.xml", CfgAion.getConfFilePath() );
        assertEquals(BASE_PATH + "/config/conquest/genesis.json", CfgAion.getGenesisFilePath() );
    }

    /**
     * Test the -d | --datadir option to see if;
     * 1. Access the correct dbPath
     * 2. Defaults correctly to "database"
     */
    @Test
    public void testDatabase() {

        final String[][] networkArgs = new String[][] {
                { "-d" , "" },              // Unspecified
                { "-d" , "{@.@}" },         // Invalid
                { "-d" , "database" },      // Default
                { "-d" , "test_db" }        // Valid
        };

        Cli cli = new Cli();
        Cfg cfg = CfgAion.inst();

        assertEquals(1, cli.call(networkArgs[0], cfg));
        assertEquals("database", cfg.getDb().getPath());

        assertEquals(1, cli.call(networkArgs[1], cfg) );
        assertEquals("database", cfg.getDb().getPath() );

        assertEquals(0, cli.call(networkArgs[2], cfg) );
        assertEquals("database/database", cfg.getDb().getPath() );

        assertEquals(0, cli.call(networkArgs[3], cfg) );
        assertEquals("test_db/database", cfg.getDb().getPath() );
    }

}