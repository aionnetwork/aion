package org.aion.zero.impl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.aion.log.LogEnum;
import org.aion.mcf.core.ImportResult;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.aion.base.AionTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AionBlockLoader {

    private static final Logger logger = LoggerFactory.getLogger(LogEnum.GEN.toString());

    private AionBlockchainImpl blockchain = AionBlockchainImpl.inst();

    private DateFormat df = new SimpleDateFormat("HH:mm:ss.SSSS");

    private void blockWork(AionBlock block) {
        if (block.getNumber() >= blockchain.getBestBlock().getNumber()
                || blockchain.getBlockByHash(block.getHash()) == null) {

            if (block.getNumber() > 0 && !isValid(block.getHeader())) {
                throw new RuntimeException();
            }

            ImportResult result = blockchain.tryToConnect(block);
            System.out.println(
                    df.format(new Date())
                            + " Imported block "
                            + block.getShortDescr()
                            + ": "
                            + result
                            + " (prework: "
                            + exec1.getQueue().size()
                            + ", work: "
                            + exec2.getQueue().size()
                            + ", blocks: "
                            + exec1.getOrderMap().size()
                            + ")");

        } else {

            if (block.getNumber() % 10000 == 0) {
                System.out.println("Skipping block #" + block.getNumber());
            }
        }
    }

    ExecutorPipeline<AionBlock, AionBlock> exec1;
    ExecutorPipeline<AionBlock, ?> exec2;

    public void loadBlocks() {
        exec1 =
                new ExecutorPipeline<>(
                        8,
                        1000,
                        true,
                        b -> {
                            for (AionTransaction tx : b.getTransactionsList()) {
                                tx.getSenderAddress();
                            }
                            return b;
                        },
                        throwable -> logger.error("Unhandled exception: ", throwable));

        exec2 =
                exec1.add(
                        1,
                        1000,
                        block -> {
                            try {
                                blockWork(block);
                            } catch (Exception e) {
                                logger.error("Unhandled exception: ", e);
                            }
                        });

        try {
            exec1.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        blockchain.flush();
    }

    private boolean isValid(A0BlockHeader header) {
        return true;
    }
}
