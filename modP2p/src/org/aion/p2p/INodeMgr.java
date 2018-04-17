package org.aion.p2p;

public interface INodeMgr {

	void updateAllNodesInfo(INode _n);

	void rmTimeOutActives(IP2pMgr pmgr);

	void moveInboundToActive(int _channelHashCode, final IP2pMgr _p2pMgr);

	void moveOutboundToActive(int _nodeIdHash, String _shortId, final IP2pMgr _p2pMgr);

	void dropActive(Integer nodeIdHash, IP2pMgr pmgr);

	void removeActive(Integer nodeIdHash, IP2pMgr pmgr);

}
