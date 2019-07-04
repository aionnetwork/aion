package org.aion.mcf.types;

import java.util.List;
import org.aion.types.AionAddress;

/**
 * A log that is emitted during the execution of a transaction.
 *
 * <p>The source of an execution log is the {@link AionAddress} of the smart contract in which this
 * log event was triggered.
 *
 * <p>Each log has a list of topics and some data.
 */
public interface IExecutionLog {

    /**
     * Returns the {@link AionAddress} of the smart contract in which this log event was triggered
     * inside.
     *
     * @return The contract whose logic triggered this logging event.
     */
    AionAddress getSourceAddress();

    /**
     * Returns a list of all the topics associated with this log.
     *
     * @return The topics in this log.
     */
    List<byte[]> getTopics();

    /**
     * Returns the data associated with this log.
     *
     * @return The log data.
     */
    byte[] getData();

    /**
     * Returns the {@link IBloomFilter} that is associated with this log.
     *
     * @return The bloom filter for this log.
     */
    IBloomFilter getBloomFilterForLog();

    // TODO: how we handle serialization is still to be determined; likely moved out of api where
    // TODO: possible.
    byte[] getEncoded();
}
