package org.aion.zero.impl.sync.handler;

import org.aion.base.util.ByteUtil;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.p2p.Ctrl;
import org.aion.p2p.Handler;
import org.aion.p2p.IP2pMgr;
import org.aion.p2p.Ver;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.sync.msg.RequestReceipts;
import org.aion.zero.impl.sync.msg.ResponseReceipts;
import org.aion.zero.impl.types.AionTxInfo;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;

/** Handle requests for transaction receipts */
public class RequestReceiptsHandler extends Handler {
    // Impl notes:
    // - Consider having a cache, like ReqBlocksBodiesHandler does
    // - Consider having an upper bound on number of receipts we're willing to send
    // - Parameterize with generics for non-Aion blockchains (i.e. classes under IBlockchain but not
    // IAionBlockchain)?

    private final IP2pMgr p2pMgr;
    private final IAionBlockchain bc;

    private static final Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.SYNC.name());

    /**
     * Constructor
     *
     * @param p2pMgr p2p manager
     * @param bc blockchain
     */
    public RequestReceiptsHandler(IP2pMgr p2pMgr, IAionBlockchain bc) {
        super(Ver.V0, Ctrl.SYNC, Act.REQUEST_RECEIPTS);
        this.p2pMgr = p2pMgr;
        this.bc = bc;
    }

    @Override
    public void receive(int id, String displayId, byte[] msg) {
        final RequestReceipts requestReceipts;
        try {
            requestReceipts = new RequestReceipts(msg);
        } catch (NullPointerException | IllegalArgumentException ex) {
            LOGGER.error(
                    "ReqTxReceiptHandler req-tx-receipts decode-error, unable to decode bodies from {}, len: {}, reason: {}",
                    displayId,
                    msg.length,
                    ex.getMessage());
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("req-tx-receipts dump: {}", ByteUtil.toHexString(msg));
            }
            return;
        }
        LOGGER.debug(
                "ReqTxReceiptHandler receive start, request size "
                        + requestReceipts.getTxHashes().size());

        List<AionTxInfo> receipts = new LinkedList<>();
        for (byte[] txHash : requestReceipts.getTxHashes()) {
            AionTxInfo txInfo = bc.getTransactionInfo(txHash);
            if (txInfo != null) {
                receipts.add(txInfo);
            }
            LOGGER.trace("Requested receipt with txHash '%s' not found; ignoring it");
        }

        LOGGER.debug(
                String.format(
                        "ReqTxReceiptHandler received receipt request of size %d; sending back %d receipts",
                        requestReceipts.getTxHashes().size(), receipts.size()));
        this.p2pMgr.send(id, displayId, new ResponseReceipts(receipts));
    }
}
