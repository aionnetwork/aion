/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.types;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aion.base.type.AionAddress;
import org.aion.base.type.IBlock;
import org.aion.base.type.ITransaction;
import org.aion.base.type.ITxExecSummary;
import org.aion.base.util.Functional;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.vm.api.interfaces.Address;
import org.slf4j.Logger;

/** AbstractBlockSummary */
public class AbstractBlockSummary<
        BLK extends IBlock<?, ?>,
        TX extends ITransaction,
        TXR extends AbstractTxReceipt<TX>,
        TXES extends ITxExecSummary> {

    protected BLK block;
    protected Map<Address, BigInteger> rewards;
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

    protected static byte[] encodeRewards(Map<Address, BigInteger> rewards) {
        return encodeMap(
                rewards,
                new Functional.Function<Address, byte[]>() {
                    @Override
                    public byte[] apply(Address address) {
                        return RLP.encodeElement(address.toBytes());
                    }
                },
                new Functional.Function<BigInteger, byte[]>() {
                    @Override
                    public byte[] apply(BigInteger reward) {
                        return RLP.encodeBigInteger(reward);
                    }
                });
    }

    protected static Map<Address, BigInteger> decodeRewards(RLPList rewards) {
        return decodeMap(
                rewards,
                new Functional.Function<byte[], Address>() {
                    @Override
                    public Address apply(byte[] bytes) {
                        return AionAddress.wrap(bytes);
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
    public Map<Address, BigInteger> getRewards() {
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
