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

package org.aion.p2p.impl.comm;

import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class NodeMgr implements INodeMgr {

    private final static int TIMEOUT_INBOUND_NODES = 10000;

    private final Set<String> seedIps = new HashSet<>();
    private final BlockingQueue<Node> tempNodes = new LinkedBlockingQueue<>();
    private final Map<Integer, Node> outboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> inboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> activeNodes = new ConcurrentHashMap<>();

    public Map<Integer, Node> getOutboundNodes() {
        return outboundNodes;
    }

    private final static char[] hexArray = "0123456789abcdef".toCharArray();

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
     *
     * @param selfShortId
     *            String
     */
    public String dumpNodeInfo(String selfShortId) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(String.format(
                "================================================================== p2p-status-%6s ==================================================================\n",
                selfShortId));
        sb.append(String.format(
                "temp[%3d] inbound[%3d] outbound[%3d] active[%3d]                            s - seed node, td - total difficulty, # - block number, bv - binary version\n",
                tempNodesSize(), inboundNodes.size(), outboundNodes.size(), activeNodes.size()));
        List<Node> sorted = new ArrayList<>(activeNodes.values());
        if (sorted.size() > 0) {
            sb.append("\n          s"); // id & seed
            sb.append("               td");
            sb.append("          #");
            sb.append("                                                             hash");
            sb.append("              ip");
            sb.append("  port");
            sb.append("     conn");
            sb.append("              bv\n");
            sb.append(
                    "-------------------------------------------------------------------------------------------------------------------------------------------------------\n");
            sorted.sort((n1, n2) -> {
                int tdCompare = n2.getTotalDifficulty().compareTo(n1.getTotalDifficulty());
                if (tdCompare == 0) {
                    Long n2Bn = n2.getBestBlockNumber();
                    Long n1Bn = n1.getBestBlockNumber();
                    return n2Bn.compareTo(n1Bn);
                } else
                    return tdCompare;
            });
            for (Node n : sorted) {
                try {
                    sb.append(String.format("id:%6s %c %16s %10d %64s %15s %5d %8s %15s\n", n.getIdShort(),
                            n.getIfFromBootList() ? 'y' : ' ', n.getTotalDifficulty().toString(10),
                            n.getBestBlockNumber(),
                            n.getBestBlockHash() == null ? "" : bytesToHex(n.getBestBlockHash()), n.getIpStr(),
                            n.getPort(), n.getConnection(), n.getBinaryVersion()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * @param _n
     *            Node
     */
    public void tempNodesAdd(final Node _n) {
        if (!tempNodes.contains(_n)) {
            tempNodes.add(_n);
        }
    }

    /**
     * @param _ip
     *            String
     */
    public void seedIpAdd(String _ip) {
        this.seedIps.add(_ip);
    }

    public boolean isSeedIp(String _ip) {
        return this.seedIps.contains(_ip);
    }

    public void inboundNodeAdd(final Node _n) {
        inboundNodes.put(_n.getChannel().hashCode(), _n);
    }

    public void addOutboundNode(final Node _n) {
        outboundNodes.put(_n.getIdHash(), _n);
    }

    public Node tempNodesTake() throws InterruptedException {
        return tempNodes.take();
    }

    public int tempNodesSize() {
        return tempNodes.size();
    }

    public int activeNodesSize() {
        return activeNodes.size();
    }

    public boolean hasActiveNode(int k) {
        return activeNodes.containsKey(k);
    }

    public Node getActiveNode(int k) {
        return activeNodes.get(k);
    }

    public Node getInboundNode(int k) {
        return inboundNodes.get(k);
    }

    public Node getOutboundNode(int k) {
        return outboundNodes.get(k);
    }

    public Node allocNode(String ip, int p0, int p1) {
        Node n = new Node(ip, p0, p1);
        if(seedIps.contains(ip))
            n.setFromBootList(true);
        return n;
    }

    public List<Node> getActiveNodesList() {
        return new ArrayList(activeNodes.values());
    }

    public Map<Integer, INode> getActiveNodesMap() {
        return new HashMap(activeNodes);
    }

    public INode getRandom() {
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
     * @param _nodeIdHash
     *            int
     * @param _shortId
     *            String
     * @param _p2pMgr
     *            P2pMgr
     */
    // Attention: move node from container need sync to avoid node not belong to
    // any container during transit.
    public synchronized void moveOutboundToActive(int _nodeIdHash, String _shortId, final IP2pMgr _p2pMgr) {
        Node node = outboundNodes.remove(_nodeIdHash);
        if (node != null) {
            node.setConnection("outbound");
            INode previous = activeNodes.put(_nodeIdHash, node);
            if (previous != null)
                _p2pMgr.closeSocket(node.getChannel(), "duplicated-outbound-vs-active");
            else {
                if (_p2pMgr.isShowLog())
                    System.out.println("<p2p action=move-outbound-to-active node-id=" + _shortId + ">");
            }
        }
    }

    /**
     * @param _channelHashCode
     *            int
     * @param _p2pMgr
     *            P2pMgr
     */
    // Attention: move node from container need sync to avoid node not belong to
    // any container during transit.
    public synchronized void moveInboundToActive(int _channelHashCode, final IP2pMgr _p2pMgr) {
        Node node = inboundNodes.remove(_channelHashCode);
        if (node != null) {
            node.setConnection("inbound");
            node.setFromBootList(seedIps.contains(node.getIpStr()));
            INode previous = activeNodes.put(node.getIdHash(), node);
            if (previous != null)
                _p2pMgr.closeSocket(node.getChannel(), "duplicated-inbound-vs-active");
            else {
                if (_p2pMgr.isShowLog())
                    System.out.println("<p2p action=move-inbound-to-active channel-id=" + _channelHashCode + ">");
            }
        }
    }

    public void rmTimeOutInbound(final IP2pMgr _p2pMgr) {
        Iterator inboundIt = inboundNodes.keySet().iterator();
        while (inboundIt.hasNext()) {
            int key = (int) inboundIt.next();
            Node node = inboundNodes.get(key);
            if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_INBOUND_NODES) {
                _p2pMgr.closeSocket(node.getChannel(), "inbound-timeout ip=" + node.getIpStr());
                inboundIt.remove();
            }
        }
    }

    public void rmTimeOutActives(IP2pMgr _p2pMgr) {
        long now = System.currentTimeMillis();

        OptionalDouble average = activeNodes.values().stream().mapToLong(n -> now - n.getTimestamp()).average();
        double timeout = average.orElse(4000) * 5;
        timeout = Math.max(10000, Math.min(timeout, 60000));
        if (_p2pMgr.isShowLog()) {
            System.out.printf("<p2p average-delay=%.0fms>\n", average.orElse(0));
        }

        Iterator activeIt = activeNodes.keySet().iterator();
        while (activeIt.hasNext()) {
            int key = (int) activeIt.next();
            Node node = getActiveNode(key);

            if (now - node.getTimestamp() > timeout || !node.getChannel().isConnected()) {

                _p2pMgr.closeSocket(node.getChannel(), "active-timeout node=" + node.getIdShort() + " ip=" + node.getIpStr());
                activeIt.remove();
            }
        }
    }

    public void dropActive(int nodeIdHash, final IP2pMgr _p2pMgr) {
        Node node = activeNodes.remove(nodeIdHash);
        if (node == null)
            return;
        _p2pMgr.closeSocket(node.getChannel(), "drop-active node=" + node.getIdShort() + " ip=" + node.getIpStr());
    }

    public void removeActive(int nodeIdHash, final IP2pMgr _p2pMgr) {
        dropActive(nodeIdHash, _p2pMgr);
    }

    /**
     * @param _p2pMgr
     *            P2pMgr
     */
    public void shutdown(final IP2pMgr _p2pMgr) {
        try {
            activeNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel(), "p2p-shutdown"));
            activeNodes.clear();
            outboundNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel(),"p2p-shutdown"));
            outboundNodes.clear();
            inboundNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel(),"p2p-shutdown"));
            inboundNodes.clear();
        } catch (Exception e) {

        }
    }

    public void ban(Integer nodeIdHash) {
        Node node = activeNodes.get(nodeIdHash);
        if (node != null) {
            node.peerMetric.ban();
        }
    }
}
