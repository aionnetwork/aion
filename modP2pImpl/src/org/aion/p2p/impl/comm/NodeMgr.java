package org.aion.p2p.impl.comm;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingDeque;
import org.aion.p2p.INode;
import org.aion.p2p.INodeMgr;
import org.aion.p2p.IP2pMgr;
import org.slf4j.Logger;

public class NodeMgr implements INodeMgr {

    // node timeout constants
    static final int TIMEOUT_INBOUND_NODES = 10_000;
    static final int TIMEOUT_OUTBOUND_NODES = 20_000;
    static final int MIN_TIMEOUT_ACTIVE_NODES = 10_000;
    static final int MAX_TIMEOUT_ACTIVE_NODES = 60_000;

    private static final Random random = new SecureRandom();
    private static final char[] hexArray = "0123456789abcdef".toCharArray();
    private static Logger p2pLOG;
    private final int maxActiveNodes;
    private final int maxTempNodes;
    private final Set<String> seedIps = new HashSet<>();
    private final IP2pMgr p2pMgr;
    private final Deque<INode> tempNodes;
    private final Set<Integer> tempNodesKeys = new ConcurrentSkipListSet<>();
    private final Map<Integer, INode> outboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, INode> inboundNodes = new ConcurrentHashMap<>();
    private final Map<Integer, INode> activeNodes = new ConcurrentHashMap<>();
    private int avgLatency = 0;
    private final INode myNode;

    public NodeMgr(IP2pMgr _p2pMgr, int _maxActiveNodes, int _maxTempNodes, Logger _logger, INode myNode) {
        this.maxActiveNodes = _maxActiveNodes;
        this.maxTempNodes = _maxTempNodes;
        this.p2pMgr = _p2pMgr;
        p2pLOG = _logger;
        // this data structure is preferable to ConcurrentLinkedDeque because
        // 1. we only really need to access the data one thread at a time
        // 2. it allows bounding the collection size
        tempNodes = new LinkedBlockingDeque<>(maxTempNodes);

        this.myNode = myNode;
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

    /** @param selfShortId String */
    @Override
    public String dumpNodeInfo(String selfShortId, boolean completeInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(
                String.format(
                        "=========================================================================== p2p-status-%6s =============================================================================\n",
                        selfShortId));
        sb.append(
                String.format(
                        " temp[%3d] inbound[%3d] outbound[%3d] active[%3d]                           id - node short id, s - seed node, td - total difficulty, # - block number, bv - binary version\n",
                        tempNodesSize(),
                        inboundNodes.size(),
                        outboundNodes.size(),
                        activeNodes.size()));

        sb.append(appendColumnFormat());

        if (myNode != null) {
            sb.append(appendNodeInfo(myNode)).append("\n");
        }

        List<INode> sorted = new ArrayList<>(activeNodes.values());
        if (sorted.size() > 0) {
            sorted.sort(
                    (n1, n2) -> {
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
                    p2pLOG.error("<NodeMgr dumpNodeInfo exception>", ex);
                }
            }
        }
        return sb.toString();
    }

    private static String appendColumnFormat() {
        return "\n"
                + String.format(" %6s", "id")
                + String.format(" %c", 's')
                + String.format(" %39s", "td")
                + String.format(" %8s", "#")
                + String.format(" %64s", "hash")
                + String.format(" %15s", "ip")
                + String.format(" %5s", "port")
                + String.format(" %8s", "conn")
                + String.format(" %16s", "bv")
                + "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n";
    }

    private String appendNodeInfo(INode n) {
        return String.format(
                " %6s %c %39s %8d %64s %15s %5d %8s %16s\n",
                n.getIdShort(),
                n.getIfFromBootList() ? 'y' : ' ',
                n.getTotalDifficulty().toString(10),
                n.getBestBlockNumber(),
                n.getBestBlockHash() == null ? "" : bytesToHex(n.getBestBlockHash()),
                n.getIpStr(),
                n.getPort(),
                n.getConnection(),
                n.getBinaryVersion());
    }

    /** @param _ip String */
    @Override
    public void seedIpAdd(String _ip) {
        this.seedIps.add(_ip);
    }

    @Override
    public boolean isSeedIp(String _ip) {
        return this.seedIps.contains(_ip);
    }

    @Override
    public void addTempNode(final INode _n) {
        if (_n == null) return;

        int key = _n.getIdHash();
        // The bootlist node will be added back into the tempNodes incase all the connections dropped.
        if (!tempNodesKeys.contains(key) && (notActiveNode(key) || _n.getIfFromBootList())) {
            if (tempNodes.offerLast(_n)) {
                tempNodesKeys.add(key);
            }
        }
    }

    @Override
    public void addInboundNode(final INode _n) {
        if (p2pLOG.isTraceEnabled()) {
            p2pLOG.trace("<addInboundNode {}>", _n.toString());
        }
        INode node = inboundNodes.put(_n.getChannel().hashCode(), _n);
        if (node != null) {
            p2pLOG.error("The inbound node={} was overwritten by node={}.", node, _n);
        }
    }

    @Override
    public void addOutboundNode(final INode _n) {
        if (p2pLOG.isTraceEnabled()) {
            p2pLOG.trace("<addOutboundNode {}>", _n.toString());
        }
        outboundNodes.put(_n.getIdHash(), _n);
    }

    @Override
    public INode tempNodesTake() {
        INode node = tempNodes.pollFirst();
        if (node != null) {
            tempNodesKeys.remove(node.getIdHash());
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
    public Map<Integer, INode> getActiveNodesMap() {
        return new HashMap<>(activeNodes);
    }

    @Override
    public int getAvgLatency() {
        return this.avgLatency;
    }

    @Override
    public void timeoutCheck(long currentTimeMillis) {
        timeoutInbound(currentTimeMillis);
        timeoutOutbound(currentTimeMillis);
        timeoutActive(currentTimeMillis);
    }

    @Override
    public boolean notAtOutboundList(int _nodeIdHash) {
        return !this.outboundNodes.containsKey(_nodeIdHash);
    }

    private void timeoutOutbound(long currentTimeMillis) {
        try {
            Iterator<Map.Entry<Integer, INode>> it = outboundNodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, INode> entry = it.next();
                if (currentTimeMillis - entry.getValue().getTimestamp() > TIMEOUT_OUTBOUND_NODES) {
                    p2pMgr.closeSocket(
                            entry.getValue().getChannel(),
                            "outbound-timeout ip=" + entry.getValue().getIpStr());
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            p2pLOG.error("<timeoutOutbound IllegalStateException>", e);
        }
    }

    @Override
    public INode getRandom() {
        if (!activeNodes.isEmpty()) {
            Object[] keysArr = activeNodes.keySet().toArray();
            try {
                return this.getActiveNode((Integer) keysArr[random.nextInt(keysArr.length)]);
            } catch (IllegalArgumentException e) {
                p2pLOG.error("<getRandom-IllegalArgumentException>", e);
                return null;
            } catch (NullPointerException e) {
                p2pLOG.error("<getRandom-NullPointerException>", e);
                return null;
            } catch (ClassCastException e) {
                p2pLOG.error("<getRandom-ClassCastException>", e);
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
     *     allowed to add to active list
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
                p2pLOG.trace("<movePeerToActive: {} {}>", _type, node.toString());
            }

            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (node) {
                if (activeNodes.size() >= maxActiveNodes) {
                    p2pMgr.closeSocket(node.getChannel(), _type + " -> active, active full");
                    return;
                }

                if (p2pMgr.isSelf(node)) {
                    p2pMgr.closeSocket(node.getChannel(), _type + " -> active, self-connected");
                    return;
                }

                node.setConnection(_type);
                node.setFromBootList(seedIps.contains(node.getIpStr()));
                INode previous = activeNodes.putIfAbsent(node.getIdHash(), node);
                if (previous != null) {
                    p2pMgr.closeSocket(
                            node.getChannel(),
                            _type + " -> active, node " + previous.getIdShort() + " exits");
                } else if (!activeIpAllow(node.getIpStr())) {
                    p2pMgr.closeSocket(
                            node.getChannel(),
                            _type + " -> active, ip " + node.getIpStr() + " exits");
                }

                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug(
                            "<{} -> active node-id={} ip={}>",
                            _type,
                            node.getIdShort(),
                            node.getIpStr());
                }
            }
        } else {
            if (p2pLOG.isTraceEnabled()) {
                p2pLOG.trace("<movePeerToActive empty {} {}>", _type, _hash);
            }
        }
    }

    @Override
    public void updateChainInfo(long blockNumber, byte[] blockHash, BigInteger blockTD) {
        // We only need the block information
        myNode.updateStatus(blockNumber, blockHash, blockTD, (byte)0, (short) 0, 0, 0);
    }

    private void timeoutInbound(long currentTimeMillis) {
        try {
            Iterator<Map.Entry<Integer, INode>> it = inboundNodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, INode> entry = it.next();
                if (currentTimeMillis - entry.getValue().getTimestamp() > TIMEOUT_INBOUND_NODES) {
                    p2pMgr.closeSocket(
                            entry.getValue().getChannel(),
                            "inbound-timeout ip=" + entry.getValue().getIpStr());
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            p2pLOG.info("<timeoutInbound IllegalStateException>", e);
        }
    }

    private void timeoutActive(long now) {
        OptionalDouble average =
                activeNodes.values().stream().mapToLong(n -> now - n.getTimestamp()).average();
        this.avgLatency = (int) average.orElse(0);
        long timeout = ((long) average.orElse(4000)) * 5;
        timeout = Math.max(MIN_TIMEOUT_ACTIVE_NODES, Math.min(timeout, MAX_TIMEOUT_ACTIVE_NODES));
        if (p2pLOG.isDebugEnabled()) {
            p2pLOG.debug("<average-delay={}ms>", this.avgLatency);
        }

        try {
            Iterator<Map.Entry<Integer, INode>> it = activeNodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, INode> entry = it.next();
                INode node = entry.getValue();
                if (now - node.getTimestamp() > timeout) {
                    p2pMgr.closeSocket(node.getChannel(), "active-timeout ip=" + node.getIpStr());
                    it.remove();
                } else if (!node.getChannel().isConnected()) {
                    p2pMgr.closeSocket(
                            node.getChannel(),
                            "channel-already-closed node="
                                    + node.getIdShort()
                                    + " ip="
                                    + node.getIpStr());
                    it.remove();
                }
            }
        } catch (IllegalStateException e) {
            p2pLOG.info("<timeoutActive IllegalStateException>", e);
        }
    }

    public void dropActive(int nodeIdHash, String _reason) {

        INode node = null;
        try {
            node = activeNodes.remove(nodeIdHash);
        } catch (Exception e) {
            p2pLOG.info("<dropActive exception>", e);
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
                outboundNodes.forEach(
                        (k, n) ->
                                p2pMgr.closeSocket(
                                        n.getChannel(),
                                        "p2p-shutdown outbound node="
                                                + n.getIdShort()
                                                + " ip="
                                                + n.getIpStr()));
                outboundNodes.clear();
            }

            synchronized (inboundNodes) {
                inboundNodes.forEach(
                        (k, n) ->
                                p2pMgr.closeSocket(
                                        n.getChannel(), "p2p-shutdown inbound ip=" + n.getIpStr()));
                inboundNodes.clear();
            }

            synchronized (activeNodes) {
                activeNodes.forEach(
                        (k, n) ->
                                p2pMgr.closeSocket(
                                        n.getChannel(),
                                        "p2p-shutdown active node="
                                                + n.getIdShort()
                                                + " ip="
                                                + n.getIpStr()));
                activeNodes.clear();
            }

        } catch (Exception e) {
            p2pLOG.info("<p2p-shutdown exception>", e);
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
            p2pLOG.info("<p2p-ban null exception>", e);
        }
    }
}
