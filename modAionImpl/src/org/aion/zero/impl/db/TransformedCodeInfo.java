package org.aion.zero.impl.db;

import java.util.HashMap;
import java.util.Map;
import org.aion.base.db.TransformedCodeInfoInterface;
import org.aion.util.types.ByteArrayWrapper;

public final class TransformedCodeInfo implements TransformedCodeInfoInterface {

    // Key for this map is the codeHash, the value is a map which has AVM version as its key and transformed code as its value.
    public Map<ByteArrayWrapper, Map<Integer, byte[]>> transformedCodeMap;

    public TransformedCodeInfo() {
        transformedCodeMap = new HashMap<>();
    }

    public void add(ByteArrayWrapper codeHash, int avmVersion, byte[] transformedCode) {
        Map<Integer, byte[]> versionToTransformedCode = transformedCodeMap.get(codeHash);

        if (versionToTransformedCode == null) {
            versionToTransformedCode = new HashMap<>();
        }
        versionToTransformedCode.put(avmVersion, transformedCode.clone());
        transformedCodeMap.put(codeHash, versionToTransformedCode);
    }

    public byte[] getTransformedCode(ByteArrayWrapper codeHash, int avmVersion) {
        Map<Integer, byte[]> avmVersionMap = transformedCodeMap.get(codeHash);

        if (avmVersionMap == null) return null;

        return avmVersionMap.get(avmVersion);
    }
}