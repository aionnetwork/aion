/*******************************************************************************
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
 * Contributors to the aion source files in decreasing order of code volume:
 * 
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion;

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

import java.util.ServiceLoader;

import static org.aion.crypto.ECKeyFac.ECKeyType.ED25519;
import static org.aion.crypto.HashUtil.H256Type.BLAKE2B_256;
import static org.aion.zero.impl.Version.KERNEL_VERSION;

public class Aion {

    public static void main(String args[]) throws InterruptedException {

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
            System.exit(ret);
        }

        /*
         * if in the config.xml id is set as default [NODE-ID-PLACEHOLDER]
         * return true which means should save back to xml config
         */
        if(cfg.fromXML())
            cfg.toXML(new String[]{ "--id=" + cfg.getId() });

        
        try {
            ServiceLoader.load(AionLoggerFactory.class);
        } catch (Exception e) {
            System.out.println("load AionLoggerFactory service fail!" + e.toString());
            throw e;
        }


        // If commit this out, the config setting will be ignore. all log module been set to "INFO" Level
        AionLoggerFactory.init(cfg.getLog().getModules());
        Logger LOG = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

        System.out.println(                
                        "                     _____                  \n" +
                        "      .'.       |  .~     ~.  |..          |\n" +
                        "    .'   `.     | |         | |  ``..      |\n" +
                        "  .''''''''`.   | |         | |      ``..  |\n" +
                        ".'           `. |  `._____.'  |          ``|\n\n" +
                        "                    NETWORK  v" + KERNEL_VERSION +
                                "\n\n"
                );

        IAionChain ac = AionFactory.create();
                
        IMineRunner nm = ac.getBlockMiner();

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
            ProtocolProcessor finalProcessor = processor;
            zmqThread = new Thread(() -> {
                finalProcessor.run();
            }, "zmq-api");
            zmqThread.start();
        }

        NanoServer rpcServer = null;
        if(cfg.getApi().getRpc().getActive()) {
            CfgApiRpc rpcCfg =  cfg.getApi().getRpc();
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
            final Thread zmqThread;
            final IMineRunner miner;
            final ProtocolProcessor pp;
            final NanoServer rpc;
            
            private ShutdownThreadHolder(Thread zmqThread, IMineRunner nm, ProtocolProcessor pp, NanoServer rpc) {
                this.zmqThread = zmqThread;
                this.miner = nm;
                this.pp = pp;
                this.rpc = rpc;
            }
        }

        ShutdownThreadHolder holder = new ShutdownThreadHolder(zmqThread, nm, processor, rpcServer);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            LOG.info("Starting shutdown process...");

            if (holder.rpc != null) {
                LOG.info("Shutting down RpcServer");
                holder.rpc.shutdown();
                LOG.info("Shutdown RpcServer ... Done!");
            }

            if (holder.pp != null) {
                LOG.info("Shutting down zmq ProtocolProcessor");
                try {
                    holder.pp.shutdown();
                    LOG.info("Shutdown zmq ProtocolProcessor... Done!");
                } catch (InterruptedException e) {
                    LOG.info("Shutdown zmq ProtocolProcessor failed! {}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }

            if (holder.miner != null) {
                LOG.info("Shutting down sealer");
                holder.miner.stopMining();
                holder.miner.shutdown();
                LOG.info("Shutdown sealer... Done!");
            }

            LOG.info("Shutting down the AionHub...");
            ac.getAionHub().close();

            LOG.info("---------------------------------------------");
            LOG.info("| Aion kernel graceful shutdown successful! |");
            LOG.info("---------------------------------------------");

        }, "shutdown"));
    }
}
