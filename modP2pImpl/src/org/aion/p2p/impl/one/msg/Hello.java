package org.aion.p2p.impl.one.msg;

import java.io.UnsupportedEncodingException;
import org.aion.p2p.Msg;
import org.aion.p2p.Ver;

/**
 * @author chris
 *     <p>Test versioning
 */
public final class Hello extends Msg {

    private String msg;

    public Hello(String _msg) {
        super(Ver.V1, (byte) 0, (byte) 0);
        this.msg = _msg;
    }

    public String getMsg() {
        return this.msg;
    }

    public static Hello decode(final byte[] _bytes) throws UnsupportedEncodingException {
        return new Hello(new String(_bytes, "UTF-8"));
    }

    @Override
    public byte[] encode() {
        return this.msg.getBytes();
    }
}
