package org.aion.p2p.impl.comm;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.Header;
import org.aion.p2p.Msg;
import org.aion.p2p.impl2.selector.Task;

/** @author chris */
public class ChannelBuffer {

    public int nodeIdHash = 0;

    public String ip;
    public int port;

    public ByteBuffer headerBuf = ByteBuffer.allocate(Header.LEN);

    public ByteBuffer bodyBuf = null;

    public Header header = null;

    public byte[] body = null;

    public Task task;

    /** write flag */
    public AtomicBoolean onWrite = new AtomicBoolean(false);

    /** Indicates whether this channel is closed. */
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

    /** @return boolean */
    public boolean isHeaderCompleted() {
        return header != null;
    }

    /** @return boolean */
    public boolean isBodyCompleted() {
        return this.header != null && this.body != null && body.length == header.getLen();
    }
}
