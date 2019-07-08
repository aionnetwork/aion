package org.aion.mcf.types;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.Transaction;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.tx.TxExecSummary;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.types.AionAddress;
import org.slf4j.Logger;

/** AbstractBlockSummary */
public class AbstractBlockSummary<
        BLK extends Block<?>,
        TXR extends AbstractTxReceipt,
        TXES extends TxExecSummary> {

    protected BLK block;
    protected Map<AionAddress, BigInteger> rewards;
    protected List<TXR> receipts;
    protected List<TXES> summaries;
    protected BigInteger totalDifficulty = BigInteger.ZERO;

    private Logger LOG = AionLoggerFactory.getLogger(LogEnum.CONS.toString());

    public BLK getBlock() {
        return block;
    }

    public List<TXR> getReceipts() {
        return receipts;
    }

    public List<TXES> getSummaries() {
        return summaries;
    }

    protected static byte[] encodeRewards(Map<AionAddress, BigInteger> rewards) {
        return encodeMap(
                rewards,
                new Functional.Function<AionAddress, byte[]>() {
                    @Override
                    public byte[] apply(AionAddress address) {
                        return RLP.encodeElement(address.toByteArray());
                    }
                },
                new Functional.Function<BigInteger, byte[]>() {
                    @Override
                    public byte[] apply(BigInteger reward) {
                        return RLP.encodeBigInteger(reward);
                    }
                });
    }

    protected static Map<AionAddress, BigInteger> decodeRewards(RLPList rewards) {
        return decodeMap(
                rewards,
                new Functional.Function<byte[], AionAddress>() {
                    @Override
                    public AionAddress apply(byte[] bytes) {
                        return new AionAddress(bytes);
                    }
                },
                new Functional.Function<byte[], BigInteger>() {
                    @Override
                    public BigInteger apply(byte[] bytes) {
                        return (bytes == null || bytes.length == 0)
                                ? BigInteger.ZERO
                                : new BigInteger(1, bytes);
                    }
                });
    }

    /**
     * All the mining rewards paid out for this block, including the main block rewards, uncle
     * rewards, and transaction fees.
     */
    public Map<AionAddress, BigInteger> getRewards() {
        return rewards;
    }

    public void setTotalDifficulty(BigInteger totalDifficulty) {
        this.totalDifficulty = totalDifficulty;

        if (LOG.isTraceEnabled()) {
            LOG.trace("The current total difficulty is: {}", totalDifficulty.toString());
        }
    }

    public BigInteger getTotalDifficulty() {
        return totalDifficulty;
    }

    protected static <T> byte[] encodeList(
            List<T> entries, Functional.Function<T, byte[]> encoder) {
        byte[][] result = new byte[entries.size()][];
        for (int i = 0; i < entries.size(); i++) {
            result[i] = encoder.apply(entries.get(i));
        }

        return RLP.encodeList(result);
    }

    protected static <T> List<T> decodeList(RLPList list, Functional.Function<byte[], T> decoder) {
        List<T> result = new ArrayList<>();
        for (RLPElement item : list) {
            result.add(decoder.apply(item.getRLPData()));
        }
        return result;
    }

    protected static <K, V> byte[] encodeMap(
            Map<K, V> map,
            Functional.Function<K, byte[]> keyEncoder,
            Functional.Function<V, byte[]> valueEncoder) {
        byte[][] result = new byte[map.size()][];
        int i = 0;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            byte[] key = keyEncoder.apply(entry.getKey());
            byte[] value = valueEncoder.apply(entry.getValue());
            result[i++] = RLP.encodeList(key, value);
        }
        return RLP.encodeList(result);
    }

    protected static <K, V> Map<K, V> decodeMap(
            RLPList list,
            Functional.Function<byte[], K> keyDecoder,
            Functional.Function<byte[], V> valueDecoder) {
        Map<K, V> result = new HashMap<>();
        for (RLPElement entry : list) {
            K key = keyDecoder.apply(((RLPList) entry).get(0).getRLPData());
            V value = valueDecoder.apply(((RLPList) entry).get(1).getRLPData());
            result.put(key, value);
        }
        return result;
    }
}
