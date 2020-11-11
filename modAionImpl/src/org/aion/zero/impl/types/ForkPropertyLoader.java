package org.aion.zero.impl.types;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;
import org.aion.util.bytes.ByteUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A loader class for the protocol upgrade settings
 *
 * @author Jay Tseng
 */
public class ForkPropertyLoader {

    private static final Pattern isAlphaNumeric = Pattern.compile("^(0x)?[0-9a-fA-F]+$");
    private static final Pattern isNumeric = Pattern.compile("^?[0-9]+$");

    /**
     * @param filePath the file path to the fork properties JSON file
     * @return the ProtocolUpgrade Settings
     */
    public static ProtocolUpgradeSettings loadJSON(String filePath)
            throws IOException {
        File forkPropertyFile = new File(filePath);
        if (forkPropertyFile.exists()) {
            try (InputStream is = new FileInputStream(forkPropertyFile)) {
                String json = new String(ByteStreams.toByteArray(is));
                JSONObject mapper = new JSONObject(json);

                Properties upgrade = new Properties();
                List<byte[]> rollbackTx = null;
                if (mapper.has("fork")) {
                    JSONArray array = mapper.getJSONArray("fork");
                    for (int i=0 ; i<array.length() ; i++) {
                        JSONObject object = array.getJSONObject(i);
                        Objects.requireNonNull(object);
                        String ver = object.getString("ver");
                        String block = object.getString("block");
                        Objects.requireNonNull(ver);
                        Objects.requireNonNull(block);
                        if (!isNumeric.matcher(block).matches()) {
                            throw new IOException("the block number must be numerical");
                        }
                        upgrade.put("fork" + ver, block);

                        if (ver.equals("1.6")) {
                            rollbackTx = parseRollbackTx(object);
                        }
                    }
                }

                boolean forTest = false;
                if (mapper.has("forTest")) {
                    forTest = mapper.getBoolean("forTest");
                }

                return new ProtocolUpgradeSettings(upgrade, rollbackTx, forTest);
            } catch (IOException | JSONException e) {
                throw new IOException(e);
            }
        } else {
            throw new IOException(String.format("fork property file not found at %s", filePath));
        }
    }

    private static List<byte[]> parseRollbackTx(JSONObject object) throws IOException {
        JSONArray txArray = object.optJSONArray("rollbackTx");
        if (txArray == null) {
            return null;
        }
        List<byte[]> list = new ArrayList<>();
        for (int i=0 ; i<txArray.length() ; i++) {
            String txHash = txArray.getString(i);
            Objects.requireNonNull(txHash);

            if (!isAlphaNumeric.matcher(txHash).matches())
                throw new IOException("the rollbackTx transaction hash must be hex or numerical");

            byte[] hash = ByteUtil.hexStringToBytes(txHash);
            if (hash.length != 32) {
                throw new IOException("the rollbackTx transaction hash must be 32 bytes");
            }

            list.add(hash);
        }

        return list;
    }
}
