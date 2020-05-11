package org.aion.api.server.rpc3;

import static org.aion.mcf.blockchain.BlockHeader.BlockSealType.SEAL_POS_BLOCK;
import static org.aion.mcf.blockchain.BlockHeader.BlockSealType.SEAL_POW_BLOCK;

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
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.rpc.errors.RPCExceptions.FailedToComputeMetricsRPCException;
import org.aion.rpc.types.RPCTypes;
import org.aion.types.AionAddress;
import org.apache.commons.collections4.map.LRUMap;
import org.slf4j.Logger;

public class MinerStatisticsCalculator {

    private final ChainHolder chainHolder;
    private final int size;
    private final Deque<Block> aionBlockCache;
    private final Map<AionAddress, RPCTypes.MinerStats> statsHistory;
    private final int blockTimeCount;
    private static final Logger logger= AionLoggerFactory.getLogger(LogEnum.API.name());

    MinerStatisticsCalculator(ChainHolder chainHolder, int size, int blockTimeCount) {
        this.chainHolder = chainHolder;
        this.size = size;
        aionBlockCache = new LinkedBlockingDeque<>(size * 2); // will always store elements in the reverse
        // of natural order, ie. n-1th element>nth element
        this.blockTimeCount = blockTimeCount;
        statsHistory = Collections.synchronizedMap(new LRUMap<>(size * 2));
    }

    private boolean update() {
        Block block = chainHolder.getBestPOWBlock();
        if (aionBlockCache.isEmpty()) {//lazy population of the block history list
            int i = 1;
            aionBlockCache.addFirst(block);
            // Update latest (size) PoW blocks and (size) PoS blocks
            while (i < (size * 2)) {
                block = chainHolder.getBlockByHash(block.getHeader().getParentHash());
                aionBlockCache.addLast(block);
                i++;
            }
            return true;
        } else if (!Arrays.equals(aionBlockCache.peek().getHash(), block.getHash())) {
            statsHistory.clear();
            Deque<Block> blockStack = new LinkedList<>(); // We're using a stack so that
                                                          // the ordering of the block history can be enforced
            blockStack.push(block);
            aionBlockCache.removeLast();

            int i = 1; // we start at 1 because the stack contains 1 element
            //noinspection ConstantConditions
            while (i < (size * 2)
                    && block.getNumber() > 2
                    && !aionBlockCache.isEmpty()
                    && !Arrays.equals(
                            block.getHeader().getParentHash(),
                            aionBlockCache.peekFirst().getHash())) {
                block = chainHolder.getBlockByHash(block.getHeader().getParentHash());
                blockStack.push(block);
                aionBlockCache.removeLast();
                i++;
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
            long totalBlockTime = 0;
            long countedBlockTime = 0;
            long previousBlockTimestamp = -1;
            long blockCount = 0;
            BigInteger lastDifficulty = BigInteger.ZERO;
            try {
                int i = 0;
                Iterator<Block> iterator = aionBlockCache.iterator();

                while (iterator.hasNext()) {
                    Block block = iterator.next();

                    // only accumulate block times over the last this.blockTimeCount blocks
                    if (i <= this.blockTimeCount) {
                        if (block.getHeader().getSealType().equals(SEAL_POS_BLOCK) && previousBlockTimestamp != -1) {
                            // accumulate the block time
                            totalBlockTime += previousBlockTimestamp - block.getTimestamp();
                            countedBlockTime++;
                        }

                        if (block.getHeader().getSealType().equals(SEAL_POW_BLOCK)) {
                            previousBlockTimestamp = block.getTimestamp();
                        }
                    }

                    if (block.getHeader().getSealType().equals(SEAL_POW_BLOCK)) {
                        // Note latest PoW block's difficulty
                        if (lastDifficulty == BigInteger.ZERO) {
                            lastDifficulty = block.getDifficultyBI();
                        }
                        
                        // Count PoW blocks mined by this miner
                        if (block.getCoinbase().equals(aionAddress)) {
                            blockCount++;
                        }

                        // Increment PoW index
                        i++;
                    }
                }

                //noinspection ConstantConditions
                BigDecimal averageBlockTime = BigDecimal.ZERO;
                if (countedBlockTime > 0) {
                    averageBlockTime = BigDecimal.valueOf(totalBlockTime)
                        .divide(BigDecimal.valueOf(countedBlockTime), 4, RoundingMode.HALF_UP); // find the average block time
                }
                       
                BigDecimal averageHashRate = BigDecimal.ZERO;
                if (averageBlockTime != BigDecimal.ZERO) {
                    averageHashRate = new BigDecimal(lastDifficulty)
                        .divide(averageBlockTime, 4, RoundingMode.HALF_UP); // use the avg block time to minimize
                                                                            // the possibility of mining a block
                                                                            // early
                }
                        
                BigDecimal minerShare = BigDecimal.ZERO;
                if (i > 0) {
                    minerShare = BigDecimal.valueOf(blockCount)
                        .divide(BigDecimal.valueOf(i), 4, RoundingMode.HALF_UP);
                }
                        
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
