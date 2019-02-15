package org.aion.zero.impl.sync;

import org.aion.base.util.ByteUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.TransactionStore;
import org.aion.p2p.IP2pMgr;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.sync.msg.ReqTxReceipts;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionTxInfo;
import org.aion.zero.types.AionTransaction;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReceiptsRetrievalVerifier {
    private final IP2pMgr p2p;
    private final IAionBlockchain bc;
    private final Map<String, AionBlock> outstandingRequests;

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    public ReceiptsRetrievalVerifier(
            IP2pMgr p2p,
            IAionBlockchain blockchain) {
        this.p2p = p2p;
        this.bc = blockchain;
        this.outstandingRequests = new HashMap<>();
    }

    public void requestReceiptsFromPeers(List<AionBlock> blocks, String originNodeDisplayId, int originNodeHashId) {
        for(AionBlock b : blocks) {
            for(AionTransaction tx : b.getTransactionsList())
            outstandingRequests.put(ByteUtil.toHexString(tx.getTransactionHash()), b);
        }

        ReqTxReceipts request = new ReqTxReceipts(blocks
                .stream()
                .flatMap(b -> b.getTransactionsList()
                        .stream().map(t ->  t.getTransactionHash())
                )
                .collect(Collectors.toList())
        );

        long b0 = blocks.get(0).getNumber();
        long bn = blocks.get(blocks.size()-1).getNumber();
        String logMsg = String.format(
                "<<<ReceiptsRetrievalVerifier>>> requesting receipts for for blocks (%d, %d) from peer %s.  reqSize = " + request.getTxHashes().size(),
                b0, bn, originNodeDisplayId
        );
        LOG.info(logMsg);

        p2p.send(originNodeHashId, originNodeDisplayId, request);
    }

    /** Validate an incoming AionTxInfo against what is stored in the canonical tx db */
    public void validateAgainstBlockchain(AionTxInfo receivedInfo) {
        byte[] hash = receivedInfo.getReceipt().getTransaction().getTransactionHash();
        AionTxInfo blockchainInfo = bc.getTransactionInfo(hash);

        if(!Arrays.equals(
                receivedInfo.getReceipt().getTransaction().getTransactionHash(),
                blockchainInfo.getReceipt().getTransaction().getTransactionHash())) {
            throw new IllegalArgumentException(String.format("Can't compare AionTxInfo of different hashes (%s / %s)",
                    receivedInfo.getReceipt().getTransaction().getTransactionHash(),
                    blockchainInfo.getReceipt().getTransaction().getTransactionHash()));
        }

        String result = receivedInfo.equals(blockchainInfo) ? "SAME" : "DIFFERENT";
        LOG.info(String.format("<<<ReceiptsRetrievalVerifier %s>>> result=%s txHash=%s",
                "blockchain",
                result,
                ByteUtil.toHexString(receivedInfo.getReceipt().getTransaction().getTransactionHash())));
    }

    /** Validate that alternate tx db and canonical tx db returns same data when given a tx hash key. */
    public void validateDatabases(AionTxInfo receivedInfo, TransactionStore alternateTxStore) {
        byte[] hash = receivedInfo.getReceipt().getTransaction().getTransactionHash();

        List<AionTxInfo> altDbInfos = alternateTxStore.get(hash);
        List<AionTxInfo> canonicalDbInfos = AionRepositoryImpl.inst().getTransactionStore().get(hash);

        String result = altDbInfos.equals(canonicalDbInfos) ? "SAME" : "DIFFERENT";
        LOG.info(String.format("<<<ReceiptsRetrievalVerifier %s>>> result=%s altDbInfos=%s canonicalDbInfos=%s",
                "database",
                result,
                altDbInfos,
                canonicalDbInfos));
    }

    public void receivedTxHash(byte[] txHash) {
        String sTxHash = ByteUtil.toHexString(txHash);
        if(!outstandingRequests.containsKey(sTxHash)) {
            LOG.info(String.format(
                    "<<<ReceiptsRetrievalVerifier unexpected-rx>>> Received txHash %s but was not expected to",
                    ByteUtil.toHexString(txHash)));
        } else {
            outstandingRequests.remove(sTxHash);
        }
    }

    public void displayOutstandingRequests() {
        StringBuffer sb = new StringBuffer();
        LOG.info("<<<ReceiptsRetrievalVerifier outstanding-requests>>>" + outstandingRequests.entrySet()
                .stream()
                .map(e -> String.format("<txHash=%s (Block %s)",
                        e.getKey(), e.getValue().getNumber()))
                .collect(Collectors.toList())
                .toString()
        );
    }
}
