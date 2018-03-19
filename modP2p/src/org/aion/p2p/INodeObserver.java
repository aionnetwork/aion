package org.aion.p2p;

/**
 * Currently observable methods from NodeMgr, note that the thread
 * executing these functions are guaranteed to be executed in
 * the same order as they are executed in the {@link IP2pMgr}
 * threads.
 *
 */
public interface INodeObserver {

    void newActiveNode(Integer nodeId);

    void removeActiveNode(Integer nodeId);
}
