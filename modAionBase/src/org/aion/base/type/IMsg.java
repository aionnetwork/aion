package org.aion.base.type;

public interface IMsg {

    /**
     * Returns the message header.
     * @return
     */
    IMsgHeader getHeader();

    /**
     * Returns byte array encoding of message.
     * @return
     */
    byte[] encode();

}
