package org.aion.precompiled.contracts;

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
import org.aion.vm.TransactionResult;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import static com.google.common.truth.Truth.assertThat;

public class EDVerifyContractTest {

    private static final long VALID_NRG_LIMIT = 21000L;
    private static final long INVALID_NRG_LIMIT = 10000L;
    private static final int INPUT_BUFFER_LENGTH = 128;

    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private DataWord blockDifficulty = new DataWord(0x100000000L);

    private int kind = ExecutionContext.CREATE;

    private ExecutionContext ctx;

    private ECKey ecKey;
    private byte[] hashedMessage;
    private ISignature signature;
    private IPrecompiledContract contract;

    @BeforeClass
    public static void beforeClass() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
    }

    @Before
    public void setup() {
        beforeEach();
    }

    @Test
    public void shouldReturnSuccessAnd1IfTheSignatureIsValid() {
<<<<<<< HEAD
        byte[] input = setupInput(INPUT_BUFFER_LENGTH);

=======
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey ecKey = ECKeyFac.inst().create();
        ecKey = ecKey.fromPrivate(Hex.decode("5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426fff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));

        byte[] pubKey = ecKey.getPubKey();
        String rawMessage = "This is a message from outer space";
        String message = "\u0019Aion Signed Message:\n" + rawMessage.length() + rawMessage;
        byte[] hashedMessage = HashUtil.keccak256(message.getBytes());

        ISignature signature = ecKey.sign(hashedMessage);

        byte[] input = new byte[128];
        System.arraycopy(hashedMessage, 0, input, 0, 32);
        System.arraycopy(signature.getSignature(), 0, input, 32, 64);
        System.arraycopy(pubKey, 0, input, 96, 32);

        ExecutionContext ctx = new ExecutionContext(txHash, ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
                nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty);
>>>>>>> 2147fee0... Add more test cases and document precompiled contract
        IPrecompiledContract contract = ContractFactory.getPrecompiledContract(ctx, null);

        IExecutionResult result = contract.execute(input, VALID_NRG_LIMIT);
        assertThat(result.getOutput()[0]).isEqualTo(1);
    }

    @Test
    public void shouldReturnSuccessAnd0IfSignatureIsNotValid() {
<<<<<<< HEAD
        byte[] input = setupInput(INPUT_BUFFER_LENGTH);
        input[33] = 0;

        IPrecompiledContract contract = ContractFactory.getPrecompiledContract(ctx, null);

        IExecutionResult result = contract.execute(input, VALID_NRG_LIMIT);
        assertThat(result.getOutput()[0]).isEqualTo(0);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.SUCCESS.toInt());
    }

    @Test
    public void shouldFailureAnd0IfInputIsNotValid() {
        byte[] input = setupInput(127);

        IPrecompiledContract contract = ContractFactory.getPrecompiledContract(ctx, null);
=======
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey ecKey = ECKeyFac.inst().create();
        ecKey = ecKey.fromPrivate(Hex.decode("5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426fff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));

        byte[] pubKey = ecKey.getPubKey();
        String rawMessage = "This is a message from outer space";
        String message = "\u0019Aion Signed Message:\n" + rawMessage.length() + rawMessage;
        byte[] hashedMessage = HashUtil.keccak256(message.getBytes());

        ISignature signature = ecKey.sign(hashedMessage);

        byte[] input = new byte[128];
        System.arraycopy(hashedMessage, 0, input, 0, 32);
        byte[] alteredSig = signature.getSignature();
        alteredSig[0] = 1;
        System.arraycopy(alteredSig, 0, input, 32, 64);
        System.arraycopy(pubKey, 0, input, 96, 32);

        ExecutionContext ctx = new ExecutionContext(txHash, ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
                nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty);
        IPrecompiledContract contract = ContractFactory.getPrecompiledContract(ctx, null);

        IExecutionResult result = contract.execute(input, 21000L);
        assertThat(result.getOutput()[0]).isEqualTo(0);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.SUCCESS.toInt());
    }

    @Test
    public void shouldFailureAnd0IfInputIsNotValid() {
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey ecKey = ECKeyFac.inst().create();
        ecKey = ecKey.fromPrivate(Hex.decode("5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426fff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));

        byte[] pubKey = ecKey.getPubKey();
        String rawMessage = "This is a message from outer space";
        String message = "\u0019Aion Signed Message:\n" + rawMessage.length() + rawMessage;
        byte[] hashedMessage = HashUtil.keccak256(message.getBytes());

        ISignature signature = ecKey.sign(hashedMessage);

        byte[] input = new byte[127];
        System.arraycopy(hashedMessage, 0, input, 0, 32);
        System.arraycopy(signature.getSignature(), 0, input, 32, 63);
        System.arraycopy(pubKey, 0, input, 95, 32);

        ExecutionContext ctx = new ExecutionContext(txHash, ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
                nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty);
        IPrecompiledContract contract = ContractFactory.getPrecompiledContract(ctx, null);

        IExecutionResult result = contract.execute(input, 21000L);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.INTERNAL_ERROR.toInt());
        assertThat(result.getOutput()[0]).isEqualTo(0);
    }

    @Test
    public void shouldReturnOutOfEnergyAnd0IfNotEnoughEnergy() {
        nrgPrice = DataWord.ONE;
        ECKeyFac.setType(ECKeyFac.ECKeyType.ED25519);
        ECKey ecKey = ECKeyFac.inst().create();
        ecKey = ecKey.fromPrivate(Hex.decode("5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426fff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));

        byte[] pubKey = ecKey.getPubKey();
        String rawMessage = "This is a message from outer space";
        String message = "\u0019Aion Signed Message:\n" + rawMessage.length() + rawMessage;
        byte[] hashedMessage = HashUtil.keccak256(message.getBytes());
>>>>>>> 2147fee0... Add more test cases and document precompiled contract

        IExecutionResult result = contract.execute(input, VALID_NRG_LIMIT);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.INTERNAL_ERROR.toInt());
        assertThat(result.getOutput()).isEqualTo(Address.ZERO_ADDRESS().toBytes());
    }

    @Test
    public void shouldReturnOutOfEnergyAnd0IfNotEnoughEnergy() {
        byte[] input = setupInput(INPUT_BUFFER_LENGTH);

        IPrecompiledContract contract = ContractFactory.getPrecompiledContract(ctx, null);

        IExecutionResult result = contract.execute(input, INVALID_NRG_LIMIT);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.OUT_OF_NRG.toInt());
        assertThat(result.getOutput()[0]).isEqualTo(0);
    }

    private void beforeEach() {
        DataWord nrgPrice = DataWord.ONE;
        long nrgLimit = 20000;
        DataWord callValue = DataWord.ZERO;
        byte[] callData = new byte[0];

        long blockNumber = 1;
        long blockNrgLimit = 5000000;
        int depth = 0;
        int flags = 0;
        ctx = new ExecutionContext(txHash, ContractFactory.getEdVerifyContractAddress(), origin, caller, nrgPrice,
                nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty);

<<<<<<< HEAD
        ecKey = ECKeyFac.inst().create();
        ecKey = ecKey.fromPrivate(Hex.decode("5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426f" +
                "ff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));

        String rawMessage = "This is a message from outer space";
        String message = "\u0019Aion Signed Message:\n" + rawMessage.length() + rawMessage;
        hashedMessage = HashUtil.keccak256(message.getBytes());
        signature = ecKey.sign(hashedMessage);
=======
        IExecutionResult result = contract.execute(input, 10000L);
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.OUT_OF_NRG.toInt());
        assertThat(result.getOutput()[0]).isEqualTo(0);
>>>>>>> 2147fee0... Add more test cases and document precompiled contract
    }

    private byte[] setupInput(int length) {
        byte[] input = new byte[length];
        System.arraycopy(hashedMessage, 0, input, 0, 32);
        System.arraycopy(signature.getSignature(), 0, input, 32, 64);
        System.arraycopy(ecKey.getPubKey(), 0, input, 96, (length == 128) ? 32 : 31);
        return input;
    }
}
