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
package org.aion.p2p;

import java.util.List;
import java.util.Map;

public interface INodeMgr {

	void timeoutActive(final IP2pMgr _p2pMgr);

	void moveInboundToActive(int _channelHashCode, final IP2pMgr _p2pMgr);

	void moveOutboundToActive(int _nodeIdHash, String _shortId, final IP2pMgr _p2pMgr);

	void dropActive(int _nodeIdHash, final IP2pMgr _p2pMgr, String _reason);

	void timeoutInbound(IP2pMgr _p2pMgr);

	Map<Integer, INode> getOutboundNodes();

	int activeNodesSize();

	INode tempNodesTake() throws InterruptedException;

	boolean isSeedIp(String _ip);

	void addTempNode(INode _n);

	boolean hasActiveNode(int k);

	void addOutboundNode(INode _n);

	void addInboundNode(INode _n);

	INode allocNode(String ip, int p0);

	INode getActiveNode(int k);

	List<INode> getActiveNodesList();

	int tempNodesSize();

	INode getInboundNode(int k);

	INode getOutboundNode(int k);

	String dumpNodeInfo(String selfShortId);

	void seedIpAdd(String _ip);

	void shutdown(IP2pMgr _p2pMgr);

	void ban(int _nodeIdHash);

	INode getRandom();

	Map<Integer, INode> getActiveNodesMap();

}
