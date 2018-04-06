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

package org.aion.p2p.impl2;

import org.aion.p2p.Header;
import org.aion.p2p.Msg;
import org.aion.p2p.impl2.selector.MainIOLoop;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

/**
 * @author chris
 */
public class TaskWrite implements Runnable {

	private MainIOLoop ioLoop;
	private boolean showLog;
	private String nodeShortId;
	private SocketChannel sc;
	private Msg msg;

	TaskWrite(final MainIOLoop ioLoop, final ExecutorService worker, boolean _showLog, String _nodeShortId,
			final SocketChannel _sc, final Msg _msg) {
		this.ioLoop = ioLoop;
		this.showLog = _showLog;
		this.nodeShortId = _nodeShortId;
		this.sc = _sc;
		this.msg = _msg;
	}

	@Override
	public void run() {
		/*
		 * @warning header set len (body len) before header encode
		 */
		try {
			byte[] bodyBytes = msg.encode();
			int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
			Header h = msg.getHeader();
			h.setLen(bodyLen);
			byte[] headerBytes = h.encode();

			// print route
			// System.out.println("write " + h.getVer() + "-" + h.getCtrl() + "-" +
			// h.getAction());
			ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
			buf.put(headerBytes);
			if (bodyBytes != null)
				buf.put(bodyBytes);
			buf.flip();

			// send outbound event to ioLoop for I/O
			this.ioLoop.write(buf, this.sc);
		} catch (Throwable e) {
			System.out.println("<p2p-taskWrite-throw>" + e.toString());
		}
	}
}
