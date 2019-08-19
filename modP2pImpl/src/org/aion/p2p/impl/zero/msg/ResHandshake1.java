package org.aion.p2p.impl.zero.msg;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.annotation.Nonnull;
import org.slf4j.Logger;

/** @author chris */
public final class ResHandshake1 extends ResHandshake {

    // success(byte) + binary version len (byte)
    private static final int MIN_LEN = 2;

    private final Logger p2pLOG;
    private String binaryVersion;

    public ResHandshake1(final Logger p2pLOG, boolean _success, @Nonnull final String _binaryVersion) {
        super(_success);

        this.p2pLOG = p2pLOG;

        // truncate string when byte length large then 127
        if (_binaryVersion.getBytes().length > Byte.MAX_VALUE) {
            this.binaryVersion =
                    new String(
                            ByteBuffer.allocate(Byte.MAX_VALUE)
                                    .put(_binaryVersion.getBytes(), 0, Byte.MAX_VALUE)
                                    .array());
        } else {
            this.binaryVersion = _binaryVersion;
        }
    }

    public static ResHandshake1 decode(final byte[] _bytes, final Logger p2pLOG) {
        if (_bytes == null || _bytes.length < MIN_LEN) {
            return null;
        } else {
            try {
                // decode binary version
                byte len = _bytes[1];
                if (len > 0 && _bytes.length >= MIN_LEN + len) {
                    byte[] binaryVersionBytes = Arrays.copyOfRange(_bytes, MIN_LEN, MIN_LEN + len);
                    String binaryVersion;
                    try {
                        binaryVersion = new String(binaryVersionBytes, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        if (p2pLOG.isDebugEnabled()) {
                            p2pLOG.debug("res-handshake-decode error.", e);
                        }
                        return null;
                    }
                    return new ResHandshake1(p2pLOG, _bytes[0] == 0x01, binaryVersion);
                } else {
                    if (p2pLOG.isDebugEnabled()) {
                        p2pLOG.debug(
                                "res-handshake-decode length error. verLen={} msgLen={}",
                                len,
                                _bytes.length);
                    }
                    return null;
                }
            } catch (Exception e) {
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("res-handshake-decode error.", e);
                }
                return null;
            }
        }
    }

    public String getBinaryVersion() {
        return this.binaryVersion;
    }

    @Override
    public byte[] encode() {
        byte[] superBytes = super.encode();
        byte[] binaryVersionBytes;
        try {
            binaryVersionBytes = this.binaryVersion.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            if (p2pLOG.isDebugEnabled()) {
                p2pLOG.debug("res-handshake-encode error.", e);
            }
            return null;
        }

        int len = binaryVersionBytes.length;
        if (len > Byte.MAX_VALUE) {
            binaryVersionBytes = Arrays.copyOfRange(binaryVersionBytes, 0, Byte.MAX_VALUE);
            len = Byte.MAX_VALUE;
            // Update the Version
            try {
                this.binaryVersion = new String(binaryVersionBytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                if (p2pLOG.isDebugEnabled()) {
                    p2pLOG.debug("res-handshake-encode error.", e);
                }
                return null;
            }
        }
        ByteBuffer buf = ByteBuffer.allocate(superBytes.length + 1 + len);
        buf.put(superBytes);
        buf.put((byte) len);
        buf.put(binaryVersionBytes);
        return buf.array();
    }
}
