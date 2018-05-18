package org.aion.p2p.impl1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aion.p2p.P2pConstant;

public class TaskInbound2 implements Runnable {

    private AtomicBoolean start = new AtomicBoolean(true);
    private final boolean showLog;
    private final P2pMgr mgr;
    private final Selector selector;

    public TaskInbound2(P2pMgr _mgr, boolean _showLog, Selector _selector) {
        this.showLog = _showLog;
        this.mgr = _mgr;
        this.selector = _selector;
    }

    @Override
    public void run() {

        // read buffer pre-alloc. @ max_body_size
        ByteBuffer readBuf = ByteBuffer.allocate(P2pConstant.MAX_BODY_SIZE);

        while (start.get()) {

            try {
                Thread.sleep(0, 1);
            } catch (Exception e) {
            }

            int num;
            try {
                num = this.selector.selectNow();
            } catch (IOException e) {
                if (showLog)
                    System.out.println("<p2p inbound-select-io-exception>");
                continue;
            }

            if (num == 0) {
                continue;
            }

            Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();

            while (keys.hasNext() && (num-- > 0)) {

                final SelectionKey sk = keys.next();
                keys.remove();

                try{

                    if (!sk.isValid())
                        continue;

                    if (sk.isAcceptable())
                        this.mgr.accept();

                    if (sk.isReadable()) {

                        readBuf.rewind();

                        ChannelBuffer chanBuf = (ChannelBuffer) (sk.attachment());
                        try {

                            int ret;
                            int cnt = 0;

                            while ((ret = ((SocketChannel) sk.channel()).read(readBuf)) > 0) {
                                cnt += ret;
                            }

                            // read empty select key, continue.
                            if (cnt <= 0) {
                                continue;
                            }

                            int prevCnt = cnt + chanBuf.buffRemain;
                            ByteBuffer forRead;

                            if (chanBuf.buffRemain != 0) {
                                byte[] alreadyRead = new byte[cnt];

                                readBuf.position(0);
                                readBuf.get(alreadyRead);
                                forRead = ByteBuffer.allocate(prevCnt);
                                forRead.put(chanBuf.remainBuffer);
                                forRead.put(alreadyRead);
                            } else {
                                forRead = readBuf;
                            }

                            do {
                                cnt = this.mgr.read(sk, forRead, prevCnt);

                                if (prevCnt == cnt) {
                                    break;
                                } else
                                    prevCnt = cnt;

                            } while (cnt > 0);

                            // check if really read data.
                            if (cnt > prevCnt) {
                                chanBuf.buffRemain = 0;
                                throw new P2pException(
                                    "IO read overflow!  suppose read:" + prevCnt + " real left:" + cnt);
                            }

                            chanBuf.buffRemain = cnt;

                            if (cnt == 0) {
                                readBuf.rewind();
                            } else {
                                // there are no perfect cycling buffer in jdk
                                // yet.
                                // simply just buff move for now.
                                // @TODO: looking for more efficient way.

                                int currPos = forRead.position();
                                chanBuf.remainBuffer = new byte[cnt];
                                forRead.position(currPos - cnt);
                                forRead.get(chanBuf.remainBuffer);
                                readBuf.rewind();
                            }

                        } catch (NullPointerException e) {
                            this.mgr.closeSocket((SocketChannel) sk.channel(), chanBuf.displayId + "-read-msg-null-exception");
                            chanBuf.isClosed.set(true);
                        } catch (P2pException e) {
                            this.mgr.closeSocket((SocketChannel) sk.channel(), chanBuf.displayId + "-read-msg-p2p-exception");
                            chanBuf.isClosed.set(true);

                        } catch (ClosedChannelException e) {
                            this.mgr.closeSocket((SocketChannel) sk.channel(),
                                chanBuf.displayId + "-read-msg-closed-channel-exception");

                        } catch (IOException e) {
                            this.mgr.closeSocket((SocketChannel) sk.channel(),
                                chanBuf.displayId + "-read-msg-io-exception: " + e.getMessage());
                            chanBuf.isClosed.set(true);

                        } catch (CancelledKeyException e) {
                            chanBuf.isClosed.set(true);
                            this.mgr.closeSocket((SocketChannel) sk.channel(),
                                chanBuf.displayId + "-read-msg-key-cancelled-exception");
                        } catch (Exception e) {
                            if (showLog)
                                System.out.println("<p2p-pi global exception>");
                        }
                    }
                } catch(Exception ex) {
                    if(showLog) {
                        System.out.println("<p2p-pi on-sk-exception=" + ex.getMessage() + ">");
                        ex.printStackTrace();
                    }
                }
            }
        }
        if (showLog)
            System.out.println("<p2p-pi shutdown>");
    }

}
