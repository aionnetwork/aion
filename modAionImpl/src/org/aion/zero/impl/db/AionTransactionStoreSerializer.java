package org.aion.zero.impl.db;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.aion.db.store.Serializer;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.SharedRLPList;
import org.aion.util.types.ByteArrayWrapper;
import org.aion.zero.impl.types.AionTxInfo;
import org.slf4j.Logger;

public class AionTransactionStoreSerializer {
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.TX.toString());

    public static final Serializer<Map<ByteArrayWrapper, AionTxInfo>> serializer =
            new Serializer<>() {
                @Override
                public byte[] serialize(Map<ByteArrayWrapper, AionTxInfo> object) {
                    byte[][] txsRlp = new byte[object.size()][];
                    int i = 0;
                    for (AionTxInfo info : object.values()) {
                        txsRlp[i] = info.getEncoded();
                        i++;
                    }
                    return RLP.encodeList(txsRlp);
                }

                @Override
                public Map<ByteArrayWrapper, AionTxInfo> deserialize(byte[] stream) {
                    try {
                        SharedRLPList params = RLP.decode2SharedList(stream);
                        SharedRLPList infoList = (SharedRLPList) params.get(0);
                        Map<ByteArrayWrapper, AionTxInfo> ret = new HashMap<>();
                        for (org.aion.rlp.RLPElement rlpElement : infoList) {
                            AionTxInfo info = AionTxInfo
                                .newInstanceFromEncoding((SharedRLPList) rlpElement);
                            ret.put(Objects.requireNonNull(info).blockHash, info);
                        }
                        return ret;
                    } catch (Exception e) {
                        LOG.error("The given RLP encoding is not a valid list of AionTxInfo objects.", e);
                        return null;
                    }
                }
            };
}
