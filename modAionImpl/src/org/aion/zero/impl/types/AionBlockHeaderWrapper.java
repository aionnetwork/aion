package org.aion.zero.impl.types;

import java.util.List;
import org.aion.mcf.types.AbstractBlockHeaderWrapper;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.zero.types.A0BlockHeader;

public class AionBlockHeaderWrapper extends AbstractBlockHeaderWrapper<A0BlockHeader> {

    public AionBlockHeaderWrapper(byte[] bytes) {
        parse(bytes);
    }

    public AionBlockHeaderWrapper(A0BlockHeader header, byte[] nodeId) {
        super(header, nodeId);
    }

    protected void parse(byte[] bytes) {
        List<RLPElement> params = RLP.decode2(bytes);
        List<RLPElement> wrapper = (RLPList) params.get(0);

        byte[] headerBytes = wrapper.get(0).getRLPData();
        this.header = new A0BlockHeader(headerBytes);
        this.nodeId = wrapper.get(1).getRLPData();
    }
}
