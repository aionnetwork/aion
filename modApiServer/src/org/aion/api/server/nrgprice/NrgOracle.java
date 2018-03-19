package org.aion.api.server.nrgprice;

import org.aion.api.server.nrgprice.INrgPriceAdvisor;
import org.aion.api.server.nrgprice.NrgBlockPriceStrategy;
import org.aion.base.type.*;
import org.aion.evtmgr.IHandler;
import org.aion.evtmgr.impl.callback.EventCallbackA0;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.core.IAionBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.slf4j.Logger;

public class NrgOracle {
    private static final int BLK_TRAVERSE_ON_INSTANTIATION = 128;
    private static final long CACHE_FLUSH_BLKCOUNT = 1;

    private static final int BLKPRICE_WINDOW = 20;
    private static final int BLKPRICE_PERCENTILE = 60;

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    // flush the recommendation every CACHE_FLUSH_BLKCOUNT blocks
    private long cacheFlushCounter;
    private long recommendation;

    INrgPriceAdvisor advisor;

    /*   This constructor could take considerable time (since it warms up the recommendation engine)
     *   Please keep the BLK_TRAVERSE_ON_INSTANTIATION number reasonably small, to construct this object fast.
     */
    public NrgOracle(IAionBlockchain blockchain, IHandler handler, long nrgPriceDefault, long nrgPriceMax) {

        // get default and max nrg from the config
        cacheFlushCounter = 0;
        recommendation = nrgPriceDefault;

        advisor = new NrgBlockPriceStrategy(nrgPriceDefault, nrgPriceMax, BLKPRICE_WINDOW, BLKPRICE_PERCENTILE);

        // warm up the NrgPriceAdvisor with historical data
        // rationale for doing this in the constructor: if you don't find any transaction within the first 128 blocks
        // (at a 10s block time, that's ~20min), the miners should be willing to accept any transactions (given they meet
        // thier lower bound threshold)
        AionBlock lastBlock = blockchain.getBestBlock();
        int itr = 0;
        while (itr < BLK_TRAVERSE_ON_INSTANTIATION) {
            advisor.processBlock(lastBlock);

            if (!advisor.isHungry()) break; // recommendation engine warmed up to give good advice

            // traverse up the chain to feed the recommendation engine
            long parentBlockNumber = lastBlock.getNumber() - 1;
            if (parentBlockNumber <= 0)
                break;

            lastBlock = blockchain.getBlockByHash(lastBlock.getParentHash());
            itr--;
        }

        // check if handler is of type BLOCK, if so attach our event
        if (handler != null && handler.getType() == IHandler.TYPE.BLOCK0.getValue()) {
            handler.eventCallback(new EventCallbackA0<IBlock, ITransaction, ITxReceipt, IBlockSummary, ITxExecSummary, ISolution>() {
                public void onBlock(final IBlockSummary _bs) {
                    LOG.debug("nrg-oracle - onBlock event");
                    AionBlockSummary bs = (AionBlockSummary) _bs;
                    processBlock(bs);
                }
            });
        } else {
            LOG.error("nrg-oracle - invalid handler provided to constructor");
        }
    }

    private void processBlock(AionBlockSummary blockSummary) {
        AionBlock blk = (AionBlock) blockSummary.getBlock();
        advisor.processBlock(blk);
        cacheFlushCounter--;
    }

    public long getNrgPrice() {
        // rationale: if no new blocks have come in, just serve the info in the cache,
        //
        if (cacheFlushCounter <= 0) {
            recommendation = advisor.computeRecommendation();
            cacheFlushCounter = CACHE_FLUSH_BLKCOUNT;
        }

        return recommendation;
    }
}
