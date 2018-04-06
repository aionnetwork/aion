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

package org.aion.p2p.impl1;

import org.aion.p2p.Header;
import org.aion.p2p.Msg;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author chris
 */
public class TaskWrite implements Runnable {

	private boolean showLog;
	private String nodeShortId;
	private SocketChannel sc;
	private Msg msg;
	private ChannelBuffer channelBuffer;
	private P2pMgr p2pMgr;

	TaskWrite(boolean _showLog, String _nodeShortId, SocketChannel _sc, Msg _msg, ChannelBuffer _cb, P2pMgr p2pMgr

	) {

		this.showLog = _showLog;
		this.nodeShortId = _nodeShortId;
		this.sc = _sc;
		this.msg = _msg;
		this.channelBuffer = _cb;
		this.p2pMgr = p2pMgr;
	}

	private void clearChannelBuffer() {
		channelBuffer.refreshHeader();
		channelBuffer.refreshBody();
		p2pMgr.removeActive(channelBuffer.nodeIdHash);
	}

	@Override
	public void run() {
		// reset allocated buffer and clear messages if the channel is closed
		if (channelBuffer.isClosed.get()) {
			clearChannelBuffer();
			return;
		}

		try {
			// channelBuffer.lock.tryLock(2, TimeUnit.MILLISECONDS);
			channelBuffer.lock.lock();

			/*
			 * @warning header set len (body len) before header encode
			 */
			byte[] bodyBytes = msg.encode();
			int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
			Header h = msg.getHeader();
			h.setLen(bodyLen);
			byte[] headerBytes = h.encode();

			// print route
			// System.out.println("write " + h.getVer() + "-" + h.getCtrl() +
			// "-" + h.getAction());
			ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
			buf.put(headerBytes);
			if (bodyBytes != null)
				buf.put(bodyBytes);
			buf.flip();

			try {
				while (buf.hasRemaining()) {
					sc.write(buf);
				}
			} catch (ClosedChannelException ex1) {
				if (showLog) {
					System.out.println("<p2p closed-channel-exception node=" + this.nodeShortId + ">");
				}
				channelBuffer.isClosed.set(true);
			} catch (IOException ex2) {
				String reason = ex2.getMessage();
				if (showLog) {
					System.out.println("<p2p write-msg-io-exception node=" + this.nodeShortId + ">" + ex2.getMessage());
				}
				if (reason.equals("Broken pipe".intern())) {
					channelBuffer.isClosed.set(true);
				}
			} finally {
				// channelBuffer.refreshHeader();
				// channelBuffer.refreshBody();
			}
		} catch (Exception e) {
			e.printStackTrace();
			// channelBuffer.refreshHeader();
			// channelBuffer.refreshBody();
		} finally {
			channelBuffer.lock.unlock();
		}
	}
}
