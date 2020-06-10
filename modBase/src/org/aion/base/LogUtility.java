package org.aion.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.aion.crypto.HashUtil;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.rlp.SharedRLPList;
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

        SharedRLPList decodedTxList = RLP.decode2SharedList(rlp);

        RLPElement element = decodedTxList.get(0);
        if (!element.isList()) {
            throw new IllegalArgumentException(
                    "The rlp decode error, the decoded item should be a list");
        }
        SharedRLPList logInfo = (SharedRLPList) element;

        byte[] address = logInfo.get(0).getRLPData(); // Can be null
        byte[] data = logInfo.get(2).getRLPData(); // Can be null

        SharedRLPList encodedTopics = (SharedRLPList) logInfo.get(1);
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

    public static Log decodeLog(SharedRLPList sharedRLPList) {
        Objects.requireNonNull(sharedRLPList);

        byte[] address = sharedRLPList.get(0).getRLPData(); // Can be null
        byte[] data = sharedRLPList.get(2).getRLPData(); // Can be null

        SharedRLPList encodedTopics = (SharedRLPList) sharedRLPList.get(1);
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
