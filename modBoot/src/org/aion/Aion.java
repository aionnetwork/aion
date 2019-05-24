package org.aion;

import static java.lang.System.exit;
import static org.aion.crypto.ECKeyFac.ECKeyType.ED25519;
import static org.aion.crypto.HashUtil.H256Type.BLAKE2B_256;
import static org.aion.zero.impl.Version.KERNEL_VERSION;
import static org.aion.zero.impl.cli.Cli.ReturnType;

import java.io.Console;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;
import org.aion.api.server.http.RpcServer;
import org.aion.api.server.http.RpcServerBuilder;
import org.aion.api.server.http.RpcServerVendor;
import org.aion.api.server.http.nano.NanoRpcServer;
import org.aion.api.server.http.undertow.UndertowRpcServer;
import org.aion.api.server.pb.ApiAion0;
import org.aion.api.server.pb.IHdlr;
import org.aion.api.server.zmq.HdlrZmq;
import org.aion.api.server.zmq.ProtocolProcessor;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.evtmgr.EventMgrModule;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.config.CfgApiRpc;
import org.aion.mcf.config.CfgApiZmq;
import org.aion.mcf.config.CfgSsl;
import org.aion.mcf.mine.IMineRunner;
import org.aion.solidity.Compiler;
import org.aion.utils.NativeLibrary;
import org.aion.vm.LongLivedAvm;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.cli.Cli;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.config.Network;
import org.slf4j.Logger;
import org.zeromq.ZMQ;

public class Aion {

    public static void main(String args[]) {

        // TODO: should we load native libraries first thing?
        NativeLibrary.checkNativeLibrariesLoaded();

        try {
            Compiler.getInstance().compileHelloAion();
        } catch (IOException e) {
            System.out.println("compiler load failed!");
            throw new ExceptionInInitializerError();
        }

        /*
         * @ATTENTION: ECKey have two layer: tx layer is KeyFac optional,
         *             network layer is hardcode to secp256.
         */
        ECKeyFac.setType(ED25519);
        HashUtil.setType(BLAKE2B_256);

        CfgAion cfg = CfgAion.inst();

        ReturnType ret = new Cli().call(args, cfg);
        if (ret != ReturnType.RUN) {
            exit(ret.getValue());
        }

        Properties p = cfg.getFork().getProperties();
        p.forEach(
                (k, v) -> {
                    System.out.println(
                            "<Protocol name: "
                                    + k.toString()
                                    + " block#: "
                                    + v.toString()
                                    + " updated!");
                });

        // Check ZMQ server secure connect settings, generate keypair when the settings enabled and
        // can't find the keypair.
        if (cfg.getApi().getZmq().getActive()
                && cfg.getApi().getZmq().isSecureConnectEnabledEnabled()) {
            try {
                checkZmqKeyPair();
            } catch (Exception e) {
                System.out.println("Check zmq keypair fail! " + e.toString());
                exit(1);
            }
        }

        // UUID check
        String UUID = cfg.getId();
        if (!UUID.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            System.out.println("Invalid UUID; please check <id> setting in config.xml");
            exit(1);
        }

        ServiceLoader.load(EventMgrModule.class);

        try {
            ServiceLoader.load(AionLoggerFactory.class);
        } catch (Exception e) {
            System.out.println("load AionLoggerFactory service fail!" + e.toString());
            throw e;
        }

        // get the ssl password synchronously from the console, only if required
        // do this here, before writes to logger because if we don't do this here, then
        // it gets presented to console out of order with the rest of the logging ...
        final char[] sslPass = getSslPassword(cfg);

        // from now on, all logging to console and file happens asynchronously

        String[] filePath = new String[9];
        // Log/Database path
        if (!cfg.getLog().getLogFile()) {
            System.out.println(
                    "Logger disabled; to enable please update log settings in config.xml and restart kernel.");
            filePath[0] = "« disabled »";
        } else {
            filePath[0] = cfg.getLogPath();
        }

        // Logger initialize with LOGFILE and LOGPATH (user config inputs)
        AionLoggerFactory.init(
                cfg.getLog().getModules(), cfg.getLog().getLogFile(), cfg.getLogPath());
        Logger genLog = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        filePath[1] = cfg.getDatabasePath();
        filePath[2] = Keystore.getKeystorePath();
        filePath[3] = cfg.getExecConfigFile().getAbsolutePath();
        filePath[4] = cfg.getExecGenesisFile().getAbsolutePath();
        filePath[5] = cfg.getExecForkFile().getAbsolutePath();
        filePath[6] = cfg.getInitialConfigFile().getAbsolutePath();
        filePath[7] = cfg.getInitialGenesisFile().getAbsolutePath();
        filePath[8] = cfg.getInitialForkFile().getAbsolutePath();

        String path =
                "\n-------------------------------- USED PATHS --------------------------------"
                        + "\n> Logger path:   "
                        + filePath[0]
                        + "\n> Database path: "
                        + filePath[1]
                        + "\n> Keystore path: "
                        + filePath[2]
                        + "\n> Config write:  "
                        + filePath[3]
                        + "\n> Genesis write: "
                        + filePath[4]
                        + "\n> Fork write:    "
                        + filePath[5]
                        + "\n----------------------------------------------------------------------------"
                        + "\n> Config read:   "
                        + filePath[6]
                        + "\n> Genesis read:  "
                        + filePath[7]
                        + "\n> Fork read:     "
                        + filePath[8]
                        + "\n----------------------------------------------------------------------------\n\n";

        String logo =
                "\n                     _____                  \n"
                        + "      .'.       |  .~     ~.  |..          |\n"
                        + "    .'   `.     | |         | |  ``..      |\n"
                        + "  .''''''''`.   | |         | |      ``..  |\n"
                        + ".'           `. |  `._____.'  |          ``|\n\n";

        // always print the version string in the center of the Aion logo
        String versionStr = "v" + KERNEL_VERSION;
        String networkStr = cfg.getNetwork();
        // if using old kernel configuration
        if (networkStr == null && cfg.getNet().getId() >= 0) {
            networkStr = Network.determineNetwork(cfg.getNet().getId()).toString();
        }

        logo = appendLogo(logo, versionStr);
        if (networkStr != null) {
            logo = appendLogo(logo, networkStr);
        }

        // show enabled VMs
        logo = appendLogo(logo, "using FVM & AVM");

        genLog.info(path);
        genLog.info(logo);

        LongLivedAvm.createAndStartLongLivedAvm();
        IAionChain ac = AionFactory.create();

        IMineRunner nm = null;

        if (!cfg.getConsensus().isSeed()) {
            nm = ac.getBlockMiner();
        }

        if (nm != null) {
            nm.delayedStartMining(10);
        }

        /*
         * Create JMX server and register in-flight config receiver MBean.  Commenting out for now
         * because not using it yet.
         */
        //        InFlightConfigReceiver inFlightConfigReceiver = new InFlightConfigReceiver(
        //                cfg, new DynamicConfigKeyRegistry());
        //        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        //        ObjectName objectName = null;
        //        try {
        //            objectName = new ObjectName(InFlightConfigReceiver.DEFAULT_JMX_OBJECT_NAME);
        //            server.registerMBean(inFlightConfigReceiver, objectName);
        //        } catch (MalformedObjectNameException
        //                | NotCompliantMBeanException
        //                | InstanceAlreadyExistsException
        //                | MBeanRegistrationException ex) {
        //            genLog.error(
        //                    "Failed to initialize JMX server.  In-flight configuration changes
        // will not be available.",
        //                    ex);
        //        }

        /*
         * Start Threads.
         */
        Thread zmqThread = null;
        ProtocolProcessor processor = null;
        if (cfg.getApi().getZmq().getActive()) {
            IHdlr handler = new HdlrZmq(new ApiAion0(ac));
            processor = new ProtocolProcessor(handler, cfg.getApi().getZmq());
            zmqThread = new Thread(processor, "zmq-api");
            zmqThread.start();
        }

        RpcServer rpcServer = null;
        if (cfg.getApi().getRpc().isActive()) {
            CfgApiRpc rpcCfg = cfg.getApi().getRpc();

            Consumer<RpcServerBuilder<? extends RpcServerBuilder<?>>> commonRpcConfig =
                    (rpcBuilder) -> {
                        rpcBuilder.setUrl(rpcCfg.getIp(), rpcCfg.getPort());
                        rpcBuilder.enableEndpoints(rpcCfg.getEnabled());
                        rpcBuilder.enableMethods(rpcCfg.getEnabledMethods());
                        rpcBuilder.disableMethods(rpcCfg.getDisabledMethods());

                        rpcBuilder.setWorkerPoolSize(rpcCfg.getWorkerThreads());
                        rpcBuilder.setIoPoolSize(rpcCfg.getIoThreads());
                        rpcBuilder.setRequestQueueSize(rpcCfg.getRequestQueueSize());
                        rpcBuilder.setStuckThreadDetectorEnabled(
                                rpcCfg.isStuckThreadDetectorEnabled());

                        if (rpcCfg.isCorsEnabled()) {
                            rpcBuilder.enableCorsWithOrigin(rpcCfg.getCorsOrigin());
                        }

                        CfgSsl cfgSsl = rpcCfg.getSsl();
                        if (cfgSsl.getEnabled()) {
                            rpcBuilder.enableSsl(cfgSsl.getCert(), sslPass);
                        }
                    };
            RpcServerVendor rpcVendor =
                    RpcServerVendor.fromString(rpcCfg.getVendor()).orElse(RpcServerVendor.UNDERTOW);
            try {
                switch (rpcVendor) {
                    case NANO:
                        {
                            NanoRpcServer.Builder rpcBuilder = new NanoRpcServer.Builder();
                            commonRpcConfig.accept(rpcBuilder);
                            rpcServer = rpcBuilder.build();
                            break;
                        }
                    case UNDERTOW:
                    default:
                        {
                            UndertowRpcServer.Builder rpcBuilder = new UndertowRpcServer.Builder();
                            commonRpcConfig.accept(rpcBuilder);
                            rpcServer = rpcBuilder.build();
                            break;
                        }
                }
            } catch (Exception e) {
                genLog.error("Failed to instantiate RPC server.", e);
            }

            if (rpcServer == null) {
                throw new IllegalStateException(
                        "Issue with RPC settings caused server instantiation to fail. "
                                + "Please check RPC settings in config file.");
            }

            rpcServer.start();
        }

        /*
         * This is a hack, but used to let us pass zmqThread into thread
         * Shutdown hook for Ctrl+C
         */
        class ShutdownThreadHolder {

            private final Thread zmqThread;
            private final IMineRunner miner;
            private final ProtocolProcessor pp;
            private final RpcServer rpc;

            private ShutdownThreadHolder(
                    Thread zmqThread, IMineRunner nm, ProtocolProcessor pp, RpcServer rpc) {
                this.zmqThread = zmqThread;
                this.miner = nm;
                this.pp = pp;
                this.rpc = rpc;
            }
        }

        ShutdownThreadHolder holder = new ShutdownThreadHolder(zmqThread, nm, processor, rpcServer);

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    genLog.info("Starting shutdown process...");

                                    if (holder.rpc != null) {
                                        genLog.info("Shutting down RpcServer");
                                        holder.rpc.stop();
                                        genLog.info("Shutdown RpcServer ... Done!");
                                    }

                                    if (holder.pp != null) {
                                        genLog.info("Shutting down zmq ProtocolProcessor");
                                        try {
                                            holder.pp.shutdown();
                                            genLog.info("Shutdown zmq ProtocolProcessor... Done!");
                                        } catch (InterruptedException e) {
                                            genLog.info(
                                                    "Shutdown zmq ProtocolProcessor failed! {}",
                                                    e.getMessage());
                                            Thread.currentThread().interrupt();
                                        }
                                    }

                                    if (holder.zmqThread != null) {
                                        genLog.info("Shutting down zmq thread");
                                        try {
                                            holder.zmqThread.interrupt();
                                            genLog.info("Shutdown zmq thread... Done!");
                                        } catch (Exception e) {
                                            genLog.info(
                                                    "Shutdown zmq thread failed! {}",
                                                    e.getMessage());
                                            Thread.currentThread().interrupt();
                                        }
                                    }

                                    if (holder.miner != null) {
                                        genLog.info("Shutting down sealer");
                                        holder.miner.stopMining();
                                        holder.miner.shutdown();
                                        genLog.info("Shutdown sealer... Done!");
                                    }

                                    genLog.info("Shutting down the AionHub...");
                                    ac.getAionHub().close();

                                    genLog.info("Shutting down the virtual machines...");
                                    LongLivedAvm.destroy();

                                    genLog.info("---------------------------------------------");
                                    genLog.info("| Aion kernel graceful shutdown successful! |");
                                    genLog.info("---------------------------------------------");
                                },
                                "shutdown"));
    }

    private static void checkZmqKeyPair() throws IOException {
        File zmqkeyDir =
                new File(System.getProperty("user.dir") + File.separator + CfgApiZmq.ZMQ_KEY_DIR);

        if (!zmqkeyDir.isDirectory()) {
            if (!zmqkeyDir.mkdir()) {
                System.out.println(
                        "zmq keystore directory could not be created. "
                                + "Please check user permissions or create directory manually.");
                System.exit(1);
            }
            System.out.println();
        }

        if (!existZmqSecKeyFile(zmqkeyDir.toPath())) {
            System.out.print("Can't find zmq key pair, generate new pair! \n");
            ZMQ.Curve.KeyPair kp = ZMQ.Curve.generateKeyPair();
            genKeyFile(zmqkeyDir.getPath(), kp.publicKey, kp.secretKey);
        } else {
            System.out.print("Find zmq key pair! \n");
        }
    }

    private static boolean existZmqSecKeyFile(final Path path) {
        List<File> files = org.aion.util.file.File.getFiles(path);

        for (File file : files) {
            if (file.getName().contains("zmqCurveSeckey")) {
                return true;
            }
        }

        return false;
    }

    private static void genKeyFile(
            final String path, final String publicKey, final String secretKey) throws IOException {
        DateFormat df = new SimpleDateFormat("yy-MM-dd'T'HH-mm-ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String iso_date = df.format(new Date(System.currentTimeMillis()));

        String fileName = "UTC--" + iso_date + "--zmqCurvePubkey";
        writeKeyToFile(path, fileName, publicKey);

        fileName = "UTC--" + iso_date + "--zmqCurveSeckey";
        writeKeyToFile(path, fileName, secretKey);
    }

    private static void writeKeyToFile(final String path, final String fileName, final String key)
            throws IOException {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-----");
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);

        Path p = Paths.get(path).resolve(fileName);
        Path keyFile;
        if (!java.nio.file.Files.exists(p)) {
            keyFile = java.nio.file.Files.createFile(p, attr);
        } else {
            keyFile = p;
        }

        FileOutputStream fos = new FileOutputStream(keyFile.toString());
        fos.write(key.getBytes());
        fos.close();
    }

    private static String appendLogo(String value, String input) {
        int leftPad = Math.round((44 - input.length()) / 2.0f) + 1;
        StringBuilder padInput = new StringBuilder();
        for (int i = 0; i < leftPad; i++) {
            padInput.append(" ");
        }
        padInput.append(input);
        value += padInput.toString();
        value += "\n\n";
        return value;
    }

    private static char[] getSslPassword(CfgAion cfg) {
        CfgSsl sslCfg = cfg.getApi().getRpc().getSsl();
        char[] sslPass = sslCfg.getPass();
        // interactively ask for a password for the ssl file if they did not set on in the config
        // file
        if (sslCfg.getEnabled() && sslPass == null) {
            Console console = System.console();
            // https://docs.oracle.com/javase/10/docs/api/java/io/Console.html
            // if the console does not exist, then either:
            // 1) jvm's underlying platform does not provide console
            // 2) process started in non-interactive mode (background scheduler, redirected output,
            // etc.)
            // don't wan't to compromise security in these scenarios
            if (console == null) {
                System.out.println(
                        "SSL-certificate-use requested with RPC server and no console found. "
                                + "Please set the ssl password in the config file (insecure) to run kernel non-interactively with this option.");
                exit(1);
            } else {
                console.printf("---------------------------------------------\n");
                console.printf("----------- INTERACTION REQUIRED ------------\n");
                console.printf("---------------------------------------------\n");
                sslPass =
                        console.readPassword(
                                "Password for SSL keystore file [" + sslCfg.getCert() + "]\n");
            }
        }

        return sslPass;
    }
}
