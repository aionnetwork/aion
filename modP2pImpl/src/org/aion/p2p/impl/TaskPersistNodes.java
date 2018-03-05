package org.aion.p2p.impl;

public class TaskPersistNodes implements Runnable{
    private NodeMgr nodeMgr;

    TaskPersistNodes(NodeMgr nodeMgr){
        this.nodeMgr = nodeMgr;
    }

    @Override
    public void run() {
        System.out.println("<p2p persisting-nodes-to-disk>");
        nodeMgr.persistNodes();
    }
}
