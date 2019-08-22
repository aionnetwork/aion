package org.aion.p2p;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;

/** @author chris */
public interface IP2pMgr {

    /** @return Map */
    Map<Integer, INode> getActiveNodes();

    /** @param _hs List<Handler> */
    void register(final List<Handler> _hs);

    /** @return INode */
    INode getRandom();

    /**
     * @param _id int
     * @param _msg Msg
     */
    void send(int _id, String _displayId, final Msg _msg);

    /** Used to hook up with kernel to shutdown threads in network module. */
    void shutdown();

    /** Starts all p2p processes. */
    void run();

    List<Short> versions();

    int getSelfIdHash();

    void closeSocket(final SocketChannel _sc, String _reason);

    void closeSocket(final SocketChannel _sc, String _reason, Exception e);

    void errCheck(int nodeIdHashcode, String _displayId);

    void dropActive(int _nodeIdHash, String _reason);

    void configChannel(SocketChannel _channel) throws IOException;

    int getMaxActiveNodes();

    boolean isSyncSeedsOnly();

    int getMaxTempNodes();

    boolean validateNode(INode _node);

    int getAvgLatency();

    /**
     * Compares the given network identifier to the one recorded in the p2p manager.
     *
     * @param netId network identifier for attempted connection
     * @return {@code true} if the network is compatible according to the p2p manager's definition
     *     of correctness, {@code false} otherwise
     */
    boolean isCorrectNetwork(int netId);
}
