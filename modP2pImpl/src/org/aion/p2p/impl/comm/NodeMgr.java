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
import org.aion.p2p.impl1.P2pMgr;
import org.aion.p2p.impl1.TaskWrite;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class NodeMgr {

    private final static int TIMEOUT_INBOUND_NODES = 10000;
    private final static int TIMEOUT_OUTBOUND_NODES = 20000;
    private final boolean showLog;

    private final Set<String> seedIps = new HashSet<>();
    private final BlockingQueue<Node> tempNodes = new LinkedBlockingQueue<>();
    private final Map<Integer, Node> outboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> inboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> activeNodes = new ConcurrentHashMap<>();

    public NodeMgr(boolean _showLog) {
        showLog = _showLog;
    }

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
     * @param selfShortId String
     */
    public String showStatus(String selfShortId) {
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
                    sb.append(String.format("id:%6s %c %16s %10d %64s %15s %5d %8s %15s\n",
                            n.getIdShort(),
                            n.getIfFromBootList() ? 'y' : ' ', n.getTotalDifficulty().toString(10),
                            n.getBestBlockNumber(),
                            n.getBestBlockHash() == null ? "" : bytesToHex(n.getBestBlockHash()), n.getIpStr(),
                            n.getPort(),
                            n.getInboundOrOutbound(),
                            n.getBinaryVersion()));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        sb.append("\n");
        return sb.toString();
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

    /**
     * @param _nodeIdHash int
     * @return Node
     */
    public Node getOutboundNode(int _nodeIdHash) {
        return outboundNodes.get(_nodeIdHash);
    }

    public List<INode> getActiveNodesList() {
        return new ArrayList<>(activeNodes.values());
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

    public void addSeedIp(String _seedIp){
        this.seedIps.add(_seedIp);
    }

    /**
     * @param _n Node
     */
    public void addTempNode(final Node _n) {
        if (!tempNodes.contains(_n))
            tempNodes.add(_n);
    }

    public boolean isSeedIp(String _ip){
        return seedIps.contains(_ip);
    }

    public void addInboundNode(final Node _node) {
        inboundNodes.put(_node.getChannel().hashCode(), _node);
    }

    /**
     * @param _node Node
     * @return boolean indicates if same node (same node id) exists
     */
    public boolean addOutboundNode(final Node _node) {
        return outboundNodes.put(_node.getIdHash(), _node) == null;
    }

    /**
     * @param _nodeIdHash int
     * @param _shortId    String
     * @param _p2pMgr     P2pMgr
     */
    public void moveOutboundToActive(int _nodeIdHash, String _shortId, final P2pMgr _p2pMgr) {
        Node node = outboundNodes.remove(_nodeIdHash);
        if (node != null) {
            INode previous = activeNodes.put(_nodeIdHash, node);

            // close new connected if exists on active list already
            if (previous != null)
                _p2pMgr.closeSocket(node.getChannel(), "outbound-node-exits-on-active");
            else if (showLog)
                System.out.println("<p2p move-outbound-to-active node-id=" + _shortId + ">");
        }
    }

    /**
     * @param _channelHashCode int
     * @param _p2pMgr          P2pMgr
     */
    public void moveInboundToActive(int _channelHashCode, String _shortId, final P2pMgr _p2pMgr) {
        Node node = inboundNodes.remove(_channelHashCode);
        if (node != null) {
            INode previous = activeNodes.put(node.getIdHash(), node);

            // close new connected if exists on active list already
            if (previous != null)
                _p2pMgr.closeSocket(node.getChannel(), "inbound-node-exits-on-active id=" + node.getIdShort() + " ip=" + node.getIpStr());
            else if (showLog)
                    System.out.println("<p2p move-inbound-to-active node-id=" + _shortId + ">");
        }
    }

    public void timeoutOutbound(final P2pMgr _p2pMgr){
        Iterator outboundIt = outboundNodes.entrySet().iterator();
        long now = System.currentTimeMillis();
        while (outboundIt.hasNext()) {
            Map.Entry<Integer, Node> entry = (Map.Entry<Integer, Node>)outboundIt.next();
            Node node = entry.getValue();
            if ((now - node.getTimestamp()) > TIMEOUT_OUTBOUND_NODES) {
                outboundIt.remove();
                _p2pMgr.closeSocket(node.getChannel(), "timeout-outbound-" + node.getIdShort() + "-" + node.getIpStr());
            }

        }
    }

    public void timeoutInbound(final P2pMgr _p2pMgr) {
        Iterator inboundIt = inboundNodes.keySet().iterator();
        while (inboundIt.hasNext()) {
            int key = (int) inboundIt.next();
            Node node = inboundNodes.get(key);
            if (System.currentTimeMillis() - node.getTimestamp() > TIMEOUT_INBOUND_NODES) {
                inboundIt.remove();
                _p2pMgr.closeSocket(node.getChannel(), "timeout-inbound");
                if (this.showLog)
                    System.out.println("<p2p timeout-inbound node=" + node.getIdShort() + " ip=" + node.getIpStr() + ">");
            }
        }
    }

    public void timeoutActive(final P2pMgr _p2pMgr) {
        long now = System.currentTimeMillis();

        OptionalDouble average = activeNodes.values().stream().mapToLong(n -> now - n.getTimestamp()).average();
        double timeout = average.orElse(4000) * 5;
        timeout = Math.max(60000, timeout);
        if (showLog)
            System.out.printf("<p2p average-delay=%.0fms>\n", average.orElse(0));

        Iterator activeIt = activeNodes.keySet().iterator();
        while (activeIt.hasNext()) {
            int key = (int) activeIt.next();
            Node node = getActiveNode(key);
            if (now - node.getTimestamp() > timeout) {
                activeIt.remove();
                _p2pMgr.closeSocket(node.getChannel(), "timeout-active");
                if (showLog)
                    System.out.println("<p2p timeout-active node=" + node.getIdShort() + " ip=" + node.getIpStr() + ">");
            }
        }
    }

    public void dropActive(int nodeIdHash, final P2pMgr _p2pMgr) {
        Node node = activeNodes.remove(nodeIdHash);
        if (node != null)
            _p2pMgr.closeSocket(node.getChannel(), "drop-active");
    }

    public void tryDropActiveByChannelId(final SocketChannel _sc, final P2pMgr _p2pMgr) {

        int channelId = _sc.hashCode();
        try {
            _sc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Iterator it = activeNodes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Node> entry = (Map.Entry) it.next();
            Node node = entry.getValue();
            if (node.getChannel().hashCode() == channelId) {
                it.remove();
                return;
            }
        }
    }

    /**
     * @param _p2pMgr P2pMgr
     */
    public void shutdown(final P2pMgr _p2pMgr) {
        try {

            activeNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel(), "node-mgr-shutdown"));
            activeNodes.clear();
            outboundNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel(), "node-mgr-shutdown"));
            outboundNodes.clear();
            inboundNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel(),"node-mgr-shutdown"));
            inboundNodes.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void ban(Integer nodeIdHash) {
        Node node = activeNodes.get(nodeIdHash);
//        if (node != null) {
//            node.nodeStats.ban();
//        }
    }
}
