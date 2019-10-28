package org.aion.p2p;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public interface INodeMgr {

    void dropActive(int _nodeIdHash, String _reason);

    int activeNodesSize();

    INode tempNodesTake();

    boolean isSeedIp(String _ip);

    void addTempNode(INode _n);

    void addOutboundNode(INode _n);

    void addInboundNode(INode _n);

    INode allocNode(String ip, int p0);

    INode getActiveNode(int k);

    List<INode> getActiveNodesList();

    int tempNodesSize();

    INode getInboundNode(int k);

    INode getOutboundNode(int k);

    String dumpNodeInfo(String selfShortId, boolean complete);

    void seedIpAdd(String _ip);

    void shutdown();

    INode getRandom();

    Map<Integer, INode> getActiveNodesMap();

    void timeoutCheck(long currentTimeMillis);

    void ban(int _nodeIdHash);

    boolean notActiveNode(int _nodeIdHash);

    boolean notAtOutboundList(int _nodeIdHash);

    int getAvgLatency();

    /**
     * move node object from the inbound or outbound list to the active list
     *
     * @param _hash the hash of the node, see inboundNodes/outboundNodes use in the NodeMgr
     * @param _type the string represent "inbound" or "outbound"
     */
    void movePeerToActive(int _hash, String _type);

    /**
     * This method is updating the kernel best block to the peerlist information
     * @param blockNumber blockNumber of the bestblock
     * @param blockHash blockHash of the bestblock
     * @param blockTD blockTD of the bestblock
     */
    void updateChainInfo(long blockNumber, byte[] blockHash, BigInteger blockTD);
}
