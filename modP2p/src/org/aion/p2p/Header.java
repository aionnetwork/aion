package org.aion.p2p;

import java.nio.ByteBuffer;

/** @author chris */
public class Header {

    public static final int LEN = 8;

    private static final int MAX_BODY_LEN_BYTES = P2pConstant.MAX_BODY_SIZE;
    private final short ver;
    private final byte ctrl;
    private final byte action;
    private int len;

    /**
     * @param _ver short
     * @param _ctrl byte
     * @param _action byte
     * @param _len byte
     */
    Header(short _ver, byte _ctrl, byte _action, int _len) {
        this.ver = _ver;
        this.ctrl = _ctrl;
        this.action = _action;
        this.len = _len < 0 ? 0 : _len;
    }

    /** @return short */
    public short getVer() {
        return this.ver;
    }

    /** @return byte */
    public byte getCtrl() {
        return this.ctrl;
    }

    /** @return byte */
    public byte getAction() {
        return this.action;
    }

    /** @return int */
    public int getRoute() {
        return (ver << 16) | (ctrl << 8) | action;
    }

    /** @return int */
    public int getLen() {
        return this.len;
    }

    public void setLen(int _len) {
        this.len = _len;
    }

    /** @return byte[] */
    public byte[] encode() {
        return ByteBuffer.allocate(LEN).putInt(this.getRoute()).putInt(len).array();
    }

    /**
     * @param _headerBytes byte[]
     * @return Header
     */
    public static Header decode(final byte[] _headerBytes) {
        if (_headerBytes == null || _headerBytes.length != LEN) {
            throw new IllegalArgumentException("invalid-header-bytes");
        } else {
            ByteBuffer bb1 = ByteBuffer.wrap(_headerBytes);
            short ver = bb1.getShort();
            byte ctrl = bb1.get();
            byte action = bb1.get();
            int len = bb1.getInt();
            if (len > MAX_BODY_LEN_BYTES) {
                throw new IndexOutOfBoundsException("exceed-max-body-size");
            }
            return new Header(ver, ctrl, action, len);
        }
    }
}
