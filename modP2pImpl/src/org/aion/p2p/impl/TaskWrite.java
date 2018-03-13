package org.aion.p2p.impl;

import org.aion.p2p.Header;
import org.aion.p2p.Msg;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class TaskWrite implements Runnable {

    private ExecutorService workers;
    private boolean showLog;
    private String nodeShortId;
    private SocketChannel sc;
    private Msg msg;
    private ChannelBuffer channelBuffer;

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
        Thread.currentThread().setName("p2p-write");

        // NOTE: the following logic may cause message loss
        if (this.channelBuffer.onWrite.compareAndSet(false, true)) {
            /*
             * @warning header set len (body len) before header encode
             */
            byte[] bodyBytes = msg.encode();
            int bodyLen = bodyBytes == null ? 0 : bodyBytes.length;
            Header h = msg.getHeader();
            h.setLen(bodyLen);
            byte[] headerBytes = h.encode();

            //System.out.println("write " + h.getCtrl() + "-" + h.getAction());
            ByteBuffer buf = ByteBuffer.allocate(headerBytes.length + bodyLen);
            buf.put(headerBytes);
            if (bodyBytes != null)
                buf.put(bodyBytes);
            buf.flip();

            try {
                while (buf.hasRemaining()) {
                    sc.write(buf);
                }
            } catch (IOException e) {
                if (showLog) {
                    System.out.println("<p2p write-msg-io-exception node=" + this.nodeShortId + ">");
                }
            } finally {
                this.channelBuffer.onWrite.set(false);
                try {

                    Msg msg = this.channelBuffer.messages.poll(1, TimeUnit.MILLISECONDS);

                    if (msg != null) {
                        //System.out.println("write " + h.getCtrl() + "-" + h.getAction());
                        workers.submit(new TaskWrite(workers, showLog, nodeShortId, sc, msg, channelBuffer));
                    }
                } catch (InterruptedException e) {
                    if(showLog)
                        e.printStackTrace();
                }
            }
        } else {
            try {
                this.channelBuffer.messages.put(msg);
            } catch (InterruptedException e) {
                if(showLog)
                    e.printStackTrace();
            }
        }
    }
}
