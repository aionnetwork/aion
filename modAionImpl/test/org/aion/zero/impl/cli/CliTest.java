/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.zero.impl.cli;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.cli.Cli.ReturnType.ERROR;
import static org.aion.zero.impl.cli.Cli.ReturnType.EXIT;
import static org.aion.zero.impl.cli.Cli.ReturnType.RUN;
import static org.junit.Assert.assertEquals;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.base.util.Hex;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.cli.Cli.ReturnType;
import org.aion.zero.impl.config.CfgAion;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** CliTest for new version with use of different networks. */
@RunWith(JUnitParamsRunner.class)
public class CliTest {

    private Cli cli = new Cli();
    private CfgAion cfg = CfgAion.inst();

    private static final String BASE_PATH = System.getProperty("user.dir");
    private static final String module = "modAionImpl";
    private static final String initialConfigFile = "test_resources/config.xml";
    private static final String initialGenesisFile = "test_resources/genesis.json";
    private static final String dataDirectory = "datadir";
    private static final File path = new File(BASE_PATH, dataDirectory);

    private static File config =
            BASE_PATH.contains(module)
                    ? new File(BASE_PATH, initialConfigFile)
                    : new File(BASE_PATH, module + "/" + initialConfigFile);
    private static File oldConfig = new File(BASE_PATH, "config/config.xml");
    private static File mainnetConfig = new File(BASE_PATH, "config/mainnet/config.xml");
    private static File testnetConfig = new File(BASE_PATH, "config/mastery/config.xml");

    private static File genesis =
            BASE_PATH.contains(module)
                    ? new File(BASE_PATH, initialGenesisFile)
                    : new File(BASE_PATH, module + "/" + initialGenesisFile);
    private static File oldGenesis = new File(BASE_PATH, "config/genesis.json");
    private static File mainnetGenesis = new File(BASE_PATH, "config/mainnet/genesis.json");
    private static File testnetGenesis = new File(BASE_PATH, "config/mastery/genesis.json");

    /** @implNote set this to true to enable printing */
    private static boolean verbose = true;

    @Before
    public void setup() {
        // reset config values
        cfg.fromXML(config);

        if (BASE_PATH.contains(module) && !mainnetConfig.exists()) {
            // save config to disk at expected location for new kernel
            File configPath = new File(BASE_PATH, "config/mainnet");
            if (!configPath.exists()) {
                configPath.mkdirs();
            }
            cfg.toXML(null, mainnetConfig);
            Cli.copyRecursively(genesis, mainnetGenesis);
        }

        if (BASE_PATH.contains(module) && !testnetConfig.exists()) {
            // save config to disk at expected location for new kernel
            File configPath = new File(BASE_PATH, "config/mastery");
            if (!configPath.exists()) {
                configPath.mkdirs();
            }
            cfg.toXML(null, testnetConfig);
            Cli.copyRecursively(genesis, testnetGenesis);
        }

        cfg.resetInternal();
    }

    @After
    public void shutdown() {
        deleteRecursively(path);

        // to avoid deleting config for all tests
        if (BASE_PATH.contains(module)) {
            deleteRecursively(new File(BASE_PATH, "config"));
        }

        deleteRecursively(new File(BASE_PATH, "mainnet"));
        deleteRecursively(new File(BASE_PATH, "mastery"));
    }

    /**
     * Ensures that the { <i>-h</i>, <i>--help</i>, <i>-v</i>, <i>--version</i> } arguments work.
     */
    @Test
    @Parameters({"-h", "--help", "-v", "--version"})
    public void testHelpAndVersion(String option) {
        assertThat(cli.call(new String[] {option}, cfg)).isEqualTo(EXIT);
    }

    /** Parameters for testing {@link #testDirectoryAndNetwork(String[], ReturnType, String)}. */
    @SuppressWarnings("unused")
    private Object parametersWithDirectoryAndNetwork() {
        List<Object> parameters = new ArrayList<>();

        String[] dir_options = new String[] {"-d", "--datadir"};
        String[] net_options = new String[] {"-n", "--network"};
        String expected = new File(path, "mainnet").getAbsolutePath();

        // data directory alone
        for (String op : dir_options) {
            // with relative path
            parameters.add(new Object[] {new String[] {op, dataDirectory}, RUN, expected});
            // with absolute path
            parameters.add(new Object[] {new String[] {op, path.getAbsolutePath()}, RUN, expected});
            // without value
            parameters.add(new Object[] {new String[] {op}, ERROR, BASE_PATH});
            // with invalid characters (Linux & Win)
            parameters.add(new Object[] {new String[] {op, "/\\<>:\"|?*"}, ERROR, BASE_PATH});
        }

        // network and directory
        String[] net_values = new String[] {"mainnet", "invalid"};
        for (String opDir : dir_options) {
            for (String opNet : net_options) {
                for (String valNet : net_values) {
                    // with relative path
                    parameters.add(
                            new Object[] {
                                new String[] {opDir, dataDirectory, opNet, valNet}, RUN, expected
                            });
                    parameters.add(
                            new Object[] {
                                new String[] {opNet, valNet, opDir, dataDirectory}, RUN, expected
                            });
                    // with absolute path
                    parameters.add(
                            new Object[] {
                                new String[] {opDir, path.getAbsolutePath(), opNet, valNet},
                                RUN,
                                expected
                            });
                    parameters.add(
                            new Object[] {
                                new String[] {opNet, valNet, opDir, path.getAbsolutePath()},
                                RUN,
                                expected
                            });
                }
            }
        }

        // network alone
        expected = new File(BASE_PATH, "mainnet").getAbsolutePath();
        for (String op : net_options) {
            // without parameter
            parameters.add(new Object[] {new String[] {op}, ERROR, BASE_PATH});
            // with two parameters
            parameters.add(
                    new Object[] {new String[] {op, "testnet", "mastery"}, ERROR, BASE_PATH});
            // invalid parameter
            parameters.add(new Object[] {new String[] {op, "invalid"}, RUN, expected});
            // mainnet as parameter
            parameters.add(new Object[] {new String[] {op, "mainnet"}, RUN, expected});
        }

        // network alone with testnet
        net_values = new String[] {"mastery", "testnet"};
        expected = new File(BASE_PATH, "mastery").getAbsolutePath();
        for (String op : net_options) {
            for (String netVal : net_values) {
                // mastery as parameter
                parameters.add(new Object[] {new String[] {op, netVal}, RUN, expected});
            }
        }

        // network and directory with testnet
        expected = new File(path, "mastery").getAbsolutePath();
        for (String opDir : dir_options) {
            for (String opNet : net_options) {
                for (String netVal : net_values) {
                    // with relative path
                    parameters.add(
                            new Object[] {
                                new String[] {opDir, dataDirectory, opNet, netVal}, RUN, expected
                            });
                    parameters.add(
                            new Object[] {
                                new String[] {opNet, netVal, opDir, dataDirectory}, RUN, expected
                            });
                    // with absolute path
                    parameters.add(
                            new Object[] {
                                new String[] {opDir, path.getAbsolutePath(), opNet, netVal},
                                RUN,
                                expected
                            });
                    parameters.add(
                            new Object[] {
                                new String[] {opNet, netVal, opDir, path.getAbsolutePath()},
                                RUN,
                                expected
                            });
                }
            }
        }

        // with subdirectories
        String dir = dataDirectory + "/subfolder";
        File path = new File(BASE_PATH, dir);
        expected = new File(path, "mainnet").getAbsolutePath();
        for (String op : dir_options) {
            // with relative path with subdirectories
            parameters.add(new Object[] {new String[] {op, dir}, RUN, expected});
            // with multiple values
            parameters.add(new Object[] {new String[] {op, dataDirectory, dir}, ERROR, BASE_PATH});
        }

        return parameters.toArray();
    }

    /**
     * Ensures that the { <i>-d</i>, <i>--datadir</i>, <i>-n</i>, <i>--network</i> } arguments work.
     */
    @Test
    @Parameters(method = "parametersWithDirectoryAndNetwork")
    public void testDirectoryAndNetwork(
            String[] input, ReturnType expectedReturn, String expectedPath) {
        assertThat(cli.call(input, cfg)).isEqualTo(expectedReturn);
        assertThat(cfg.getBasePath()).isEqualTo(expectedPath);
        assertThat(cfg.getExecConfigPath())
                .isEqualTo(new File(expectedPath, "config/config.xml").getAbsolutePath());
        assertThat(cfg.getExecGenesisPath())
                .isEqualTo(new File(expectedPath, "config/genesis.json").getAbsolutePath());
        assertThat(cfg.getDatabasePath())
                .isEqualTo(new File(expectedPath, "database").getAbsolutePath());
        assertThat(cfg.getLogPath()).isEqualTo(new File(expectedPath, "log").getAbsolutePath());
        assertThat(cfg.getKeystorePath())
                .isEqualTo(new File(expectedPath, "keystore").getAbsolutePath());

        if (verbose) {
            printPaths(cfg);
        }
    }

    private void printPaths(Cfg cfg) {
        System.out.println(
                "\n-------------------------------- USED PATHS --------------------------------"
                        + "\n> Logger path:   "
                        + cfg.getLogPath()
                        + "\n> Database path: "
                        + cfg.getDatabasePath()
                        + "\n> Keystore path: "
                        + cfg.getKeystorePath()
                        + "\n> Config write:  "
                        + cfg.getExecConfigPath()
                        + "\n> Genesis write: "
                        + cfg.getExecGenesisPath()
                        + "\n----------------------------------------------------------------------------"
                        + "\n> Config read:   "
                        + cfg.getInitialConfigPath()
                        + "\n> Genesis read:  "
                        + cfg.getInitialGenesisPath()
                        + "\n----------------------------------------------------------------------------\n\n");
    }

    /** Parameters for testing {@link #testConfig(String[], String)}. */
    @SuppressWarnings("unused")
    private Object parametersWithConfig() {
        List<Object> parameters = new ArrayList<>();

        String[] options = new String[] {"-c", "--config"};
        String expected = new File(BASE_PATH, "mainnet").getAbsolutePath();

        for (String op : options) {
            // without parameter
            parameters.add(new Object[] {new String[] {op}, expected});
            // invalid parameter
            parameters.add(new Object[] {new String[] {op, "invalid"}, expected});
            // mainnet as parameter
            parameters.add(new Object[] {new String[] {op, "mainnet"}, expected});
        }

        expected = new File(BASE_PATH, "mastery").getAbsolutePath();

        for (String op : options) {
            // mastery as parameter
            parameters.add(new Object[] {new String[] {op, "mastery"}, expected});
            // testnet as parameter
            parameters.add(new Object[] {new String[] {op, "testnet"}, expected});
        }

        // config and directory
        String[] dir_options = new String[] {"-d", "--datadir"};
        expected = new File(path, "mainnet").getAbsolutePath();

        String[] net_values = new String[] {"mainnet", "invalid"};
        for (String opDir : dir_options) {
            for (String opCfg : options) {
                for (String valNet : net_values) {
                    // with relative path
                    parameters.add(
                            new Object[] {
                                new String[] {opDir, dataDirectory, opCfg, valNet}, expected
                            });
                    parameters.add(
                            new Object[] {
                                new String[] {opCfg, valNet, opDir, dataDirectory}, expected
                            });
                    // with absolute path
                    parameters.add(
                            new Object[] {
                                new String[] {opDir, path.getAbsolutePath(), opCfg, valNet},
                                expected
                            });
                    parameters.add(
                            new Object[] {
                                new String[] {opCfg, valNet, opDir, path.getAbsolutePath()},
                                expected
                            });
                }
            }
        }

        // config and directory with testnet
        net_values = new String[] {"mastery", "testnet"};
        expected = new File(path, "mastery").getAbsolutePath();
        for (String opDir : dir_options) {
            for (String opCfg : options) {
                for (String netVal : net_values) {
                    // with relative path
                    parameters.add(
                            new Object[] {
                                new String[] {opDir, dataDirectory, opCfg, netVal}, expected
                            });
                    parameters.add(
                            new Object[] {
                                new String[] {opCfg, netVal, opDir, dataDirectory}, expected
                            });
                    // with absolute path
                    parameters.add(
                            new Object[] {
                                new String[] {opDir, path.getAbsolutePath(), opCfg, netVal},
                                expected
                            });
                    parameters.add(
                            new Object[] {
                                new String[] {opCfg, netVal, opDir, path.getAbsolutePath()},
                                expected
                            });
                }
            }
        }

        return parameters.toArray();
    }

    /** Ensures that the { <i>-c</i>, <i>--config</i> } arguments work. */
    @Test
    @Parameters(method = "parametersWithConfig")
    public void testConfig(String[] input, String expectedPath) {
        assertThat(cli.call(input, cfg)).isEqualTo(EXIT);
        assertThat(cfg.getBasePath()).isEqualTo(expectedPath);
        assertThat(cfg.getExecConfigPath())
                .isEqualTo(new File(expectedPath, "config/config.xml").getAbsolutePath());
        assertThat(cfg.getExecGenesisPath())
                .isEqualTo(new File(expectedPath, "config/genesis.json").getAbsolutePath());

        if (verbose) {
            printPaths(cfg);
        }
    }

    /** Parameters for testing {@link #testConfig_oldLocation(String[], String)}. */
    @SuppressWarnings("unused")
    private Object parametersWithConfigForOldLocation() {
        List<Object> parameters = new ArrayList<>();

        String[] options = new String[] {"-c", "--config"};
        String expected = new File(BASE_PATH, "mainnet").getAbsolutePath();

        for (String op : options) {
            // without parameter
            parameters.add(new Object[] {new String[] {op}, BASE_PATH});
            // invalid parameter
            parameters.add(new Object[] {new String[] {op, "invalid"}, expected});
            // mainnet as parameter
            parameters.add(new Object[] {new String[] {op, "mainnet"}, expected});
        }

        expected = new File(BASE_PATH, "mastery").getAbsolutePath();

        for (String op : options) {
            // mastery as parameter
            parameters.add(new Object[] {new String[] {op, "mastery"}, expected});
            // testnet as parameter
            parameters.add(new Object[] {new String[] {op, "testnet"}, expected});
        }

        return parameters.toArray();
    }

    /**
     * Ensures that the { <i>-c</i>, <i>--config</i> } arguments work when using old config
     * location.
     */
    @Test
    @Parameters(method = "parametersWithConfigForOldLocation")
    public void testConfig_oldLocation(String[] input, String expectedPath) {
        // ensure config exists on disk at expected location for old kernel
        if (!oldConfig.exists()) {
            File configPath = new File(BASE_PATH, "config");
            if (!configPath.exists()) {
                configPath.mkdirs();
            }
            cfg.toXML(null, oldConfig);
            Cli.copyRecursively(genesis, oldGenesis);
        }

        assertThat(cli.call(input, cfg)).isEqualTo(EXIT);
        assertThat(cfg.getBasePath()).isEqualTo(expectedPath);
        assertThat(cfg.getExecConfigPath())
                .isEqualTo(new File(expectedPath, "config/config.xml").getAbsolutePath());
        assertThat(cfg.getExecGenesisPath())
                .isEqualTo(new File(expectedPath, "config/genesis.json").getAbsolutePath());

        if (verbose) {
            printPaths(cfg);
        }
    }

    /** Parameters for testing {@link #testInfo(String[], ReturnType, String)}. */
    @SuppressWarnings("unused")
    private Object parametersWithInfo() {
        List<Object> parameters = new ArrayList<>();

        String[] options = new String[] {"-i", "--info"};
        String expected = new File(BASE_PATH, "mainnet").getAbsolutePath();

        // only info
        for (String op : options) {
            // without parameter
            parameters.add(new Object[] {new String[] {op}, EXIT, expected});
            // invalid parameter
            parameters.add(new Object[] {new String[] {op, "value"}, ERROR, BASE_PATH});
        }

        // with network
        expected = new File(BASE_PATH, "mastery").getAbsolutePath();
        for (String op : options) {
            // mastery as parameter
            parameters.add(new Object[] {new String[] {op, "-n", "mastery"}, EXIT, expected});
            parameters.add(new Object[] {new String[] {"-n", "mastery", op}, EXIT, expected});
            // invalid parameter
            parameters.add(
                    new Object[] {new String[] {op, "value", "-n", "mastery"}, ERROR, BASE_PATH});
        }

        // with directory
        expected = new File(path, "mainnet").getAbsolutePath();
        for (String op : options) {
            // with relative path
            parameters.add(new Object[] {new String[] {op, "-d", dataDirectory}, EXIT, expected});
            parameters.add(new Object[] {new String[] {"-d", dataDirectory, op}, EXIT, expected});
            // + invalid parameter
            parameters.add(
                    new Object[] {
                        new String[] {op, "value", "-d", dataDirectory}, ERROR, BASE_PATH
                    });
            // with absolute path
            parameters.add(
                    new Object[] {new String[] {op, "-d", path.getAbsolutePath()}, EXIT, expected});
            parameters.add(
                    new Object[] {new String[] {"-d", path.getAbsolutePath(), op}, EXIT, expected});
            // + invalid parameter
            parameters.add(
                    new Object[] {
                        new String[] {op, "value", "-d", path.getAbsolutePath()}, ERROR, BASE_PATH
                    });
        }

        // with network and directory
        expected = new File(path, "mastery").getAbsolutePath();
        for (String op : options) {
            // with relative path
            parameters.add(
                    new Object[] {
                        new String[] {op, "-d", dataDirectory, "-n", "mastery"}, EXIT, expected
                    });
            parameters.add(
                    new Object[] {
                        new String[] {"-n", "mastery", op, "-d", dataDirectory}, EXIT, expected
                    });
            parameters.add(
                    new Object[] {
                        new String[] {"-n", "mastery", "-d", dataDirectory, op}, EXIT, expected
                    });
            // with absolute path
            parameters.add(
                    new Object[] {
                        new String[] {op, "-n", "mastery", "-d", path.getAbsolutePath()},
                        EXIT,
                        expected
                    });
            parameters.add(
                    new Object[] {
                        new String[] {"-d", path.getAbsolutePath(), op, "-n", "mastery"},
                        EXIT,
                        expected
                    });
            parameters.add(
                    new Object[] {
                        new String[] {"-d", path.getAbsolutePath(), "-n", "mastery", op},
                        EXIT,
                        expected
                    });
        }

        return parameters.toArray();
    }

    /** Ensures that the { <i>-i</i>, <i>--info</i> } arguments work. */
    @Test
    @Parameters(method = "parametersWithInfo")
    public void testInfo(String[] input, ReturnType expectedReturn, String expectedPath) {
        assertThat(cli.call(input, cfg)).isEqualTo(expectedReturn);
        assertThat(cfg.getBasePath()).isEqualTo(expectedPath);
    }

    /**
     * Ensures that the { <i>-i</i>, <i>--info</i> } arguments work when using old config location.
     */
    @Test
    @Parameters({"-i", "--info"})
    public void testInfo_oldLocation(String option) {
        // ensure config exists on disk at expected location for old kernel
        if (!oldConfig.exists()) {
            File configPath = new File(BASE_PATH, "config");
            if (!configPath.exists()) {
                configPath.mkdirs();
            }
            cfg.toXML(null, oldConfig);
            Cli.copyRecursively(genesis, oldGenesis);
        }

        assertThat(cli.call(new String[] {option}, cfg)).isEqualTo(EXIT);
        assertThat(cfg.getBasePath()).isEqualTo(BASE_PATH);
    }

    /**
     * Ensures that the { <i>-i</i>, <i>--info</i> } arguments work when using the config location
     * copied to the execution directory.
     */
    @Test
    @Parameters({"-i", "--info"})
    public void testInfo_execLocation(String option) {
        // generates the config at the required destination
        cli.call(new String[] {"-c", "-d", dataDirectory}, cfg);

        // ensure config exists on disk at expected location for old kernel
        assertThat(cfg.getExecConfigFile().exists()).isTrue();

        assertThat(cli.call(new String[] {option, "-d", dataDirectory}, cfg)).isEqualTo(EXIT);
    }

    //    /**
    //     * Sets up a spy Cli class that returns the String "password" when the cli.readPassword()
    // is
    //     * called using any two params.
    //     */
    //    @Before
    //    public void setup() throws IOException {
    //        doReturn("password").when(cli).readPassword(any(), any());
    //
    //        // Copies config folder recursively
    //        File src = new File(BASE_PATH + "/../modBoot/resource");
    //        File dst = new File(BASE_PATH + "/config");
    //        copyRecursively(src, dst);
    //
    //        CfgAion.setConfFilePath(BASE_PATH + "/config/mainnet/config.xml");
    //        CfgAion.setGenesisFilePath(BASE_PATH + "/config/mainnet/genesis.json");
    //        Keystore.setKeystorePath(BASE_PATH + "/keystore");
    //    }
    //
    //    @After
    //    public void shutdown() {
    //        // Deletes created folders recursively
    //        File path1 = new File(BASE_PATH + "/aaaaaaaa");
    //        File path2 = new File(BASE_PATH + "/abbbbbbb");
    //        File path3 = new File(BASE_PATH + "/abcccccc");
    //        File path4 = new File(BASE_PATH + "/keystore");
    //        File path5 = new File(BASE_PATH + "/config");
    //        if (path1.exists()
    //                || path2.exists()
    //                || path3.exists()
    //                || path4.exists()
    //                || path5.exists()) {
    //            deleteRecursively(path1);
    //            deleteRecursively(path2);
    //            deleteRecursively(path3);
    //            deleteRecursively(path4);
    //            deleteRecursively(path5);
    //        }
    //
    //        CfgAion.setConfFilePath(BASE_PATH + "/config/mainnet/config.xml");
    //        CfgAion.setGenesisFilePath(BASE_PATH + "/config/mainnet/genesis.json");
    //        Keystore.setKeystorePath(BASE_PATH + "/keystore");
    //    }

    //    /** Ensures correct behavior for the <i>-c</i> and <i>--config</i> arguments. */
    //    @Test
    //    public void testConfig() {
    //        // compatibility with old kernels
    //        assertEquals(EXIT, cli.call(new String[] {"-c"}, CfgAion.inst()));
    //        assertEquals(EXIT, cli.call(new String[] {"--config"}, CfgAion.inst()));
    //
    //        // available networks
    //        for (Cli.Network net : Cli.Network.values()) {
    //            assertEquals(EXIT, cli.call(new String[] {"-c", net.toString()}, CfgAion.inst()));
    //            assertEquals(EXIT, cli.call(new String[] {"--config", net.toString()},
    // CfgAion.inst()));
    //        }
    //
    //        // accepted alias
    //        assertEquals(EXIT, cli.call(new String[] {"-c", "testnet"}, CfgAion.inst()));
    //        assertEquals(EXIT, cli.call(new String[] {"--config", "testnet"}, CfgAion.inst()));
    //
    //        // incorrect value
    //        assertEquals(ERROR, cli.call(new String[] {"-c", "random"}, CfgAion.inst()));
    //        assertEquals(ERROR, cli.call(new String[] {"--config", "random"}, CfgAion.inst()));
    //    }

    /** Tests the -a create arguments work. */
    @Test
    @Ignore
    public void testCreateAccount() {
        String args[] = {"-a", "create"};
        assertEquals(EXIT, cli.call(args, CfgAion.inst()));
    }

    /** Tests the -a list arguments work. */
    @Test
    @Ignore
    public void testListAccounts() {
        String args[] = {"-a", "list"};
        assertEquals(EXIT, cli.call(args, CfgAion.inst()));
    }

    /** Tests the -a export arguments work on a valid account. */
    @Test
    @Ignore
    public void testExportPrivateKey() {
        String account = Keystore.create("password");

        String[] args = {"-a", "export", account};
        assertEquals(EXIT, cli.call(args, CfgAion.inst()));
    }

    /**
     * Tests the -a export arguments fail when the suupplied account is a proper substring of a
     * valid account.
     */
    @Test
    @Ignore
    public void testExportSubstringOfAccount() {
        String account = Keystore.create("password");
        String substrAcc = account.substring(1);

        String[] args = {"-a", "export", substrAcc};
        assertEquals(ERROR, cli.call(args, CfgAion.inst()));
    }

    /** Tests the -a import arguments work on a fail import key. */
    @Test
    @Ignore
    public void testImportPrivateKey() {
        ECKey key = ECKeyFac.inst().create();

        String[] args = {"-a", "import", Hex.toHexString(key.getPrivKeyBytes())};
        assertEquals(EXIT, cli.call(args, CfgAion.inst()));
    }

    /** Tests the -a import arguments fail when a non-private key is supplied. */
    @Test
    @Ignore
    public void testImportNonPrivateKey() {
        String account = Keystore.create("password");

        String[] args = {"-a", "import", account};
        assertEquals(ERROR, cli.call(args, CfgAion.inst()));
    }

    /** Tests the -a import arguments work when a valid private key is supplied. */
    @Test
    @Ignore
    public void testImportPrivateKey2() {
        ECKey key = ECKeyFac.inst().create();
        System.out.println("Original address    : " + Hex.toHexString(key.getAddress()));
        System.out.println("Original public key : " + Hex.toHexString(key.getPubKey()));
        System.out.println("Original private key: " + Hex.toHexString(key.getPrivKeyBytes()));

        String[] args = {"-a", "import", Hex.toHexString(key.getPrivKeyBytes())};
        assertEquals(EXIT, cli.call(args, CfgAion.inst()));

        ECKey key2 = Keystore.getKey(Hex.toHexString(key.getAddress()), "password");
        System.out.println("Imported address    : " + Hex.toHexString(key2.getAddress()));
        System.out.println("Imported public key : " + Hex.toHexString(key2.getPubKey()));
        System.out.println("Imported private key: " + Hex.toHexString(key2.getPrivKeyBytes()));
    }

    /** Tests the -a import arguments fail given an invalid private key. */
    @Test
    @Ignore
    public void testImportPrivateKeyWrong() {
        String[] args = {"-a", "import", "hello"};
        assertEquals(ERROR, cli.call(args, CfgAion.inst()));
    }

    // Methods below taken from FileUtils class
    private static boolean copyRecursively(File src, File target) {
        if (src.isDirectory()) {
            return copyDirectoryContents(src, target);
        } else {
            try {
                Files.copy(src, target);
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private static boolean copyDirectoryContents(File src, File target) {
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

    private static boolean isSymbolicLink(File file) {
        try {
            File canonicalFile = file.getCanonicalFile();
            File absoluteFile = file.getAbsoluteFile();
            File parentFile = file.getParentFile();
            // a symbolic link has a different name between the canonical and absolute path
            return !canonicalFile.getName().equals(absoluteFile.getName())
                    ||
                    // or the canonical parent path is not the same as the file's parent path,
                    // provided the file has a parent path
                    parentFile != null
                            && !parentFile.getCanonicalPath().equals(canonicalFile.getParent());
        } catch (IOException e) {
            // error on the side of caution
            return true;
        }
    }

    private static ImmutableList<File> listFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(files);
    }

    private static boolean deleteRecursively(File file) {
        Path path = file.toPath();
        try {
            java.nio.file.Files.walkFileTree(
                    path,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(
                                final Path file, final BasicFileAttributes attrs)
                                throws IOException {
                            java.nio.file.Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(
                                final Path file, final IOException e) {
                            return handleException(e);
                        }

                        private FileVisitResult handleException(final IOException e) {
                            // e.printStackTrace();
                            return FileVisitResult.TERMINATE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(
                                final Path dir, final IOException e) throws IOException {
                            if (e != null) {
                                return handleException(e);
                            }
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
