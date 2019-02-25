package org.aion.zero.impl.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.mcf.ds.Serializer;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.types.AionTxInfo;

public class AionTransactionStoreSerializer {
    public static final Serializer<List<AionTxInfo>, byte[]> serializer =
            new Serializer<List<AionTxInfo>, byte[]>() {
                @Override
                public byte[] serialize(List<AionTxInfo> object) {
                    byte[][] txsRlp = new byte[object.size()][];
                    for (int i = 0; i < txsRlp.length; i++) {
                        txsRlp[i] = object.get(i).getEncoded();
                    }
                    return RLP.encodeList(txsRlp);
                }

                @Override
                public List<AionTxInfo> deserialize(byte[] stream) {
                    try {
                        RLPList params = RLP.decode2(stream);
                        RLPList infoList = (RLPList) params.get(0);
                        List<AionTxInfo> ret = new ArrayList<>();
                        for (int i = 0; i < infoList.size(); i++) {
                            ret.add(new AionTxInfo(infoList.get(i).getRLPData()));
                        }
                        return ret;
                    } catch (Exception e) {
                        // fallback to previous DB version
                        e.printStackTrace();
                        return Collections.singletonList(new AionTxInfo(stream));
                    }
                }
            };
}
