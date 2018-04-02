package org.aion.p2p;

import org.aion.p2p.INode;

public interface INodeMgr {
    
    void updateAllNodesInfo(INode _n);

    /**
     * Registers a single observer. This function will potentially
     * throw {@link IllegalStateException} if multiple observers
     * are attempted to be registered.
     *
     * Should only be called right after initialization, should not
     * be called anytime after
     *
     * @param observer
     * @see INodeObserver
     */
    void registerNodeObserver(INodeObserver observer);
}
