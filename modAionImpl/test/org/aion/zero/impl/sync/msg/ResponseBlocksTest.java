package org.aion.zero.impl.sync.msg;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collections;
import java.util.List;
import junitparams.JUnitParamsRunner;
import org.aion.p2p.Ver;
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
    public void testEncodeDecode_emptyList() {
        List<AionBlock> blocks = Collections.emptyList();

        // encode
        ResponseBlocks message = new ResponseBlocks(blocks);

        assertThat(message.getBlocks()).isEqualTo(blocks);

        // check message header
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);

        byte[] encoding = message.encode();

        // decode
        ResponseBlocks decoded = ResponseBlocks.decode(encoding);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getBlocks()).containsExactly(blocks.toArray());

        // check message header
        assertThat(decoded.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(decoded.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);
    }

    @Test
    public void testEncodeDecode_singleElement() {
        List<AionBlock> blocks = TestResources.consecutiveBlocks(1);

        // encode
        ResponseBlocks message = new ResponseBlocks(blocks);

        assertThat(message.getBlocks()).isEqualTo(blocks);

        // check message header
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);

        byte[] encoding = message.encode();

        // decode
        ResponseBlocks decoded = ResponseBlocks.decode(encoding);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getBlocks()).containsExactly(blocks.toArray());

        // check message header
        assertThat(decoded.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(decoded.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);
    }

    @Test
    public void testEncodeDecode_multipleElements() {
        List<AionBlock> blocks = TestResources.consecutiveBlocks(10);

        // encode
        ResponseBlocks message = new ResponseBlocks(blocks);

        assertThat(message.getBlocks()).isEqualTo(blocks);

        // check message header
        assertThat(message.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(message.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);

        byte[] encoding = message.encode();

        // decode
        ResponseBlocks decoded = ResponseBlocks.decode(encoding);

        assertThat(decoded).isNotNull();
        assertThat(decoded.getBlocks()).containsExactly(blocks.toArray());

        // check message header
        assertThat(decoded.getHeader().getVer()).isEqualTo(Ver.V1);
        assertThat(decoded.getHeader().getAction()).isEqualTo(Act.RESPONSE_BLOCKS);
    }
}
