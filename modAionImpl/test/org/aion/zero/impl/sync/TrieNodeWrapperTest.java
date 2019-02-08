package org.aion.zero.impl.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.aion.zero.impl.sync.DatabaseType.STATE;
import static org.aion.zero.impl.sync.msg.RequestTrieDataTest.nodeKey;
import static org.aion.zero.impl.sync.msg.ResponseTrieDataTest.leafValue;

import org.aion.rlp.RLP;
import org.aion.zero.impl.sync.msg.ResponseTrieData;
import org.junit.Test;

/**
 * Unit tests for {@link TrieNodeWrapper}.
 *
 * @author Alexandra Roatis
 */
public class TrieNodeWrapperTest {
    private static final int peerId = Integer.MAX_VALUE;
    private static final String displayId = "abcdef";

    @Test
    public void testConstructor() {
        byte[] encoding =
                RLP.encodeList(
                        RLP.encodeElement(nodeKey),
                        RLP.encodeElement(leafValue),
                        RLP.encodeList(new byte[0]),
                        RLP.encodeString(STATE.toString()));
        ResponseTrieData response = ResponseTrieData.decode(encoding);

        TrieNodeWrapper node = new TrieNodeWrapper(peerId, displayId, response);

        assertThat(node.getPeerId()).isEqualTo(peerId);
        assertThat(node.getDisplayId()).isEqualTo(displayId);
        assertThat(node.getNodeKey()).isEqualTo(response.getNodeKey());
        assertThat(node.getNodeValue()).isEqualTo(response.getNodeValue());
        assertThat(node.getReferencedNodes()).isEqualTo(response.getReferencedNodes());
        assertThat(node.getDbType()).isEqualTo(response.getDbType());
    }
}
