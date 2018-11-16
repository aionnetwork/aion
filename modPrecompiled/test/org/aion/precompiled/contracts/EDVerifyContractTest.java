/*
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
 *     Centrys
 */

package org.aion.precompiled.contracts;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import org.aion.base.type.Address;
import org.aion.base.type.IExecutionResult;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractFactory;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;
import org.aion.zero.impl.config.CfgAion;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class EDVerifyContractTest {

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
    private long blockNumber = 2000001;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private DataWord blockDifficulty = new DataWord(0x100000000L);

    private DataWord nrgPrice;
    private long nrgLimit;
    private DataWord callValue;
    private byte[] callData;

    private int depth = 0;
    private int kind = ExecutionContext.CREATE;
    private int flags = 0;

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 20000;
        callValue = DataWord.ZERO;
        callData = new byte[0];
        CfgAion.inst().setForkProperties();
    }

    @Test
    public void shouldReturnSuccessTestingWith256() {
        byte[] input = setupInput();
        ExecutionContext ctx = new ExecutionContext(txHash,
            ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
            nrgLimit, callValue,
            callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
            blockDifficulty);
        IPrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        assertNotNull(contract);
        IExecutionResult result = contract.execute(input, 21000L);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.SUCCESS.toInt());
        assertThat(result.getOutput()[0]).isEqualTo(1);
    }

    @Test
    public void emptyInputTest(){
        byte[] input = new byte[128];

        ExecutionContext ctx = new ExecutionContext(txHash,
                ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
                nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty);
        IPrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        assertNotNull(contract);
        IExecutionResult result = contract.execute(input, 21000L);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.SUCCESS.toInt());
        assertThat(result.getOutput()[0]).isEqualTo(0);
    }

    @Test
    public void incorrectInputTest(){
        byte[] input = setupInput();

        input[22] = (byte)((int)(input[32]) -10); // modify sig
        input[33] = (byte)((int)(input[33]) + 4); // modify sig
        input[99] = (byte)((int)(input[33]) - 40); // modify sig

        ExecutionContext ctx = new ExecutionContext(txHash,
                ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
                nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty);
        IPrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        assertNotNull(contract);
        IExecutionResult result = contract.execute(input, 21000L);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.SUCCESS.toInt());
        assertThat(result.getOutput()[0]).isEqualTo(0);
    }

    @Test
    public void shouldFailIfNotEnoughEnergy() {
        nrgPrice = DataWord.ONE;

        byte[] input = setupInput();
        ExecutionContext ctx = new ExecutionContext(txHash,
            ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
            nrgLimit, callValue,
            callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
            blockDifficulty);
        IPrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        IExecutionResult result = contract.execute(input, 2999L);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.OUT_OF_NRG.toInt());
    }

    @Test
    public void invalidInputLengthTest(){
        byte[] input = new byte[129]; // note the length is 129
        input[128] = 0x1;

        ExecutionContext ctx = new ExecutionContext(txHash,
                ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
                nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty);
        IPrecompiledContract contract = new ContractFactory().getPrecompiledContract(ctx, null);

        IExecutionResult result = contract.execute(input, 21000L);

        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.FAILURE.toInt());
    }

    private byte[] setupInput(){
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey ecKey = ECKeyFac.inst().create();
        ecKey = ecKey.fromPrivate(Hex.decode(
                "5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426fff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));

        byte[] pubKey = ecKey.getPubKey();

        byte[] data = "Our first test in AION1234567890".getBytes();

        HashUtil.setType(HashUtil.H256Type.KECCAK_256);
        byte[] hashedMessage = HashUtil.h256(data);

        ISignature signature = ecKey.sign(hashedMessage);

        byte[] input = new byte[128];
        System.arraycopy(hashedMessage, 0, input, 0, 32);
        System.arraycopy(signature.getSignature(), 0, input, 32, 64);
        System.arraycopy(pubKey, 0, input, 96, 32);

        return input;
    }

}


