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

import static java.lang.System.exit;
import static org.aion.crypto.ECKeyFac.ECKeyType.ED25519;
import static org.aion.crypto.HashUtil.H256Type.BLAKE2B_256;
import static org.aion.zero.impl.Version.KERNEL_VERSION;

import java.util.ServiceLoader;
import org.aion.api.server.http.NanoServer;
import org.aion.api.server.pb.ApiAion0;
import org.aion.api.server.pb.IHdlr;
import org.aion.api.server.zmq.HdlrZmq;
import org.aion.api.server.zmq.ProtocolProcessor;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.evtmgr.EventMgrModule;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.config.CfgApiRpc;
import org.aion.mcf.mine.IMineRunner;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.blockchain.IAionChain;
import org.aion.zero.impl.cli.Cli;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

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
        if (args != null && args.length > 0) {
            int ret = new Cli().call(args, cfg);
            exit(ret);
        }

        /*
         * if in the config.xml id is set as default [NODE-ID-PLACEHOLDER]
         * return true which means should save back to xml config
         */
        if (cfg.fromXML()) {
            cfg.toXML(new String[]{"--id=" + cfg.getId()});
        }

        try {
            ServiceLoader.load(AionLoggerFactory.class);
        } catch (Exception e) {
            System.out.println("load AionLoggerFactory service fail!" + e.toString());
            throw e;
        }

        /*
         * Ensuring valid UUID in the config.xml
         * Valid UUID: 32 Hex in 5 Groups [0-9A-F]
         * 00000000-0000-0000-0000-000000000000
         */
        String UUID = cfg.getId();
        if (!UUID.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            System.out.println("Invalid UUID; please check <id> setting in config.xml");
            exit(-1);
        }

        /* Outputs relevant logger configuration */
        if (!cfg.getLog().getLogFile()) {
            System.out
                .println("Logger disabled; to enable please check <log> settings in config.xml");
        } else if (!cfg.getLog().isValidPath() && cfg.getLog().getLogFile()) {
            System.out.println("Invalid file path; please check <log> setting in config.xml");
            return;
        } else if (cfg.getLog().isValidPath() && cfg.getLog().getLogFile()) {
            System.out.println("Logger file path: '" + cfg.getLog().getLogPath() + "'");
        }

        /*
         * Logger initialize with LOGFILE and LOGPATH (user config inputs)
         */
        AionLoggerFactory
            .init(cfg.getLog().getModules(), cfg.getLog().getLogFile(), cfg.getLog().getLogPath());
        Logger genLog = AionLoggerFactory.getLogger(LogEnum.GEN.name());

        String logo ="\n                     _____                  \n" +
                "      .'.       |  .~     ~.  |..          |\n" +
                "    .'   `.     | |         | |  ``..      |\n" +
                "  .''''''''`.   | |         | |      ``..  |\n" +
                ".'           `. |  `._____.'  |          ``|\n\n" +
                "                    NETWORK  v" + KERNEL_VERSION +
                "\n\n";

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

        NanoServer rpcServer = null;
        if (cfg.getApi().getRpc().getActive()) {
            CfgApiRpc rpcCfg = cfg.getApi().getRpc();
            rpcServer = new NanoServer(
                rpcCfg.getIp(),
                rpcCfg.getPort(),
                rpcCfg.getCorsEnabled(),
                rpcCfg.getEnabled(),
                rpcCfg.getMaxthread());
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
            private final NanoServer rpc;

            private ShutdownThreadHolder(Thread zmqThread, IMineRunner nm, ProtocolProcessor pp,
                NanoServer rpc) {
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
                holder.rpc.shutdown();
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
}
