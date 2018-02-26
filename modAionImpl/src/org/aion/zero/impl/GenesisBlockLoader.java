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
    public static AionGenesis loadJSON(String filePath) {
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
                    if (difficulty.substring(0, 2).equals("0x"))
                        genesisBuilder.withDifficulty(ByteUtil.hexStringToBytes(mapper.getString("difficulty")));
                    else
                        genesisBuilder.withDifficulty((new BigInteger(difficulty)).toByteArray());
                }

                if (mapper.has("timestamp")) {
                    String timestamp = mapper.getString("timestamp");
                    if (timestamp.substring(0, 2).equals("0x"))
                        genesisBuilder.withTimestamp(
                                (new BigInteger(1, ByteUtil.hexStringToBytes(timestamp)).longValueExact()));
                    else
                        genesisBuilder.withTimestamp((new BigInteger(timestamp)).longValueExact());
                }

                if (mapper.has("extraData")) {
                    String extraData = mapper.getString("extraData");
                    if (extraData.substring(0, 2).equals("0x")) {
                        genesisBuilder.withExtraData(ByteUtil.hexStringToBytes(extraData));
                    } else {
                        genesisBuilder.withExtraData(extraData.getBytes());
                    }
                }

                if (mapper.has("energyLimit")) {
                    String extraData = mapper.getString("energyLimit");
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
                System.out
                        .println(String.format("AION genesis format exception at %s, loading from defaults", filePath));
                AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();
                return genesisBuilder.build();
            }
        } else {
            System.out.println(String.format("AION genesis not found at %s, loading from defaults", filePath));
            AionGenesis.Builder genesisBuilder = new AionGenesis.Builder();
            return genesisBuilder.build();
        }
    }
}
