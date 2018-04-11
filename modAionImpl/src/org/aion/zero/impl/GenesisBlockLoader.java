/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.zero.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AccountState;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.io.ByteStreams;

/**
 * Responsible for loading a genesis file.
 * 
 * @author yao
 *
 */
public class GenesisBlockLoader {

    private static final Pattern isAlphaNumeric = Pattern.compile("^(0x)?[0-9a-fA-F]+$");

    /**
     * Loader function, ported from @chrislol's configuration class, will open a
     * file located at filePath. Load the JSON (not incrementally) and generate
     * a genesis object that defaults back to parameters specified in
     * {@link AionGenesis} if not specified.
     * 
     * Alternatively, if the file cannot be loaded, then the default genesis
     * file is loaded
     * 
     * Makes no assumptions about thread-safety.
     * 
     * @param filePath
     *            filepath to the genesis JSON file
     * @return genesis file
     */
    public static AionGenesis loadJSON(String filePath) throws IOException {
        File genesisFile = new File(filePath);
        if (genesisFile.exists()) {
            try (InputStream is = new FileInputStream(genesisFile)) {
                String json = new String(ByteStreams.toByteArray(is));
                JSONObject mapper = new JSONObject(json);
                AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();

                if (mapper.has("parentHash")) {
                    genesisBuilder.withParentHash(ByteUtil.hexStringToBytes(mapper.getString("parentHash")));
                }

                if (mapper.has("coinbase")) {
                    genesisBuilder.withCoinbase(Address.wrap(mapper.getString("coinbase")));
                }

                if (mapper.has("difficulty")) {
                    String difficulty = mapper.getString("difficulty");

                    if (!isAlphaNumeric.matcher(difficulty).matches())
                        throw new IOException("difficulty must be hex or numerical");

                    if (difficulty.substring(0, 2).equals("0x"))
                        genesisBuilder.withDifficulty(ByteUtil.hexStringToBytes(mapper.getString("difficulty")));
                    else
                        genesisBuilder.withDifficulty((new BigInteger(difficulty)).toByteArray());
                }

                if (mapper.has("timestamp")) {
                    String timestamp = mapper.getString("timestamp");

                    if (!isAlphaNumeric.matcher(timestamp).matches())
                        throw new IOException("timestamp must be hex or numerical");

                    if (timestamp.substring(0, 2).equals("0x"))
                        genesisBuilder.withTimestamp(
                                (new BigInteger(1, ByteUtil.hexStringToBytes(timestamp)).longValueExact()));
                    else
                        genesisBuilder.withTimestamp((new BigInteger(timestamp)).longValueExact());
                }

                if (mapper.has("chainId")) {
                    String chainId = mapper.getString("chainId");

                    if (!isAlphaNumeric.matcher(chainId).matches())
                        throw new IOException("chainId must be hex or numerical");

                    if (chainId.length() > 2 && chainId.substring(0, 2).equals("0x")) {
                        genesisBuilder.withChainId(Integer.parseInt(chainId.substring(2), 16));
                    } else {
                        genesisBuilder.withChainId(Integer.parseInt(chainId));
                    }
                }

                if (mapper.has("energyLimit")) {
                    String extraData = mapper.getString("energyLimit");

                    if (!isAlphaNumeric.matcher(extraData).matches())
                        throw new IOException("energyLimit must be hex or numerical");

                    if (extraData.substring(0, 2).equals("0x")) {
                        genesisBuilder.withEnergyLimit(
                                new BigInteger(1, ByteUtil.hexStringToBytes(extraData)).longValueExact());
                    } else {
                        genesisBuilder.withEnergyLimit(new BigInteger(extraData).longValueExact());
                    }
                }

                if (mapper.has("networkBalanceAllocs")) {
                    JSONObject networkBalanceAllocs = mapper.getJSONObject("networkBalanceAllocs");

                    // this is mapping between an integer (in string format) ->
                    // BigInteger (decimal/string format)
                    for (String key : networkBalanceAllocs.keySet()) {
                        Integer chainId = Integer.valueOf(key);
                        BigInteger value = new BigInteger(networkBalanceAllocs.getJSONObject(key).getString("balance"));
                        genesisBuilder.addNetworkBalance(chainId, value);
                    }
                }

                // load premine accounts
                if (mapper.has("alloc")) {
                    JSONObject accountAllocs = mapper.getJSONObject("alloc");
                    for (String key : accountAllocs.keySet()) {
                        BigInteger balance = new BigInteger(accountAllocs.getJSONObject(key).getString("balance"));
                        AccountState acctState = new AccountState(BigInteger.ZERO, balance);
                        genesisBuilder.addPreminedAccount(Address.wrap(key), acctState);
                    }
                }

                return genesisBuilder.build();
            } catch (IOException | JSONException e) {
                throw new IOException(e);
            }
        } else {
            throw new IOException(String.format("Genesis file not found at %s", filePath));
        }
    }
}
