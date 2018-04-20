package org.aion.p2p;

public interface INodeMgr {

	void timeoutActive(final IP2pMgr _p2pMgr);

	void moveInboundToActive(int _channelHashCode, final IP2pMgr _p2pMgr);

	void moveOutboundToActive(int _nodeIdHash, String _shortId, final IP2pMgr _p2pMgr);

	void dropActive(int _nodeIdHash, final IP2pMgr _p2pMgr);

	void removeActive(int nodeIdHash, final IP2pMgr _p2pMgr);

}
