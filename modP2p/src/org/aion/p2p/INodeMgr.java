package org.aion.p2p;

import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;

public interface INodeMgr {

	void updateAllNodesInfo(INode _n);

	// void registerNodeObserver(INodeObserver observer);

	void rmTimeOutActives(IP2pMgr pmgr);

	void moveInboundToActive(int _channelHashCode, final IP2pMgr _p2pMgr);

	void moveOutboundToActive(int _nodeIdHash, String _shortId, final IP2pMgr _p2pMgr);

	void dropActive(Integer nodeIdHash, IP2pMgr pmgr);

	void removeActive(Integer nodeIdHash, IP2pMgr pmgr);

}
