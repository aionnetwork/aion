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

import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.impl.zero.msg.ReqActiveNodes;
import org.slf4j.Logger;

/**
 *
 * @author chris
 *
 */
public final class TaskRequestActiveNodes implements Runnable {

	private final IP2pMgr mgr;

	private final Logger p2pLOG;

	public TaskRequestActiveNodes(final IP2pMgr _mgr, final Logger p2pLOG) {
		this.mgr = _mgr;
		this.p2pLOG = p2pLOG;
	}

	@Override
	public void run() {
		INode node = mgr.getRandom();
		if (node != null) {
			if (p2pLOG.isTraceEnabled()) {
				p2pLOG.trace("TaskRequestActiveNodes: {}", node.toString());
			}
			this.mgr.send(node.getIdHash(), node.getIdShort(), new ReqActiveNodes());
		}
	}
}