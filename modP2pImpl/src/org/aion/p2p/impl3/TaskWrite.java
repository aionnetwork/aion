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

package org.aion.p2p.impl3;

import org.aion.p2p.Header;
import org.aion.p2p.Msg;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

/**
 * @author chris
 */
public class TaskWrite implements Runnable {

	private ExecutorService workers;
	private boolean showLog;
	private String nodeShortId;
	private SocketChannel sc;
	private Msg msg;
	private ChannelBuffer channelBuffer;

	/**
	 * @param _workers ExecutorService
	 * @param _showLog boolean
	 * @param _nodeShortId String
	 * @param _sc SocketChannel
	 * @param _msg Msg
	 * @param _cb ChannelBuffer
	 */
	TaskWrite(
			final ExecutorService _workers,
			boolean _showLog,
			String _nodeShortId,
			final SocketChannel _sc,
			final Msg _msg,
			final ChannelBuffer _cb
	) {
		this.workers = _workers;
		this.showLog = _showLog;
		this.nodeShortId = _nodeShortId;
		this.sc = _sc;
		this.msg = _msg;
		this.channelBuffer = _cb;
	}

	@Override
	public void run() {

		if (this.channelBuffer.onWrite.compareAndSet(false, true)) {

			byte[] bodyBytes = msg.encode();
			int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
			Header h = msg.getHeader();
			h.setLen(bodyLen);
			byte[] headerBytes = h.encode();

			// System.out.println("write " + h.getVer() + "-" + h.getCtrl() + "-" + h.getAction());
			ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
			buf.put(headerBytes);
			if (bodyBytes != null)
				buf.put(bodyBytes);
			buf.flip();

			try {
				while (buf.hasRemaining()) {
					sc.write(buf);
				}
			} catch (IOException ex) {
				if (showLog) {
					System.out.println("<p2p write-msg-io-exception node=" + this.nodeShortId + ">");
				}
			} finally {
				this.channelBuffer.onWrite.set(false);
                Msg msg = this.channelBuffer.messages.poll();
                if (msg != null) {
                    //System.out.println("write " + h.getCtrl() + "-" + h.getAction());
                    workers.submit(new TaskWrite(workers, showLog, nodeShortId, sc, msg, channelBuffer));
                }
			}
		} else {
			boolean success = this.channelBuffer.messages.offer(msg);
			if(showLog)
			    System.out.println(
                    "<p2p-task-write add-msg=" + (success ? "true" : "false") +
                    " channel-messages-size=" + this.channelBuffer.messages.size() + "/" + ChannelBuffer.messagesSize + ">");
		}
	}
}