package org.aion.zero.impl.sync;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import org.aion.mcf.valid.BlockHeaderValidator;
import org.aion.types.ByteArrayWrapper;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.sync.msg.ResponseBlocks;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.A0BlockHeader;
import org.slf4j.Logger;

/**
 * Validates received blocks. If they follow the required rule set they are added to the queue for
 * importing and the map of received hashes.
 *
 * @author Alexandra Roatis
 */
public class TaskValidateAndAddBlocks implements Runnable {

    private int peerId;
    private final String displayId;
    private final ResponseBlocks response;
    private final BlockHeaderValidator<A0BlockHeader> blockHeaderValidator;
    private final BlockingQueue<BlocksWrapper> downloadedBlocks;
    private final Map<ByteArrayWrapper, Long> importedBlockHashes;
    private final Map<ByteArrayWrapper, ByteArrayWrapper> receivedBlockHashes;
    private final Logger log;

    public TaskValidateAndAddBlocks(
            final int peerId,
            final String displayId,
            final ResponseBlocks response,
            final BlockHeaderValidator<A0BlockHeader> blockHeaderValidator,
            final BlockingQueue<BlocksWrapper> downloadedBlocks,
            final Map<ByteArrayWrapper, Long> importedBlockHashes,
            final Map<ByteArrayWrapper, ByteArrayWrapper> receivedBlockHashes,
            final Logger log) {
        this.peerId = peerId;
        this.displayId = displayId;
        this.response = response;
        this.blockHeaderValidator = blockHeaderValidator;
        this.downloadedBlocks = downloadedBlocks;
        this.importedBlockHashes = importedBlockHashes;
        this.receivedBlockHashes = receivedBlockHashes;
        this.log = log;
    }

    @Override
    public void run() {
        // TODO: re-evaluate priority when full functionality is implemented
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
        AionBlock firstBlock = response.getBlocks().get(0);
        Thread.currentThread().setName("check-" + displayId + "-" + firstBlock.getShortHash());

        // log start of operation
        if (log.isDebugEnabled()) {
            log.debug(
                    "Starting block validation from peer={}, start-block={}, batch-size={}.",
                    displayId,
                    firstBlock.getShortHash(),
                    response.getBlocks().size());
        }

        List<AionBlock> filtered = new ArrayList<>();
        List<ByteArrayWrapper> batchHashes = new ArrayList<>();

        A0BlockHeader currentHeader, previousHeader = null;
        for (AionBlock currentBlock : response.getBlocks()) {
            ByteArrayWrapper hash = currentBlock.getHashWrapper();
            if (importedBlockHashes.containsKey(hash)) { // exclude imported
                previousHeader = currentBlock.getHeader();
                continue;
            } else if (receivedBlockHashes.containsKey(hash)) { // exclude known hashes
                previousHeader = currentBlock.getHeader();
                continue;
            }
            currentHeader = currentBlock.getHeader();

            // ignore batch if any invalidated header
            // TODO: we could do partial evaluations here (as per fast sync specs)
            if (!this.blockHeaderValidator.validate(currentHeader, log)) {
                if (log.isDebugEnabled()) {
                    log.debug(
                            "<invalid-header num={} hash={} from peer={}/{}>",
                            currentHeader.getNumber(),
                            currentHeader.getHash(),
                            displayId,
                            peerId);
                }
                if (log.isTraceEnabled()) {
                    log.debug("<invalid-header: {}>", currentHeader.toString());
                }
                return;
            }

            // ignore batch if not ordered correctly
            if (previousHeader != null
                    && (currentHeader.getNumber() != (previousHeader.getNumber() - 1)
                            || !Arrays.equals(
                                    previousHeader.getParentHash(), currentHeader.getHash()))) {
                log.debug(
                        "<inconsistent-block-headers num={}, prev-1={}, p_hash={}, prev={} from peer={}/{}>",
                        currentHeader.getNumber(),
                        previousHeader.getNumber() - 1,
                        ByteUtil.toHexString(previousHeader.getParentHash()),
                        ByteUtil.toHexString(currentHeader.getHash()),
                        displayId,
                        peerId);
                return;
            }

            filtered.add(currentBlock);
            previousHeader = currentHeader;
            batchHashes.add(hash);
        }

        if (!filtered.isEmpty()) {
            ByteArrayWrapper first = batchHashes.get(0);
            downloadedBlocks.offer(new BlocksWrapper(peerId, displayId, filtered));
            batchHashes.forEach(k -> receivedBlockHashes.put(k, first));
        }

        // log end of operation
        if (log.isDebugEnabled()) {
            log.debug(
                    "Completed block validation from peer={}, start-block={}, batch-size={} with filtered-size={}.",
                    displayId,
                    firstBlock.getShortHash(),
                    response.getBlocks().size(),
                    filtered.size());
        }
    }
}
