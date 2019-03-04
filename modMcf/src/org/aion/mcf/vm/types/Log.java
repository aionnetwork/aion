package org.aion.mcf.vm.types;

import java.util.ArrayList;
import java.util.List;
import org.aion.types.Address;
import org.aion.crypto.HashUtil;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPItem;
import org.aion.rlp.RLPList;
import org.aion.util.conversions.Hex;
import org.aion.vm.api.interfaces.IBloomFilter;
import org.aion.vm.api.interfaces.IExecutionLog;

/** A log is emitted by the LOGX vm instruction. It's composed of address, topics and data. */
public class Log implements IExecutionLog {

    private Address addr;
    private List<byte[]> topics = new ArrayList<>();
    private byte[] data;

    public Log(byte[] rlp) {
        RLPList params = RLP.decode2(rlp);
        RLPList logInfo = (RLPList) params.get(0);

        RLPItem address = (RLPItem) logInfo.get(0);
        RLPList topics = (RLPList) logInfo.get(1);
        RLPItem data = (RLPItem) logInfo.get(2);

        this.addr = address.getRLPData() != null ? Address.wrap(address.getRLPData()) : null;
        this.data = data.getRLPData() != null ? data.getRLPData() : new byte[] {};

        for (RLPElement topic1 : topics) {
            byte[] topic = topic1.getRLPData();
            this.topics.add(topic);
        }
    }

    public Log(Address address, List<byte[]> topics, byte[] data) {
        this.addr = address;
        this.topics = (topics != null) ? topics : new ArrayList<>();
        this.data = (data != null) ? data : new byte[] {};
    }

    @Override
    public Address getSourceAddress() {
        return addr;
    }

    @Override
    public List<byte[]> getTopics() {
        return topics;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    /* [address, [topic, topic ...] data] */
    @Override
    public byte[] getEncoded() {

        byte[] addressEncoded;
        if (this.addr == null) {
            addressEncoded = RLP.encodeElement(null);
        } else {
            addressEncoded = RLP.encodeElement(this.addr.toBytes());
        }

        byte[][] topicsEncoded = null;
        if (topics != null) {
            topicsEncoded = new byte[topics.size()][];
            int i = 0;
            for (byte[] topic : topics) {
                topicsEncoded[i] = RLP.encodeElement(topic);
                ++i;
            }
        }

        byte[] dataEncoded = RLP.encodeElement(data);
        return RLP.encodeList(addressEncoded, RLP.encodeList(topicsEncoded), dataEncoded);
    }

    @Override
    public IBloomFilter getBloomFilterForLog() {
        Bloom ret = Bloom.create(HashUtil.h256(this.addr.toBytes()));
        for (byte[] topic : topics) {
            ret.or(Bloom.create(HashUtil.h256(topic)));
        }
        return ret;
    }

    @Override
    public String toString() {

        StringBuilder topicsStr = new StringBuilder();
        topicsStr.append("[");

        for (byte[] topic : topics) {
            String topicStr = Hex.toHexString(topic);
            topicsStr.append(topicStr).append(" ");
        }
        topicsStr.append("]");

        return "LogInfo{"
                + "address=0x"
                + this.addr.toString()
                + ", topics="
                + topicsStr
                + ", data="
                + Hex.toHexString(data)
                + '}';
    }
}
