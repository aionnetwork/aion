package org.aion.base;

import java.util.ArrayList;
import java.util.List;
import org.aion.crypto.HashUtil;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.types.Log;

public class LogUtility {

    public static Bloom createBloomFilterForLog(Log log) {
        Bloom ret = Bloom.create(HashUtil.h256(log.copyOfAddress()));
        for (byte[] topic : log.copyOfTopics()) {
            ret.or(Bloom.create(HashUtil.h256(topic)));
        }
        return ret;
    }

    public static byte[] encodeLog(Log log) {
        byte[] addressEncoded = RLP.encodeElement(log.copyOfAddress());

        List<byte[]> topics = log.copyOfTopics();
        byte[][] topicsEncoded = new byte[topics.size()][];
        for (int i = 0; i < topics.size(); i++) {
            topicsEncoded[i] = RLP.encodeElement(topics.get(i));
        }

        byte[] dataEncoded = RLP.encodeElement(log.copyOfData());
        return RLP.encodeList(addressEncoded, RLP.encodeList(topicsEncoded), dataEncoded);
    }

    public static Log decodeLog(byte[] rlp) {
        RLPList params = RLP.decode2(rlp);
        RLPList logInfo = (RLPList) params.get(0);

        RLPItem encodedAddress = (RLPItem) logInfo.get(0);
        RLPList encodedTopics = (RLPList) logInfo.get(1);
        RLPItem encodedData = (RLPItem) logInfo.get(2);

        byte[] address = encodedAddress.getRLPData(); // Can be null
        byte[] data = encodedData.getRLPData(); // Can be null

        List<byte[]> topics = new ArrayList<>();
        for (RLPElement topic1 : encodedTopics) {
            byte[] topic = topic1.getRLPData();
            topics.add(topic);
        }

        Log ret;
        if (address != null && data != null) {
            if (topics.isEmpty()) {
                ret = Log.dataOnly(address, data);
            } else {
                ret = Log.topicsAndData(address, topics, data);
            }
        } else {
            throw new IllegalArgumentException(
                    "Unable to decode Log because of null "
                            + (address == null ? "address" : "data"));
        }
        return ret;
    }
}
