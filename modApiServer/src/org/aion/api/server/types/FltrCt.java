package org.aion.api.server.types;

import java.util.Arrays;
import java.util.List;
import org.aion.base.type.Address;

/** @author chris */
// NOTE: only used by java api
public class FltrCt extends Fltr {

    private byte[] contractAddress;

    private String fromBlock;

    private String toBlock;

    private List<String> topics;

    private List<byte[]> addresses;

    private long expireTime;

    public FltrCt(
            byte[] contractAddress,
            String toBlock,
            String fromBlock,
            List<String> topics,
            List<byte[]> addrs,
            long time) {
        super(Type.EVENT);
        this.contractAddress = contractAddress;
        this.toBlock = toBlock;
        this.setFromBlock(fromBlock);
        this.topics = topics;
        this.setAddresses(addrs);
        this.setExpireTime(time);
    }

    public String getToBlock() {
        return this.toBlock;
    }

    public List<String> getTopics() {
        return this.topics;
    }

    public byte[] getContractAddr() {
        return this.contractAddress;
    }

    /** verify if current log filter is for specific contract address */
    public boolean isFor(Address contractAddress, String topic) {
        // topic = "0x" + topic;
        if (!Arrays.equals(this.contractAddress, contractAddress.toBytes())) {
            return false;
        }
        return this.topics.stream().filter(s -> s.equals(topic)).findFirst().isPresent();
    }

    public String getFromBlock() {
        return fromBlock;
    }

    public void setFromBlock(String fromBlock) {
        this.fromBlock = fromBlock;
    }

    public List<byte[]> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<byte[]> addresses) {
        this.addresses = addresses;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }
}
