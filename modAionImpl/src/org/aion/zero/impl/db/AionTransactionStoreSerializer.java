package org.aion.zero.impl.db;

import java.util.HashMap;
import java.util.Map;
import org.aion.db.store.Serializer;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
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
                        RLPList params = RLP.decode2(stream);
                        RLPList infoList = (RLPList) params.get(0);
                        Map<ByteArrayWrapper, AionTxInfo> ret = new HashMap<>();
                        for (int i = 0; i < infoList.size(); i++) {
                            AionTxInfo info = AionTxInfo.newInstanceFromEncoding(infoList.get(i).getRLPData());
                            ret.put(info.blockHash, info);
                        }
                        return ret;
                    } catch (Exception e) {
                        LOG.error("The given RLP encoding is not a valid list of AionTxInfo objects.", e);
                        return null;
                    }
                }
            };
}
