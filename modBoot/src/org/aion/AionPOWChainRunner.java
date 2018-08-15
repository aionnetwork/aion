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
 *     Centrys Inc. <https://centrys.io>
 */
package org.aion;

import org.aion.api.server.http.RpcServer;
import org.aion.api.server.http.RpcServerBuilder;
import org.aion.api.server.http.RpcServerVendor;
import org.aion.api.server.http.nano.NanoRpcServer;
import org.aion.api.server.http.undertow.UndertowRpcServer;
import org.aion.api.server.pb.ApiAion0;
import org.aion.api.server.pb.IHdlr;
import org.aion.api.server.zmq.HdlrZmq;
import org.aion.api.server.zmq.ProtocolProcessor;
import org.aion.mcf.config.CfgApiRpc;
import org.aion.mcf.config.CfgSsl;
import org.aion.mcf.mine.IMineRunner;
import org.aion.zero.impl.blockchain.AionFactory;
import org.aion.zero.impl.blockchain.IChainInstancePOW;
import org.aion.zero.impl.config.CfgAion;
import org.slf4j.Logger;

import java.util.function.Consumer;

public class AionPOWChainRunner {

    public static void start(CfgAion cfg, final char[] sslPass, Logger genLog) {
        IChainInstancePOW ac = AionFactory.create();

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

}
