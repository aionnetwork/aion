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

package org.aion;

import java.io.Console;
import java.util.ServiceLoader;
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
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgApiRpc;
import org.aion.mcf.config.CfgSsl;
import org.aion.mcf.mine.IMineRunner;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.cli.Cli;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

import static java.lang.System.exit;
import static org.aion.crypto.ECKeyFac.ECKeyType.ED25519;
import static org.aion.crypto.HashUtil.H256Type.BLAKE2B_256;
import static org.aion.zero.impl.Version.KERNEL_VERSION;

public class Aion {

    public static void main(String args[]) {

        /*
         * @ATTENTION: ECKey have two layer: tx layer is KeyFac optional,
         *             network layer is hardcode to secp256.
         */
        ECKeyFac.setType(ED25519);
        HashUtil.setType(BLAKE2B_256);
        ServiceLoader.load(EventMgrModule.class);

        CfgAion cfg = CfgAion.inst();

        /*
         * if in the config.xml id is set as default [NODE-ID-PLACEHOLDER]
         * return true which means should save back to xml config
         */
        if (cfg.fromXML()) {
            if(args != null && args.length > 0 && !(args[0].equals("-v")||args[0].equals("--version"))) {
                cfg.toXML(new String[]{"--id=" + cfg.getId()});
            }
        }

        // Reads CLI (must be after the cfg.fromXML())
        if (args != null && args.length > 0) {
            int ret = new Cli().call(args, cfg);
            if (ret != 2) {
                exit(ret);
            }
        }

        // UUID check
        String UUID = cfg.getId();
        if (!UUID.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            System.out.println("Invalid UUID; please check <id> setting in config.xml");
            exit(-1);
        }

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

        /*
         * Logger initialize with LOGFILE and LOGPATH (user config inputs)
         */
        AionLoggerFactory
            .init(cfg.getLog().getModules(), cfg.getLog().getLogFile(), cfg.getLog().getLogPath());
        Logger genLog = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        String[] filePath = new String[7];
        // Log/Database path
        if (!cfg.getLog().getLogFile()) {
            System.out.println("Logger disabled; to enable please check log settings in config.xml");
        } else if (!cfg.getLog().isValidPath() && cfg.getLog().getLogFile()) {
            System.out.println("File path is invalid; please check log setting in config.xml");
            return;
        } else if (cfg.getLog().isValidPath() && cfg.getLog().getLogFile()) {
            filePath[0] = cfg.getBasePath() + "/" + cfg.getLog().getLogPath();
        }
        filePath[1] = cfg.getBasePath() + "/" + cfg.getDb().getPath();
        filePath[2] = Keystore.getKeystorePath();
        filePath[3] = new Cli().getDstConfig();
        filePath[4] = new Cli().getDstGenesis();
        filePath[5] = CfgAion.getConfFilePath();
        filePath[6] = CfgAion.getGenesisFilePath();

        String path =
                "\n-------------------------------- USED PATHS --------------------------------" +
                "\n> Logger path:   " + filePath[0] +
                "\n> Database path: " + filePath[1] +
                "\n> Keystore path: " + filePath[2] +
                "\n> Config write:  " + filePath[3] +
                "\n> Genesis write: " + filePath[4] +
                "\n----------------------------------------------------------------------------" +
                "\n> Config read:   " + filePath[5] +
                "\n> Genesis read:  " + filePath[6] +
                "\n----------------------------------------------------------------------------\n\n";

        String logo =
              "\n                     _____                  \n" +
                "      .'.       |  .~     ~.  |..          |\n" +
                "    .'   `.     | |         | |  ``..      |\n" +
                "  .''''''''`.   | |         | |      ``..  |\n" +
                ".'           `. |  `._____.'  |          ``|\n\n";

        // always print the version string in the center of the Aion logo
        String versionStr = "v"+KERNEL_VERSION;
        String networkStr = CfgAion.getNetwork();
        logo = appendLogo(logo, versionStr);
        logo = appendLogo(logo, networkStr);

        genLog.info(path);
        genLog.info(logo);

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
//                    "Failed to initialize JMX server.  In-flight configuration changes will not be available.",
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
        if(cfg.getApi().getRpc().getActive()) {
            CfgApiRpc rpcCfg =  cfg.getApi().getRpc();

            Consumer<RpcServerBuilder<? extends RpcServerBuilder<?>>> commonRpcConfig = (rpcBuilder) -> {
                rpcBuilder.setUrl(rpcCfg.getIp(), rpcCfg.getPort());
                rpcBuilder.setWorkerPoolSize(rpcCfg.getMaxthread());
                rpcBuilder.enableEndpoints(rpcCfg.getEnabled());
                rpcBuilder.enableMethods(rpcCfg.getEnabledMethods());
                rpcBuilder.disableMethods(rpcCfg.getDisabledMethods());

                if (rpcCfg.getCorsEnabled())
                    rpcBuilder.enableCorsWithOrigin(rpcCfg.getCorsOrigin());

                CfgSsl cfgSsl = rpcCfg.getSsl();
                if (cfgSsl.getEnabled())
                    rpcBuilder.enableSsl(cfgSsl.getCert(), sslPass);
            };
            RpcServerVendor rpcVendor = RpcServerVendor.fromString(rpcCfg.getVendor()).orElse(RpcServerVendor.UNDERTOW);
            try {
                switch (rpcVendor) {
                    case NANO: {
                        NanoRpcServer.Builder rpcBuilder = new NanoRpcServer.Builder();
                        commonRpcConfig.accept(rpcBuilder);
                        rpcServer = rpcBuilder.build();
                        break;
                    }
                    case UNDERTOW:
                    default: {
                        UndertowRpcServer.Builder rpcBuilder = new UndertowRpcServer.Builder();
                        commonRpcConfig.accept(rpcBuilder);
                        rpcServer = rpcBuilder.build();
                        break;
                    }
                }
            } catch (Exception e) {
                genLog.error("Failed to instantiate RPC server.", e);
            }

            if (rpcServer == null)
                throw new IllegalStateException("Issue with RPC settings caused server instantiation to fail. " +
                        "Please check RPC settings in config file.");

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

            private ShutdownThreadHolder(Thread zmqThread, IMineRunner nm, ProtocolProcessor pp, RpcServer rpc) {
                this.zmqThread = zmqThread;
                this.miner = nm;
                this.pp = pp;
                this.rpc = rpc;
            }
        }

        ShutdownThreadHolder holder = new ShutdownThreadHolder(zmqThread, nm, processor, rpcServer);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

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
                    genLog.info("Shutdown zmq ProtocolProcessor failed! {}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }

            if (holder.zmqThread != null) {
                genLog.info("Shutting down zmq thread");
                try {
                    holder.zmqThread.interrupt();
                    genLog.info("Shutdown zmq thread... Done!");
                } catch (Exception e) {
                    genLog.info("Shutdown zmq thread failed! {}", e.getMessage());
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

            genLog.info("---------------------------------------------");
            genLog.info("| Aion kernel graceful shutdown successful! |");
            genLog.info("---------------------------------------------");

        }, "shutdown"));

    }

    public static String appendLogo(String value, String input) {
        int leftPad = Math.round((44 - input.length()) / 2.0f) + 1;
        StringBuilder padInput = new StringBuilder();
        for (int i = 0; i < leftPad; i++) padInput.append(" ");
        padInput.append(input);
        value += padInput.toString();
        value += "\n\n";
        return value;
    }

    private static char[] getSslPassword(CfgAion cfg) {
        CfgSsl sslCfg = cfg.getApi().getRpc().getSsl();
        char[] sslPass = sslCfg.getPass();
        // interactively ask for a password for the ssl file if they did not set on in the config file
        if (sslCfg.getEnabled() && sslPass == null) {
            Console console = System.console();
            // https://docs.oracle.com/javase/10/docs/api/java/io/Console.html
            // if the console does not exist, then either:
            // 1) jvm's underlying platform does not provide console
            // 2) process started in non-interactive mode (background scheduler, redirected output, etc.)
            // don't wan't to compromise security in these scenarios
            if (console == null) {
                System.out.println("SSL-certificate-use requested with RPC server and no console found. " +
                        "Please set the ssl password in the config file (insecure) to run kernel non-interactively with this option.");
                exit(1);
            } else {
                console.printf("---------------------------------------------\n");
                console.printf("----------- INTERACTION REQUIRED ------------\n");
                console.printf("---------------------------------------------\n");
                sslPass = console.readPassword("Password for SSL keystore file ["
                        +sslCfg.getCert()+"]\n");
            }
        }

        return sslPass;
    }
}