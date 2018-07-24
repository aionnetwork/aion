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
package org.aion.p2p.impl.comm;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.slf4j.Logger;

public class NodeMgr implements INodeMgr {

    private final static int TIMEOUT_INBOUND_NODES = 10000;

    private static final int TIMEOUT_OUTBOUND_NODES = 20000;
    private static final Random random = new SecureRandom();
    private final static char[] hexArray = "0123456789abcdef".toCharArray();
    private static Logger p2pLOG;
    private final int maxActiveNodes;
    private final int maxTempNodes;
    private final Set<String> seedIps = new HashSet<>();
    private final IP2pMgr p2pMgr;
    private final ReentrantLock takeLock = new ReentrantLock();
    private final ReentrantLock putLock = new ReentrantLock();
    private final Condition notEmpty = takeLock.newCondition();
    //private final BlockingQueue<INode> tempNodes = new LinkedBlockingQueue<>();
    private final Map<Integer, INode> tempNodes = Collections
        .synchronizedMap(new LinkedHashMap<>());
    private final Map<Integer, INode> outboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, INode> inboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, INode> activeNodes = new ConcurrentHashMap<>();

    public NodeMgr(IP2pMgr _p2pMgr, int _maxActiveNodes, int _maxTempNodes, Logger _logger) {
        this.maxActiveNodes = _maxActiveNodes;
        this.maxTempNodes = _maxTempNodes;
        this.p2pMgr = _p2pMgr;
        p2pLOG = _logger;
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * @param selfShortId String
     */
    @Override
    public String dumpNodeInfo(String selfShortId, boolean completeInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(String.format(
            "======================================================================== p2p-status-%6s =========================================================================\n",
            selfShortId));
        sb.append(String.format(
            "temp[%3d] inbound[%3d] outbound[%3d] active[%3d]                                         s - seed node, td - total difficulty, # - block number, bv - binary version\n",
            tempNodesSize(), inboundNodes.size(), outboundNodes.size(), activeNodes.size()));

        sb.append(appendColumnFormat());
        List<INode> sorted = new ArrayList<>(activeNodes.values());
        if (sorted.size() > 0) {
            sorted.sort((n1, n2) -> {
                int tdCompare = n2.getTotalDifficulty().compareTo(n1.getTotalDifficulty());
                if (tdCompare == 0) {
                    Long n2Bn = n2.getBestBlockNumber();
                    Long n1Bn = n1.getBestBlockNumber();
                    return n2Bn.compareTo(n1Bn);
                } else {
                    return tdCompare;
                }
            });

            for (INode n : sorted) {
                try {
                    if (!completeInfo && !n.getIfFromBootList()) {
                        continue;
                    }
                    sb.append(appendNodeInfo(n));
                } catch (Exception ex) {
                    p2pLOG.error("NodeMgr dumpNodeInfo exception.", ex);
                }
            }
        }
        return sb.toString();
    }

    private static String appendColumnFormat() {
        return "\n          s"
            + "               td"
            + "          #"
            + "                                                             hash"
            + "              ip"
            + "  port"
            + "     conn"
            + "              bv"
            + "           ci\n"
            + "--------------------------------------------------------------------------------------------------------------------------------------------------------------------\n";
    }

    private String appendNodeInfo(INode n) {
        return String.format("id:%6s %c %16s %10d %64s %15s %5d %8s %15s %12s\n",
            n.getIdShort(),
            n.getIfFromBootList() ? 'y' : ' ', n.getTotalDifficulty().toString(10),
            n.getBestBlockNumber(),
            n.getBestBlockHash() == null ? "" : bytesToHex(n.getBestBlockHash()),
            n.getIpStr(),
            n.getPort(),
            n.getConnection(),
            n.getBinaryVersion(),
            n.getChannel().hashCode());
    }

    /**
     * @param _ip String
     */
    @Override
    public void seedIpAdd(String _ip) {
        this.seedIps.add(_ip);
    }

    @Override
    public boolean isSeedIp(String _ip) {
        return this.seedIps.contains(_ip);
    }

    /**
     * @param _n Node
     */
    @Override
    public void addTempNode(final INode _n) {
        final ReentrantLock putLock = this.putLock;
        try {
            putLock.lockInterruptibly();

            if (tempNodes.size() < maxTempNodes && !tempNodes
                .containsKey(_n.getPeerId()) && (notActiveNode(_n.getIdHash()) || _n
                .getIfFromBootList())) {
                tempNodes.putIfAbsent(_n.getPeerId(), _n);
            }
        } catch (InterruptedException e) {
            p2pLOG.error("addTempNode exception!", e);
        } finally {
            putLock.unlock();
        }
    }

    @Override
    public void addInboundNode(final INode _n) {
        if (p2pLOG.isTraceEnabled()) {
            p2pLOG.trace("addInboundNode {}", _n.toString());
        }
        inboundNodes.put(_n.getChannel().hashCode(), _n);
    }

    @Override
    public void addOutboundNode(final INode _n) {
        if (p2pLOG.isTraceEnabled()) {
            p2pLOG.trace("addOutboundNode {}", _n.toString());
        }
        outboundNodes.put(_n.getIdHash(), _n);
    }

    @Override
    public INode tempNodesTake() {
        INode node = null;
        final ReentrantLock takeLock = this.takeLock;
        try {
            takeLock.lockInterruptibly();
            while (tempNodes.isEmpty()) {
                notEmpty.await();
            }

            Integer key = tempNodes.keySet().iterator().next();
            node = tempNodes.remove(key);
            if (!tempNodes.isEmpty()) {
                notEmpty.signal();
            }
        } catch (InterruptedException e) {
            p2pLOG.error("tempNodesTake IllegalStateException", e);
        } finally {
            takeLock.unlock();
        }

        return node;
    }

    @Override
    public int tempNodesSize() {
        return tempNodes.size();
    }

    @Override
    public int activeNodesSize() {
        return activeNodes.size();
    }

    @Override
    public boolean notActiveNode(int k) {
        return !activeNodes.containsKey(k);
    }

    @Override
    public INode getActiveNode(int k) {
        return activeNodes.get(k);
    }

    @Override
    public INode getInboundNode(int k) {
        return inboundNodes.get(k);
    }

    @Override
    public INode getOutboundNode(int k) {
        return outboundNodes.get(k);
    }

    @Override
    public INode allocNode(String ip, int p0) {
        INode n = new Node(ip, p0);
        if (seedIps.contains(ip)) {
            n.setFromBootList(true);
        }
        return n;
    }

    @Override
    public List<INode> getActiveNodesList() {
        return new ArrayList<>(activeNodes.values());
    }

    @Override
    public HashMap getActiveNodesMap() {
        return new HashMap<>(activeNodes);
    }

    @Override
    public void timeoutCheck() {
        timeoutInbound();
        timeoutOutBound();
        timeoutActive();
    }

    @Override
    public boolean notAtOutboundList(int _nodeIdHash) {
        return !this.outboundNodes.containsKey(_nodeIdHash);
    }

    @Override
    public INode getNodefromOutBoundList(int _nodeIdHash) {
        return this.outboundNodes.get(_nodeIdHash);
    }

    private void timeoutOutBound() {
        try {
            Iterator<Map.Entry<Integer, INode>> it = outboundNodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, INode> entry = it.next();
                if (System.currentTimeMillis() - entry.getValue().getTimestamp()
                    > TIMEOUT_OUTBOUND_NODES) {
                    p2pMgr.closeSocket(entry.getValue().getChannel(),
                        "outbound-timeout ip=" + entry.getValue().getIpStr());
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            p2pLOG.error("timeoutOutbound IllegalStateException", e);

        }
    }

    @Override
    public INode getRandom() {
        if (!activeNodes.isEmpty()) {
            Object[] keysArr = activeNodes.keySet().toArray();
            try {
                return this.getActiveNode((Integer) keysArr[random.nextInt(keysArr.length)]);
            } catch (IllegalArgumentException e) {
                p2pLOG.error("getRandom-IllegalArgumentException", e);
                return null;
            } catch (NullPointerException e) {
                p2pLOG.error("<getRandom-NullPointerException", e);
                return null;
            } catch (ClassCastException e) {
                p2pLOG.error("<getRandom-ClassCastException", e);
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * @param _ip String
     * @return boolean
     * @warning not thread safe helper function to check a specific ip a node associated with is is
     * allowed to add to active list
     */
    private boolean activeIpAllow(String _ip) {
        return true;
        // enable this in case
//        if(multiActiveAllowIps.contains(_ip))
//            return true;
//        else {
//            Set<String> ips = activeNodes.values().stream()
//                    .map((n)-> n.getIpStr())
//                    .collect(Collectors.toSet());
//            return !ips.contains(_ip);
//        }
    }

    public void movePeerToActive(int _hash, String _type) {
        Map<Integer, INode> nodes = (_type.contentEquals("inbound")) ? inboundNodes : outboundNodes;
        INode node = nodes.remove(_hash);
        if (node != null) {
            if (p2pLOG.isTraceEnabled()) {
                p2pLOG.trace("movePeerToActive: {} {}", _type, node.toString());
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (node) {
                if (activeNodes.size() >= maxActiveNodes) {
                    p2pMgr.closeSocket(node.getChannel(), _type + " -> active, active full");
                    return;
                }

                if (node.getIdHash() == p2pMgr.getSelfIdHash()) {
                    p2pMgr.closeSocket(node.getChannel(), _type + " -> active, self-connected");
                    return;
                }

                node.setConnection(_type);
                node.setFromBootList(seedIps.contains(node.getIpStr()));
                INode previous = activeNodes.putIfAbsent(node.getIdHash(), node);
                if (previous != null) {
                    p2pMgr.closeSocket(node.getChannel(),
                        _type + " -> active, node " + previous.getIdShort() + " exits");
                } else if (!activeIpAllow(node.getIpStr())) {
                    p2pMgr.closeSocket(node.getChannel(),
                        _type + " -> active, ip " + node.getIpStr() + " exits");
                }

                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug(_type + " -> active node-id={} ip={}", node.getIdShort(),
                        node.getIpStr());
                }
            }
        } else {
            if (p2pLOG.isTraceEnabled()) {
                p2pLOG.trace("movePeerToActive empty {} {}", _type, _hash);
            }
        }
    }

    void timeoutInbound() {
        try {
            Iterator<Map.Entry<Integer, INode>> it = inboundNodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, INode> entry = it.next();
                if (System.currentTimeMillis() - entry.getValue().getTimestamp()
                    > TIMEOUT_INBOUND_NODES) {
                    p2pMgr.closeSocket(entry.getValue().getChannel(),
                        "inbound-timeout ip=" + entry.getValue().getIpStr());
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            p2pLOG.info("timeoutInbound IllegalStateException ", e);
        }
    }

    private void timeoutActive() {

        long now = System.currentTimeMillis();
        OptionalDouble average = activeNodes.values().stream()
            .mapToLong(n -> now - n.getTimestamp()).average();

        long timeout = ((long) average.orElse(4000)) * 5;
        timeout = Math.max(10000, Math.min(timeout, 60000));
        if (p2pLOG.isDebugEnabled()) {
            p2pLOG.debug("average-delay={}ms", (long) average.orElse(0));
        }

        try {
            Iterator<Map.Entry<Integer, INode>> it = activeNodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, INode> entry = it.next();
                INode node = entry.getValue();
                if (now - node.getTimestamp() > timeout) {
                    p2pMgr.closeSocket(node.getChannel(),
                        "active-timeout ip=" + node.getIpStr());
                    it.remove();
                } else if (!node.getChannel().isConnected()) {
                    p2pMgr.closeSocket(node.getChannel(),
                        "channel-already-closed node=" + node.getIdShort() + " ip=" + node
                            .getIpStr());
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            p2pLOG.info("timeoutActive IllegalStateException ", e);
        }
    }

    public void dropActive(int nodeIdHash, String _reason) {

        if (p2pLOG.isDebugEnabled()) {
            p2pLOG.debug("dropActive idHash:{} reason:{}", nodeIdHash, _reason);
        }

        INode node = null;
        try {
            node = activeNodes.remove(nodeIdHash);
        } catch (Exception e) {
            p2pLOG.info("dropActive exception ", e);
        }

        if (node == null) {
            return;
        }
        p2pMgr.closeSocket(node.getChannel(), _reason);
    }

    @Override
    public void shutdown() {
        try {

            synchronized (outboundNodes) {
                outboundNodes.forEach((k, n) -> p2pMgr.closeSocket(n.getChannel(),
                    "p2p-shutdown outbound node=" + n.getIdShort() + " ip=" + n.getIpStr()));
                outboundNodes.clear();
            }

            synchronized (inboundNodes) {
                inboundNodes.forEach((k, n) -> p2pMgr
                    .closeSocket(n.getChannel(), "p2p-shutdown inbound ip=" + n.getIpStr()));
                inboundNodes.clear();
            }

            synchronized (activeNodes) {
                activeNodes.forEach((k, n) -> p2pMgr.closeSocket(n.getChannel(),
                    "p2p-shutdown active node=" + n.getIdShort() + " ip=" + n.getIpStr()));
                activeNodes.clear();
            }

        } catch (Exception e) {
            p2pLOG.info("p2p-shutdown exception ", e);
        }
    }

    @Override
    public void ban(int _nodeIdHash) {
        try {
            INode node = activeNodes.get(_nodeIdHash);
            if (node != null) {
                node.getPeerMetric().ban();
            }
        } catch (NullPointerException e) {
            p2pLOG.info("p2p-ban null exception ", e);
        }
    }
}
