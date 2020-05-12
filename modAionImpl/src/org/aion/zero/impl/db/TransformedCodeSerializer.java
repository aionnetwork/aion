package org.aion.zero.impl.db;

import java.util.HashMap;
import java.util.Map;
import org.aion.db.store.Serializer;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPElement;
import org.aion.rlp.RLPList;
import org.aion.util.types.ByteArrayWrapper;

public class TransformedCodeSerializer {

    private static byte[] encodeInnerMap(Map<Integer, byte[]> code) {
        byte[][] rlpInfo = new byte[code.size()][];
        int i = 0;
        for (Integer vmVersion : code.keySet()) {
            rlpInfo[i++] =
                RLP.encodeList(
                    // encode VM Version
                    RLP.encodeInt(vmVersion),
                    // encode Transformed Code
                    RLP.encodeElement(code.get(vmVersion)));
        }

        // combine 2D-array
        return RLP.encodeList(rlpInfo);
    }

    private static Map<Integer, byte[]> decodeInnerMap(RLPList list) {

        Map<Integer, byte[]> map = new HashMap<>();

        for (RLPElement e : (list)) {
            // validity check pair
            if (!(e instanceof RLPList)) { return null; }

            RLPList pair = (RLPList) e;

            // validity check
            if (pair.size() != 2) { return null; }

            int avmVersion = Byte.toUnsignedInt(pair.get(0).getRLPData()[0]);
            byte[] transformedCode = pair.get(1).getRLPData();

            map.put(avmVersion, transformedCode); // zero (i.e. false) decodes to empty byte array
        }

        return map;
    }


    public static final Serializer<TransformedCodeInfo> RLP_SERIALIZER = new Serializer<>() {

        /**
         * Returns an RLP encoding of the given TransformedCodeInformation object.
         */
        @Override
        public byte[] serialize(TransformedCodeInfo info) {
            byte[][] rlpInfo = new byte[info.transformedCodeMap.size()][];
            int i = 0;
            for (ByteArrayWrapper codeHash : info.transformedCodeMap.keySet()) {
                rlpInfo[i++] =
                    RLP.encodeList(
                        // encode Code Hash
                        RLP.encodeElement(codeHash.toBytes()),
                        // encode the inner map
                        encodeInnerMap(info.transformedCodeMap.get(codeHash)));
            }
            return RLP.encodeList(rlpInfo);
        }

        /**
         * Decodes a TransformedCodeInformation object from the RLP encoding.
         */
        @Override
        public TransformedCodeInfo deserialize(byte[] rlpEncoded) {
            try {
                // validity check
                if (rlpEncoded == null || rlpEncoded.length == 0) { return null; }

                RLPElement decoded = RLP.decode2(rlpEncoded).get(0);

                // validity check
                if (!(decoded instanceof RLPList)) { return null; }

                // create and populate object
                TransformedCodeInfo info = new TransformedCodeInfo();
                RLPList list = (RLPList) decoded;

                for (RLPElement e : list) {
                    // validity check
                    if (!(e instanceof RLPList)) { return null; }

                    RLPList pair = (RLPList) e;

                    // validity check
                    if (pair.size() != 2
                        || !(pair.get(1) instanceof RLPList)
                        || pair.get(0).getRLPData().length != 32) { return null; }

                    Map<Integer, byte[]> current = decodeInnerMap((RLPList) pair.get(1));

                    // validity check
                    if (current == null) { return null; }

                    info.transformedCodeMap
                        .put(ByteArrayWrapper.wrap(pair.get(0).getRLPData()), current);
                }

                return info;
            }
            catch (Exception e) {
                return null;
            }
        }
    };
}
