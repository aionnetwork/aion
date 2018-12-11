package org.aion.p2p;

/** @author chris */
public abstract class Handler {

    private Header header;

    /**
     * @param _ver short
     * @param _ctrl byte
     * @param _act byte
     */
    public Handler(short _ver, byte _ctrl, byte _act) {
        this.header = new Header(_ver, _ctrl, _act, 0);
    }

    /** @return Header */
    public Header getHeader() {
        return this.header;
    }

    /**
     * @param _id int
     * @param _displayId String
     * @param _msg byte[]
     */
    public abstract void receive(int _id, String _displayId, final byte[] _msg);

    public void shutDown() {}
}
