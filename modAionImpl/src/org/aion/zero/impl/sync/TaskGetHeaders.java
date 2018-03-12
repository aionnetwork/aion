package org.aion.zero.impl.sync;

import org.aion.p2p.INode;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.impl.sync.msg.ReqBlocksHeaders;
import org.aion.zero.impl.types.AionBlock;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

final class TaskGetHeaders implements Runnable {

    private final IP2pMgr p2p;

    private final AionBlockchainImpl chain;

    private final AtomicReference<NetworkStatus> networkStatus;

    private final AtomicLong jump;

    private final int syncForwardMax;

    TaskGetHeaders(final IP2pMgr _p2p, final AionBlockchainImpl _chain, final AtomicReference<NetworkStatus> _networkStatus, final AtomicLong _jump, int _syncForwardMax){
        this.p2p = _p2p;
        this.chain = _chain;
        this.networkStatus = _networkStatus;
        this.jump = _jump;
        this.syncForwardMax = _syncForwardMax;
    }

    @Override
    public void run() {

        Thread.currentThread().setName("sync-gh");
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        AionBlock selfBlock = this.chain.getBestBlock();
        long selfNum = selfBlock.getNumber();
        long retargetNum = jump.get();

        // retarget future if its higher than self
        // long adjusted = Math.max(selfNum, retargetNum);
        long adjusted = retargetNum;

        Set<Integer> ids = new HashSet<>();

        List<INode> filtered = this.p2p.getActiveNodes().values().stream().filter(
                (n) -> this.networkStatus.get().totalDiff != null &&
                        n.getTotalDifficulty() != null &&
                        (new BigInteger(1, n.getTotalDifficulty())).compareTo(this.networkStatus.get().totalDiff) >= 0).collect(Collectors.toList());

        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < 3; i++) {
            if (filtered.size() > 0) {
                INode node = filtered.get(r.nextInt(filtered.size()));
                if (!ids.contains(node.getIdHash())) {
                    ids.add(node.getIdHash());
                    System.out.println("sync " + (adjusted + 1) + " " + this.syncForwardMax);
                    this.p2p.send(node.getIdHash(), new ReqBlocksHeaders(adjusted + 1, this.syncForwardMax));
                }
            }
        }
    }
}
