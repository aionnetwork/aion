package org.aion.api.server.pb;

/** */
public interface IHdlr {
    byte[] process(byte[] request);

    void shutDown();
}
