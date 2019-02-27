package org.aion.zero.impl.sync.msg;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.List;
import junitparams.JUnitParamsRunner;
import org.aion.p2p.Ver;
import org.aion.rlp.RLP;
import org.aion.util.TestResources;
import org.aion.zero.impl.sync.Act;
import org.aion.zero.impl.types.AionBlock;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link ResponseBlocks} messages.
 *
 * @author Alexandra Roatis
 */
@RunWith(JUnitParamsRunner.class)
public class ResponseBlocksTest {

    @Test
    public void testDecode_nullMessage() {
        assertThat(ResponseBlocks.decode(null)).isNull();
    }

    @Test
    public void testDecode_emptyMessage() {
        assertThat(ResponseBlocks.decode(new byte[0])).isNull();
    }

    @Test
    public void testDecode_notAList() {
        byte[] encoding = RLP.encodeElement(new byte[] {1, 2, 3});
        assertThat(ResponseBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testDecode_notBlocks() {
        byte[] encoding = RLP.encodeList(RLP.encodeElement(new byte[] {1, 2, 3}));
        assertThat(ResponseBlocks.decode(encoding)).isNull();
    }

    @Test
    public void testHeader_newObject() {
        ResponseBlocks message = new ResponseBlocks(TestResources.consecutiveBlocks(1));
        // check message header
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);
    }

    @Test
    public void testHeader_decode() {
        byte[] encoding = RLP.encodeList(TestResources.consecutiveBlocks(1).get(0).getEncoded());
        ResponseBlocks message = ResponseBlocks.decode(encoding);
        // check message header
        assertThat(message).isNotNull();
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);
    }

    @Test
    public void testEncodeDecode_emptyList() {
        List<AionBlock> blocks = Collections.emptyList();

        // encode
        ResponseBlocks message = new ResponseBlocks(blocks);
        assertThat(message.getBlocks()).isEqualTo(blocks);
        byte[] encoding = message.encode();

        // decode
        ResponseBlocks decoded = ResponseBlocks.decode(encoding);
        assertThat(decoded).isNotNull();
        assertThat(decoded.getBlocks()).containsExactly(blocks.toArray());

        // equals & hashCode
        assertThat(message).isEqualTo(decoded);
        assertThat(message.hashCode()).isEqualTo(decoded.hashCode());
    }

    @Test
    public void testEncodeDecode_singleElement() {
        List<AionBlock> blocks = TestResources.consecutiveBlocks(1);

        // encode
        ResponseBlocks message = new ResponseBlocks(blocks);
        assertThat(message.getBlocks()).isEqualTo(blocks);
        byte[] encoding = message.encode();

        // decode
        ResponseBlocks decoded = ResponseBlocks.decode(encoding);
        assertThat(decoded).isNotNull();
        assertThat(decoded.getBlocks()).containsExactly(blocks.toArray());

        // equals & hashCode
        assertThat(message).isEqualTo(decoded);
        assertThat(message.hashCode()).isEqualTo(decoded.hashCode());
    }

    @Test
    public void testEncodeDecode_multipleElements() {
        List<AionBlock> blocks = TestResources.consecutiveBlocks(10);

        // encode
        ResponseBlocks message = new ResponseBlocks(blocks);
        assertThat(message.getBlocks()).isEqualTo(blocks);
        byte[] encoding = message.encode();

        // decode
        ResponseBlocks decoded = ResponseBlocks.decode(encoding);
        assertThat(decoded).isNotNull();
        assertThat(decoded.getBlocks()).containsExactly(blocks.toArray());

        // equals & hashCode
        assertThat(message).isEqualTo(decoded);
        assertThat(message.hashCode()).isEqualTo(decoded.hashCode());
    }
}
