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

import java.math.BigInteger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.aion.base.util.Hex;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;

import javax.xml.stream.*;

public class NodeMgr implements INodeMgr {

    private final static int TIMEOUT_ACTIVE_NODES = 30000;
    private final static int TIMEOUT_INBOUND_NODES = 10000;
    private static final String BASE_PATH = System.getProperty("user.dir");
    public static final String PEER_LIST_FILE_PATH = BASE_PATH + "/config/peers.xml";

    private final Set<String> seedIps = new HashSet<>();
    private final Map<Integer, Node> allNodes = new ConcurrentHashMap<>();
    private final BlockingQueue<Node> tempNodes = new LinkedBlockingQueue<>();
    private final Map<Integer, Node> outboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> inboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, Node> activeNodes = new ConcurrentHashMap<>();

    Map<Integer, Node> getOutboundNodes() {
        return outboundNodes;
    }

    public void dumpAllNodeInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("   ==================== ALL PEERS METRIC ===========================\n");
        List<Node> all = new ArrayList<>(allNodes.values());
        all.sort((a, b) -> (int) (b.getBestBlockNumber() - a.getBestBlockNumber()));
        int cnt = 0;
        for (Node n : all) {
            char isSeed = n.getIfFromBootList() ? 'Y' : 'N';
            sb.append(String.format(" %3d ID:%6s SEED:%c IP:%15s PORT:%5d PORT_CONN:%5d FC:%1d BB:%8d  \n", cnt,
                    n.getIdShort(), isSeed, n.getIpStr(), n.getPort(), n.getConnectedPort(),
                    n.peerMetric.metricFailedConn, n.getBestBlockNumber()));
            cnt++;
        }
        System.out.println(sb.toString());
    }

    /**
     *
     * @param selfShortId String
     */
    void dumpNodeInfo(String selfShortId) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(String.format("   ================== p2p-status-%6s ==================\n", selfShortId));
        sb.append(String.format("   temp[%d] inbound[%d] outbound[%d]\n",
            tempNodesSize(),
            inboundNodes.size(),
            outboundNodes.size()
        ));
        List<Node> sorted = new ArrayList<>(activeNodes.values());
        if(sorted.size() > 0){
            sb.append("   -------------------------------------------------------\n");
            sb.append("   seed       blk                                                           header               td      id              ip   port      type\n");
            sorted.sort((n1, n2) -> Arrays.compare(n2.getTotalDifficulty(), n1.getTotalDifficulty()));
            for (Node n : sorted) {
                sb.append(
                    String.format("      %c%10d %64s %16s  %6s %15s  %5d  %8s\n",
                        n.getIfFromBootList() ? 0x221A : ' ',
                        n.getBestBlockNumber(),
                        n.getBestBlockHash() == null ? "" : Hex.toHexString(n.getBestBlockHash()),
                        n.getTotalDifficulty() == null ? "0" : new BigInteger(1, n.getTotalDifficulty()).toString(10),
                        n.getIdShort(),
                        n.getIpStr(),
                        n.getPort(),
                        n.getType()
                    )
                );
            }
        }
        sb.append("\n");
        System.out.println(sb.toString());
    }

    private void updateMetric(final Node _n) {
        if (_n.hasFullInfo()) {
            int fullHash = _n.getFullHash();
            if (allNodes.containsKey(fullHash)) {
                Node orig = allNodes.get(fullHash);

                // pull out metric.
                _n.peerMetric = orig.peerMetric;
                _n.copyNodeStatus(orig);
            }
            allNodes.put(fullHash, _n);
        }
    }

    public void updateAllNodesInfo(INode _n) {
        Node n = (Node) _n;

        if (n.hasFullInfo()) {
            int fullHash = n.getFullHash();
            if (allNodes.containsKey(fullHash)) {
                Node orig = allNodes.get(fullHash);
                // pull out metric.
                orig.copyNodeStatus(n);
            }
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

    void inboundNodeAdd(final Node _n) {
        updateMetric(_n);
        inboundNodes.put(_n.getChannel().hashCode(), _n);
    }

    synchronized Node tempNodesTake() throws InterruptedException {
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

    Node allocNode(String ip, int p0, int p1) {
        return new Node(ip, p0, p1);
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

    public INode getRandomRealtime(long bbn) {

        List<Integer> keysArr = new ArrayList<>();

        for (Node n : activeNodes.values()) {
            if ((n.getBestBlockNumber() == 0) || (n.getBestBlockNumber() > bbn)) {
                keysArr.add(n.getIdHash());
            }
        }

        int nodesCount = keysArr.size();
        if (nodesCount > 0) {
            Random r = new Random(System.currentTimeMillis());

            try {
                int randomNodeKeyIndex = r.nextInt(keysArr.size());
                int randomNodeKey = keysArr.get(randomNodeKeyIndex);
                return this.getActiveNode(randomNodeKey);
            } catch (IllegalArgumentException e) {
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
            node.setFromBootList(seedIps.contains(node.getIpStr()));
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

    /**
     * @param _p2pMgr P2pMgr
     */
    void shutdown(final P2pMgr _p2pMgr) {
        try {
            activeNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel()));
            activeNodes.clear();
            outboundNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel()));
            outboundNodes.clear();
            inboundNodes.forEach((k, n) -> _p2pMgr.closeSocket(n.getChannel()));
            inboundNodes.clear();
        } catch (Exception e) {

        }
    }

    void persistNodes(){
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        output.setProperty("escapeCharacters", false);
        XMLStreamWriter sw = null;
        try {
            sw = output.createXMLStreamWriter(new FileWriter(PEER_LIST_FILE_PATH));
            sw.writeStartDocument("utf-8", "1.0");
            sw.writeCharacters("\r\n");
            sw.writeStartElement("aion-peers");

            for (Node node : allNodes.values()) {
                sw.writeCharacters(node.toXML());
            }

            sw.writeCharacters("\r\n");
            sw.writeEndElement();
            sw.flush();
            sw.close();

        } catch (Exception e) {
            System.out.println("<error on-write-peers-xml-to-file>");
        } finally {
            if (sw != null) {
                try {
                    sw.close();
                } catch (XMLStreamException e) {
                    System.out.println("<error on-close-stream-writer>");
                }
            }
        }
    }

    void loadPersistedNodes(){
        File peerFile = new File(PEER_LIST_FILE_PATH);
        XMLInputFactory input = XMLInputFactory.newInstance();
        FileInputStream file = null;
        try {
            file = new FileInputStream(peerFile);
            XMLStreamReader sr = input.createXMLStreamReader(file);
            loop: while (sr.hasNext()) {
                int eventType = sr.next();
                switch (eventType) {
                    case XMLStreamReader.START_ELEMENT:
                        String elementName = sr.getLocalName().toLowerCase();
                        switch (elementName) {
                            case "aion-peers":
                                loopNode:
                                while (sr.hasNext()) {
                                    int eventType1 = sr.next();
                                    switch (eventType1) {
                                        case XMLStreamReader.START_ELEMENT:
                                            Node node = Node.fromXML(sr);

                                            if(node == null)
                                                break;
                                            if(!node.peerMetric.shouldNotConn())
                                                tempNodes.add(node);
                                            allNodes.put(node.getFullHash(), node);
                                            break;
                                        case XMLStreamReader.END_ELEMENT:
                                            break loopNode;
                                    }
                                }
                                break;
                        }
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        if (sr.getLocalName().toLowerCase().equals("aion-peers"))
                            break loop;
                        else
                            break;
                }
            }
        } catch (Exception e) {
            System.out.println("<error on-parsing-peers-xml msg=" + e.getLocalizedMessage() + ">");
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (IOException e) {
                    System.out.println("<error on-close-file-input-stream>");
                }
            }
        }
    }

}
