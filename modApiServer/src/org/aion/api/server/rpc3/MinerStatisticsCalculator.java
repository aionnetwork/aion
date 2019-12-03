package org.aion.api.server.rpc3;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import org.aion.api.server.external.ChainHolder;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.blockchain.BlockHeader.BlockSealType;
import org.aion.rpc.errors.RPCExceptions.FailedToComputeMetricsRPCException;
import org.aion.rpc.types.RPCTypes;
import org.aion.types.AionAddress;
import org.aion.zero.impl.types.AionBlock;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

public class MinerStatisticsCalculator {

    private final ChainHolder chainHolder;
    private final int size;
    private final Deque<AionBlock> aionBlockCache;
    private final Map<AionAddress, RPCTypes.MinerStats> statsHistory;
    private final int blockTimeCount;
    private static final Logger logger= AionLoggerFactory.getLogger(LogEnum.API.name());

    MinerStatisticsCalculator(ChainHolder chainHolder, int size, int blockTimeCount) {
        this.chainHolder = chainHolder;
        this.size = size;
        aionBlockCache = new LinkedBlockingDeque<>(size); // will always store elements in the reverse
        // of natural order, ie. n-1th element>nth element
        this.blockTimeCount = blockTimeCount;
        statsHistory = Collections.synchronizedMap(new LRUMap<>(32));
    }

    private boolean update() {
        Block block = chainHolder.getBestPOWBlock();
        if (aionBlockCache.isEmpty()) {//lazy population of the block history list
            int i = 1;
            aionBlockCache.addFirst((AionBlock) block);
            while (i < size) {
                block = chainHolder.getBlockByHash(block.getHeader().getParentHash());
                if (block.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
                    aionBlockCache.addLast((AionBlock) block);
                    i++;
                }
            }
            return true;
        } else if (!Arrays.equals(aionBlockCache.peek().getHash(), block.getHash())) {
            statsHistory.clear();
            Deque<AionBlock> blockStack = new LinkedList<>(); // We're using a stack so that
                                                              // the ordering of the block history can be enforced
            blockStack.push((AionBlock) block);
            aionBlockCache.removeLast();

            int i = 1;// we start at 1 because the stack contains 1 element
            //noinspection ConstantConditions
            while (i < size
                    && block.getNumber() > 2
                    && !aionBlockCache.isEmpty()
                    && !Arrays.equals(
                            block.getHeader().getParentHash(),
                            aionBlockCache.peekFirst().getHash())) {
                block = chainHolder.getBlockByHash(block.getHeader().getParentHash());
                //skip any POS blocks
                if (block.getHeader().getSealType() == BlockSealType.SEAL_POW_BLOCK) {
                    blockStack.push((AionBlock) block);
                    aionBlockCache.removeLast();
                    i++;
                }
            }
            while (!blockStack.isEmpty()) {//transfer the contents to block cache
                aionBlockCache.push(blockStack.pop());
            }
            if(logger.isDebugEnabled()) {//log contents of the block cache
                logger.debug(
                        "Updated block history: {}",
                        aionBlockCache.stream()
                                .mapToLong(Block::getNumber)
                                .boxed()
                                .map(Object::toString)
                                .collect(Collectors.joining(", ")));
            }
            return true;
        } else return false;
    }

    public synchronized RPCTypes.MinerStats getStats(AionAddress aionAddress) {
        if (update() || !this.statsHistory.containsKey(aionAddress)) {
            BigInteger totalDifficulty = BigInteger.ZERO;
            long totalBlockTime = 0;
            long previousBlockTimestamp = -1;
            long blockCount = 0;
            try {
                int i = 0;
                Iterator<AionBlock> iterator = aionBlockCache.iterator();

                while (iterator.hasNext() && i < this.blockTimeCount) {
                    AionBlock block = iterator.next();
                    totalDifficulty =
                            totalDifficulty.add(
                                    block.getDifficultyBI()); // accumulate the difficulty
                    if (previousBlockTimestamp == -1) {
                        previousBlockTimestamp = block.getTimestamp();
                    } else {
                        // accumulate the block time
                        totalBlockTime +=
                                Math.abs(block.getTimestamp() - previousBlockTimestamp);
                        //update
                        previousBlockTimestamp = block.getTimestamp();
                    }
                    if (block.getCoinbase().equals(aionAddress)) {
                        blockCount++;
                    }
                    i++;
                }
                //noinspection ConstantConditions
                final BigInteger lastDifficulty = aionBlockCache.peekFirst().getDifficultyBI(); // get the difficulty of the last block
                final BigDecimal averageBlockTime =
                        BigDecimal.valueOf(totalBlockTime)
                                .divide(BigDecimal.valueOf(aionBlockCache.size()),
                                        RoundingMode.HALF_UP); // find the average block time
                final BigDecimal averageHashRate =
                        new BigDecimal(lastDifficulty)
                                .divide(averageBlockTime,
                                        4,
                                        RoundingMode.HALF_UP); // use the avg block time to minimize
                                                               // the possibility of mining a block
                                                               // early
                final BigDecimal minerShare =
                        BigDecimal.valueOf(blockCount)
                                .divide(BigDecimal.valueOf(aionBlockCache.size()),
                                        RoundingMode.HALF_UP);
                final BigDecimal minerHashRate = minerShare.multiply(averageHashRate);

                RPCTypes.MinerStats minerStats =
                        new RPCTypes.MinerStats(
                                averageHashRate.toPlainString(),
                                minerHashRate.toPlainString(),
                                minerShare.toPlainString());

                statsHistory.put(aionAddress, minerStats); // cache the result so that it can be reused
                return minerStats;
            } catch (Exception e) {
                throw FailedToComputeMetricsRPCException.INSTANCE;
            }
        } else {
            return this.statsHistory.get(aionAddress);
        }
    }
}
