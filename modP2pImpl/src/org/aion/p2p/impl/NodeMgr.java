/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 * This file is part of the aion network project.
 *
 * The aion network project is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * The aion network project is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the aion network project source files.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *
 * Aion foundation.
 *
 */

package org.aion.p2p.impl;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.aion.p2p.INode;

public class NodeMgr {

    private final static int TIMEOUT_ACTIVE_NODES = 30000;
    private final static int TIMEOUT_INBOUND_NODES = 10000;

    final Set<String> seedIps = new HashSet<>();
    private final Map<Integer, Node> allNodes = new ConcurrentHashMap<>();
    private final BlockingQueue<Node> tempNodes = new LinkedBlockingQueue<>();
    private final Map<Integer, Node> outboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> inboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> activeNodes = new ConcurrentHashMap<>();

    Map<Integer, Node> getOutboundNodes() {
        return outboundNodes;
    }

    public void dumpAllNodeInfo() {
        StringBuffer sb = new StringBuffer();
        sb.append("   ==================== ALL PEERS METRIC ===========================\n");

        int cnt = 0;
        for (Node n : allNodes.values()) {
            char isSeed = n.getIfFromBootList() ? 'Y' : 'N';
            sb.append(String.format(" %3d ID:%6s SEED:%c IP:%15s PORT:%5d FC:%1d BB:%8d  \n", cnt, n.getIdShort(),
                    isSeed, n.getIpStr(), n.getPort(), n.peerMetric.metricFailedConn, n.getBestBlockNumber()));
            cnt++;
        }
        System.out.println(sb.toString());
    }

    /**
     *
     * @param selfShortId String
     */
    void dumpNodeInfo(String selfShortId) {
        System.out.println("[p2p-status " + selfShortId + "]");
        System.out.println("[temp-nodes-size=" + tempNodesSize() + "]");
        System.out.println("[inbound-nodes-size=" + inboundNodes.size() + "]");
        System.out.println("[outbound-nodes-size=" + outboundNodes.size() + "]");

        List<Node> sorted = new ArrayList<>(activeNodes.values());
        sorted.sort((n1, n2) -> Long.compare(n2.getBestBlockNumber(), n1.getBestBlockNumber()));

        System.out.println("[active-nodes(nodeIdHash)=[" + sorted.stream()
                .map((entry) -> "\n" + entry.getBestBlockNumber() + "-" + entry.getIdShort() + "-"
                        + entry.getIpStr() + "-" + entry.getType() + (entry.getIfFromBootList() ? "-seed" : ""))
                .collect(Collectors.joining(",")) + "]]");
    }

    private void updateMetric(final Node _n) {
        if (_n.hasFullInfo()) {
            int fullHash = _n.getFullHash();
            if (allNodes.containsKey(fullHash)) {
                Node orig = allNodes.get(fullHash);
                _n.peerMetric = orig.peerMetric;
            }
            allNodes.put(fullHash, _n);
        }
    }

    /**
     * @param _n Node
     */
    void tempNodesAdd(final Node _n) {
        if (!tempNodes.contains(_n)) {
            updateMetric(_n);
            tempNodes.add(_n);
        }
    }

    /**
     * @param _ip String
     */
    void seedIpAdd(String _ip){
        this.seedIps.add(_ip);
    }

    void inboundNodeAdd(Node n) {
        inboundNodes.put(n.getChannel().hashCode(), n);
        updateMetric(n);
    }

    Node tempNodesTake() throws InterruptedException {
        return tempNodes.take();
    }

    int tempNodesSize() {
        return tempNodes.size();
    }

    /**
     * for test
     */
    void clearTempNodes(){
        this.tempNodes.clear();
    }

    int activeNodesSize() {
        return activeNodes.size();
    }

    int outboundNodesSize() {
        return outboundNodes.size();
    }

    int inboundNodesSize() {
        return inboundNodes.size();
    }

    boolean hasActiveNode(int k) {
        return activeNodes.containsKey(k);
    }

    Node getActiveNode(int k) {
        return activeNodes.get(k);
    }

    Node getInboundNode(int k) {
        return inboundNodes.get(k);
    }

    Map<Integer, Node> getNodes() {
        return allNodes;
    }

    Node allocNode(boolean b, String ip) {
        return new Node(b, ip);
    }

    Node allocNode(String ip, int port) {
        return new Node(ip, port);
    }

    List<Node> getActiveNodesList() {
        return new ArrayList(activeNodes.values());
    }

    Map<Integer, INode> getActiveNodesMap() {
        return new HashMap(activeNodes);
    }

    INode getRandom() {
        int nodesCount = activeNodes.size();
        if (nodesCount > 0) {
            Random r = new Random(System.currentTimeMillis());
            List<Integer> keysArr = new ArrayList<>(activeNodes.keySet());
            try {
                int randomNodeKeyIndex = r.nextInt(keysArr.size());
                int randomNodeKey = keysArr.get(randomNodeKeyIndex);
                return this.getActiveNode(randomNodeKey);
            } catch (IllegalArgumentException e) {
                System.out.println("<p2p get-random-exception>");
                return null;
            }
        } else
            return null;
    }

    /**
     * @param _nodeIdHash int
     * @param _shortId String
     * @param _p2pMgr P2pMgr
     */
    void moveOutboundToActive(int _nodeIdHash, String _shortId, final P2pMgr _p2pMgr) {
        Node node = outboundNodes.remove(_nodeIdHash);
        if (node != null) {
            node.setType("outbound");
            INode previous = activeNodes.putIfAbsent(_nodeIdHash, node);
            if (previous != null)
                _p2pMgr.closeSocket(node.getChannel());
            else {
                if (_p2pMgr.showLog)
                    System.out.println("<p2p action=move-outbound-to-active node-id=" + _shortId + ">");
            }
        }
    }

    /**
     * @param _channelHashCode int
     * @param _p2pMgr P2pMgr
     */
    void moveInboundToActive(int _channelHashCode, final P2pMgr _p2pMgr) {
        Node node = inboundNodes.remove(_channelHashCode);
        if (node != null) {
            node.setType("inbound");
            if(seedIps.contains(node.getIpStr()))
                node.setFromBootList(true);
            INode previous = activeNodes.putIfAbsent(node.getIdHash(), node);
            if (previous != null)
                _p2pMgr.closeSocket(node.getChannel());
            else {
                if (_p2pMgr.showLog)
                    System.out.println("<p2p action=move-inbound-to-active channel-id=" + _channelHashCode + ">");
            }
        }
    }

    void rmMetricFailedNodes() {
        {
            Iterator nodesIt = tempNodes.iterator();
            while (nodesIt.hasNext()) {
                Node n = (Node) nodesIt.next();
                if (n.peerMetric.shouldNotConn())
                    tempNodes.remove(n);
            }
        }
    }

    void rmTimeOutInbound(P2pMgr pmgr) {
        {
            Iterator inboundIt = inboundNodes.keySet().iterator();
            while (inboundIt.hasNext()) {
                int key = (int) inboundIt.next();
                Node node = inboundNodes.get(key);
                if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_INBOUND_NODES) {

                    pmgr.closeSocket(node.getChannel());

                    inboundIt.remove();

                    if (pmgr.showLog)
                        System.out.println("<p2p-clear inbound-timeout>");
                }
            }
        }
    }

    void rmTimeOutActives(P2pMgr pmgr) {
        Iterator activeIt = activeNodes.keySet().iterator();
        while (activeIt.hasNext()) {
            int key = (int) activeIt.next();
            Node node = getActiveNode(key);
            if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_ACTIVE_NODES) {

                pmgr.closeSocket(node.getChannel());
                activeIt.remove();
                if (pmgr.showLog)
                    System.out.println("<p2p-clear active-timeout>");
            }
        }
    }

    void shutdown(P2pMgr pmgr) {
        try {
            activeNodes.forEach((k, n) -> pmgr.closeSocket(n.getChannel()));
            activeNodes.clear();
            outboundNodes.forEach((k, n) -> pmgr.closeSocket(n.getChannel()));
            outboundNodes.clear();
            inboundNodes.forEach((k, n) -> pmgr.closeSocket(n.getChannel()));
            inboundNodes.clear();
        } catch (Exception e) {

        }
    }

}
