package org.aion.cli;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.mcf.account.Keystore;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.config.CfgAion;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import org.aion.zero.impl.cli.Cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

@RunWith(JUnitParamsRunner.class)
public class CliTest {

    private static final Cli cli = Mockito.spy(new Cli());

    CfgAion cfg = CfgAion.inst();

    String BASE_PATH = cfg.getBasePath();

    /**
     * Sets up a spy Cli class that returns the String "password" when the cli.readPassword()
     * is called using any two params.
     */
    @Before
    public void setup() throws IOException {
        doReturn("password").when(cli).readPassword(any(), any());

        // Copies config folder recursively
        File src = new File(BASE_PATH + "/../modBoot/resource");
        File dst = new File(BASE_PATH + "/config");
        copyRecursively(src, dst);

        CfgAion.setConfFilePath(BASE_PATH + "/config/mainnet/config.xml");
        CfgAion.setGenesisFilePath(BASE_PATH + "/config/mainnet/genesis.json");
        Keystore.setKeystorePath(BASE_PATH + "/keystore");
    }

    @After
    public void shutdown() {
        // Deletes created folders recursively
        File path1 = new File(BASE_PATH + "/aaaaaaaa");
        File path2 = new File(BASE_PATH + "/abbbbbbb");
        File path3 = new File(BASE_PATH + "/abcccccc");
        File path4 = new File(BASE_PATH + "/keystore");
        File path5 = new File(BASE_PATH + "/config");
        if(path1.exists() || path2.exists() || path3.exists() || path4.exists() || path5.exists()) {
            deleteRecursively(path1);
            deleteRecursively(path2);
            deleteRecursively(path3);
            deleteRecursively(path4);
            deleteRecursively(path5);
        }

        CfgAion.setConfFilePath(BASE_PATH + "/config/mainnet/config.xml");
        CfgAion.setGenesisFilePath(BASE_PATH + "/config/mainnet/genesis.json");
        Keystore.setKeystorePath(BASE_PATH + "/keystore");
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
     * Test the -d | --datadir option to see if;
     * 1. Access the correct dbPath
     * 2. Defaults correctly to "database"
     */
    @Test
    public void testDatadir() {

        final String[][] networkArgs = new String[][] {
                { "-d" , "" },                              // Unspecified
                { "-d" , "{@.@}" },                         // Invalid (illegal characters)
                { "-d" , "test_db" , "test"},               // Invalid (number of arguments)
                { "-d" , "aaaaaaaa" },                      // Valid
                { "-d" , "abbbbbbb" },                      // Valid
        };

        Cli cli = new Cli();

        String logOG = cfg.getLog().getLogPath();
        String dbOG = cfg.getDb().getPath();
        System.out.println(logOG + " " + dbOG);

        System.out.println("Invalid Datadir:");
        assertEquals(1, cli.call(networkArgs[0], cfg));
        assertEquals(1, cli.call(networkArgs[1], cfg));
        assertEquals(1, cli.call(networkArgs[2], cfg));

        cfg.getLog().setLogPath(logOG);
        cfg.getDb().setPath(dbOG);
        System.out.println(cfg.getLog().getLogPath() + " " + cfg.getDb().getPath());

        System.out.println("\nValid Datadir 1: " + networkArgs[3][1]);
        assertEquals(2, cli.call(networkArgs[3], cfg) );
        assertEquals("aaaaaaaa/mainnet/database", cfg.getDb().getPath() );
        assertEquals("aaaaaaaa/mainnet/log", cfg.getLog().getLogPath() );
        assertEquals(BASE_PATH + "/aaaaaaaa/mainnet/keystore", Keystore.getKeystorePath());
        printPaths(BASE_PATH, cfg);

        System.out.println("\nValid Datadir 2: " + networkArgs[4][1]);
        assertEquals(2, cli.call(networkArgs[4], cfg) );
        assertEquals("abbbbbbb/mainnet/database", cfg.getDb().getPath() );
        assertEquals("abbbbbbb/mainnet/log", cfg.getLog().getLogPath() );
        assertEquals(BASE_PATH + "/abbbbbbb/mainnet/keystore", Keystore.getKeystorePath());
        printPaths(BASE_PATH, cfg);

    }

    /**
     * Test the -n | --network option to see if;
     * 1. Access the correct CONF_FILE_PATH
     * 2. Access the correct GENESIS_FILE_PATH
     * 3. Defaults correctly to "mainnet"
     */
    @Test
    public void testNetwork() {

        final String[][] networkArgs = new String[][] {
                { "-n" , "" },                              // Unspecified
                { "-n" , "{@.@}" },                         // Invalid (illegal characters)
                { "-n" , "testnet", "test" },               // Invalid (number of arguments)
                { "-n" , "mainnet" },                       // Mainnet
                { "-n" , "testnet" },                       // Testnet
                { "-n" , "conquest" },                      // Conquest
        };

        Cli cli = new Cli();

        System.out.println("Invalid Networks:");
        assertEquals(1, cli.call(networkArgs[0], cfg) );
        assertEquals(1, cli.call(networkArgs[1], cfg) );
        assertEquals(1, cli.call(networkArgs[2], cfg) );

        System.out.println("\nValid Network 1: " + networkArgs[3][1]);
        assertEquals(2, cli.call(networkArgs[3], cfg) );
        assertEquals("mainnet", CfgAion.getNetwork() );
        assertEquals(BASE_PATH + "/config/mainnet/config.xml", CfgAion.getConfFilePath() );
        assertEquals(BASE_PATH + "/config/mainnet/genesis.json", CfgAion.getGenesisFilePath() );
        printPaths(BASE_PATH, cfg);

        System.out.println("\nValid Network 2: " + networkArgs[4][1]);
        assertEquals(2, cli.call(networkArgs[4], cfg) );
        assertEquals("conquest", CfgAion.getNetwork() );
        assertEquals(BASE_PATH + "/config/conquest/config.xml", CfgAion.getConfFilePath() );
        assertEquals(BASE_PATH + "/config/conquest/genesis.json", CfgAion.getGenesisFilePath() );
        printPaths(BASE_PATH, cfg);

        System.out.println("\nValid Network 3: " + networkArgs[5][1]);
        assertEquals(2, cli.call(networkArgs[5], cfg) );
        assertEquals("conquest", CfgAion.getNetwork() );
        assertEquals(BASE_PATH + "/config/conquest/config.xml", CfgAion.getConfFilePath() );
        assertEquals(BASE_PATH + "/config/conquest/genesis.json", CfgAion.getGenesisFilePath() );
        printPaths(BASE_PATH, cfg);

    }

    @Test
    public void testMultiNetworkDatadir() {

        final String[][] multiArgs = new String[][] {
                { "-d" , "aaaaaaaa"     , "-n" , "mainnet"  },          // New network
                { "-d" , "abbbbbbb"     , "-n" , "conquest" },          // New dir
                { "-n" , "conquest"     , "-d" , "abcccccc" },          // New dir
                { "-d" , "aaaaaaaa"     , "-n" , "conquest" },          // Existing dir / network
                { "-n" , "conquest"     , "-d" , "aaaaaaaa" },          // Exisitng network / dir
                { "-d" , "{@.@}"        , "-n" , "conquest" },          // Invalid dir
                { "-n" , "conquest"     , "-d" , "{@.@}"    },          // Invalid dir
                { "-d" , "aaaaaaaa"     , "-n" , "{@.@}"    },          // Invalid network
                { "-n" , "{@.@}"        , "-d" , "aaaaaaaa" },          // Invalid network
        };

        Cli cli = new Cli();
        Cfg cfg = CfgAion.inst();

        System.out.println("\nNew 1: " + multiArgs[0][1] + " " + multiArgs[0][3]);
        assertEquals(2, cli.call(multiArgs[0], cfg));
        assertEquals("mainnet", CfgAion.getNetwork() );
        assertEquals("aaaaaaaa/mainnet/log", cfg.getLog().getLogPath());
        assertEquals("aaaaaaaa/mainnet/database", cfg.getDb().getPath());
        assertEquals(BASE_PATH + "/aaaaaaaa/mainnet/keystore", Keystore.getKeystorePath());
        printPaths(BASE_PATH, cfg);

        System.out.println("\nNew 2: " + multiArgs[1][1] + " " + multiArgs[1][3]);
        assertEquals(2, cli.call(multiArgs[1], cfg));
        assertEquals("conquest", CfgAion.getNetwork() );
        assertEquals("abbbbbbb/conquest/log", cfg.getLog().getLogPath());
        assertEquals("abbbbbbb/conquest/database", cfg.getDb().getPath());
        assertEquals(BASE_PATH + "/abbbbbbb/conquest/keystore", Keystore.getKeystorePath());
        printPaths(BASE_PATH, cfg);

        System.out.println("\nNew 3: " + multiArgs[2][1] + " " + multiArgs[2][3]);
        assertEquals(2, cli.call(multiArgs[2], cfg));
        assertEquals("conquest", CfgAion.getNetwork() );
        assertEquals("abcccccc/conquest/log", cfg.getLog().getLogPath());
        assertEquals("abcccccc/conquest/database", cfg.getDb().getPath());
        assertEquals(BASE_PATH + "/abcccccc/conquest/keystore", Keystore.getKeystorePath());
        printPaths(BASE_PATH, cfg);

        System.out.println("\n Exist 1: " + multiArgs[3][1] + " " + multiArgs[3][3]);
        assertEquals(2, cli.call(multiArgs[3], cfg));
        assertEquals("conquest", CfgAion.getNetwork() );
        assertEquals("aaaaaaaa/conquest/log", cfg.getLog().getLogPath());
        assertEquals("aaaaaaaa/conquest/database", cfg.getDb().getPath());
        assertEquals(BASE_PATH + "/aaaaaaaa/conquest/keystore", Keystore.getKeystorePath());
        printPaths(BASE_PATH, cfg);

        System.out.println("\nExist 2: " + multiArgs[4][1] + " " + multiArgs[4][3]);
        assertEquals(2, cli.call(multiArgs[4], cfg));
        assertEquals("conquest", CfgAion.getNetwork() );
        assertEquals("aaaaaaaa/conquest/log", cfg.getLog().getLogPath());
        assertEquals("aaaaaaaa/conquest/database", cfg.getDb().getPath());
        assertEquals(BASE_PATH + "/aaaaaaaa/conquest/keystore", Keystore.getKeystorePath());
        printPaths(BASE_PATH, cfg);

        // Invalid input
        assertEquals(1, cli.call(multiArgs[5], cfg));
        assertEquals(1, cli.call(multiArgs[6], cfg));
        assertEquals(1, cli.call(multiArgs[7], cfg));
        assertEquals(1, cli.call(multiArgs[8], cfg));
    }

    // The following tests do not have assert; they are only to check which arguments work with multi CLI
    private String[] option1 = new String[] {
            "-h", "-i", "-v"
    };

    private String[][] option2 = new String[][] {
            { "-a", "create" },
            { "-a", "list" },
            { "-s", "create" },
            { "-r", "100" },
            { "-c", "conquest" },
            { "-n", "conquest" },
            { "-d", "test_db" },
    };

    private String[][] option3 = new String[][] {
            { "-a", "export", "9aabf5b86690ca4cae3fada8c72b280c4b9302dd8dd5e17bd788f241d7e3045c" },
            { "-a", "import", "ab5e32b3180abc5251420aecf1cd4ed5f6014757dbdcf595d5ddf907a43ebd4af2d9cac934c028a26a681fe2127d0b602496834d7cfddd0db8a7a45079428525" },
    };

    /**
     * @return parameters for testing
     *         {@link #testMultiFuzz12(String, String[])}
     */
    @SuppressWarnings("unused")
    private Object arg12Parameters() {
        Object[] parameters = new Object[option1.length * option2.length];
        int index = 0;
        for (String arg1 : option1) {
            for (String[] arg2 : option2) {
                parameters[index] = new Object[]{arg1, arg2};
                index++;
            }
        }
        return parameters;
    }

    @Test
    @Parameters(method = "arg12Parameters")
    public void testMultiFuzz12(String arg1, String[] arg2) {
        System.out.println("\nTest Arguments: "+arg1+" "+arg2[0]+" "+arg2[1]);
        String[] args12 = { arg1, arg2[0], arg2[1] };
        cli.call(args12, cfg);
    }

    /**
     * @return parameters for testing
     *         {@link #testMultiFuzz23(String[], String[])}
     */
    @SuppressWarnings("unused")
    private Object arg23Parameters() {
        Object[] parameters = new Object[option2.length * option3.length];
        int index = 0;
        for (String[] arg2 : option2) {
            for (String[] arg3 : option3) {
                parameters[index] = new Object[]{arg2, arg3};
                index++;
            }
        }
        return parameters;
    }

    @Test
    @Parameters(method = "arg23Parameters")
    public void testMultiFuzz23(String[] arg2, String[] arg3) {
        System.out.println( "\nTest Arguments: "+arg2[0]+" "+arg2[1]+" "+arg3[0]+" "+arg3[1]+" "+arg3[2]+"\n");
        String[] args23 = { arg2[0], arg2[1], arg3[0], arg3[1], arg3[2] };
        cli.call(args23, cfg);
    }

    /**
     * @return parameters for testing
     *         {@link #testMultiFuzz31(String[], String)}
     */
    @SuppressWarnings("unused")
    private Object arg31Parameters() {
        Object[] parameters = new Object[option3.length * option1.length];
        int index = 0;
        for (String[] arg3 : option3) {
            for (String arg1 : option1) {
                parameters[index] = new Object[]{arg3, arg1};
                index++;
            }
        }
        return parameters;
    }

    @Test
    @Parameters(method = "arg31Parameters")
    public void testMultiFuzz31(String[] arg3, String arg1) {
        String[] args31 = { arg3[0], arg3[1], arg3[2], arg1 };
        System.out.println( "\nTest Arguments: "+arg3[0]+" "+arg3[1]+" "+arg3[2]+" "+arg1+"\n");
        cli.call(args31, cfg);
    }


    private void printPaths(String BASE_PATH, Cfg cfg) {
        System.out.println("\n-------------------------------- USED PATHS --------------------------------" +
                "\n> Logger path:   " + BASE_PATH + "/" + cfg.getLog().getLogPath() +
                "\n> Database path: " + BASE_PATH + "/" + cfg.getDb().getPath() +
                "\n> Keystore path: " + Keystore.getKeystorePath() +
                "\n> Config write:  " + Cli.getDstConfig() +
                "\n> Genesis write: " + Cli.getDstGenesis() +
                "\n----------------------------------------------------------------------------" +
                "\n> Config read:   " + CfgAion.getConfFilePath() +
                "\n> Genesis read:  " + CfgAion.getGenesisFilePath() +
                "\n----------------------------------------------------------------------------\n\n");
    }

    // Methods below taken from FileUtils class
    private static boolean copyRecursively(File src, File target)
    {
        if (src.isDirectory()) {
            return copyDirectoryContents(src, target);
        }
        else {
            try {
                Files.copy(src, target);
                return true;
            }
            catch (IOException e) {
                return false;
            }
        }
    }

    private static boolean copyDirectoryContents(File src, File target)
    {
        Preconditions.checkArgument(src.isDirectory(), "Source dir is not a directory: %s", src);

        // Don't delete symbolic link directories
        if (isSymbolicLink(src)) {
            return false;
        }

        target.mkdirs();
        Preconditions.checkArgument(target.isDirectory(), "Target dir is not a directory: %s", src);

        boolean success = true;
        for (File file : listFiles(src)) {
            success = copyRecursively(file, new File(target, file.getName())) && success;
        }
        return success;
    }

    private static boolean isSymbolicLink(File file)
    {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            File parentFile = file.getParentFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName()) ||
                    // or the canonical parent path is not the same as the file's parent path,
                    // provided the file has a parent path
                    parentFile != null && !parentFile.getCanonicalPath().equals(canonicalFile.getParent());
        }
        catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    private static ImmutableList<File> listFiles(File dir)
    {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

    private static boolean deleteRecursively(File file) {
        Path path = file.toPath();
        try {
            java.nio.file.Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    java.nio.file.Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(final Path file, final IOException e) {
                    return handleException(e);
                }

                private FileVisitResult handleException(final IOException e) {
                    // e.printStackTrace();
                    return FileVisitResult.TERMINATE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException e) throws IOException {
                    if (e != null)
                        return handleException(e);
                    java.nio.file.Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

}