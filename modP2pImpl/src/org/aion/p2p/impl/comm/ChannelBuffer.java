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
package org.aion.p2p.impl.comm;

import org.aion.p2p.Header;
import org.aion.p2p.Msg;
import org.aion.p2p.impl2.selector.Task;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * @author chris
 *
 */
public class ChannelBuffer {

	public int nodeIdHash = 0;

	public String ip;
	public int port;

	public ByteBuffer headerBuf = ByteBuffer.allocate(Header.LEN);

	public ByteBuffer bodyBuf = null;

	public Header header = null;

	public byte[] body = null;

	public Task task;

	/**
	 * write flag
	 */
	public AtomicBoolean onWrite = new AtomicBoolean(false);

	/**
	 * Indicates whether this channel is closed.
	 */
	public AtomicBoolean isClosed = new AtomicBoolean(false);

	public BlockingQueue<Msg> messages = new ArrayBlockingQueue<>(128);

	public void refreshHeader() {
		headerBuf.clear();
		header = null;
	}

	public void refreshBody() {
		bodyBuf = null;
		body = null;
	}

	/**
	 * @return boolean
	 */
	public boolean isHeaderCompleted() {
		return header != null;
	}

	/**
	 * @return boolean
	 */
	public boolean isBodyCompleted() {
		return this.header != null && this.body != null && body.length == header.getLen();
	}

}
