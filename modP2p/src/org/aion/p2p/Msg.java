package org.aion.p2p;

/** @author chris */
public abstract class Msg {

    private final Header header;

    /**
     * @param _ver short
     * @param _ctrl byte
     * @param _act byte
     * @warning: at the msg construction phase, len of msg is unknown therefore right before
     *     socket.write, we need to figure out len before preparing the byte[]
     */
    public Msg(short _ver, byte _ctrl, byte _act) {
        this.header = new Header(_ver, _ctrl, _act, 0);
    }

    /** @return Header */
    public Header getHeader() {
        return this.header;
    }

    /**
     * Returns byte array encoding of message.
     *
     * @return
     */
    public abstract byte[] encode();
}
