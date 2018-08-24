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
        byte[] input = setupInput(INPUT_BUFFER_LENGTH);

        IExecutionResult result = contract.execute(input, VALID_NRG_LIMIT);

        assertThat(result.getOutput()).isEqualTo(ecKey.getAddress());
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.SUCCESS.toInt());
    }

    @Test
    public void shouldReturnSuccessAnd0IfSignatureIsNotValid() {
        byte[] input = setupInput(INPUT_BUFFER_LENGTH);
        input[33] = 0;

        IExecutionResult result = contract.execute(input, VALID_NRG_LIMIT);

        assertThat(result.getOutput()).isEqualTo(Address.ZERO_ADDRESS().toBytes());
        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.SUCCESS.toInt());
    }

    @Test
    public void shouldFailureAnd0IfInputIsNotValid() {
        byte[] input = setupInput(127);

        IExecutionResult result = contract.execute(input, VALID_NRG_LIMIT);

        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.INTERNAL_ERROR.toInt());
        assertThat(result.getOutput()).isEqualTo(Address.ZERO_ADDRESS().toBytes());
    }

    @Test
    public void shouldReturnOutOfEnergyAnd0IfNotEnoughEnergy() {
        byte[] input = setupInput(INPUT_BUFFER_LENGTH);

        IExecutionResult result = contract.execute(input, INVALID_NRG_LIMIT);

        assertThat(result.getCode()).isEqualTo(ExecutionResult.ResultCode.OUT_OF_NRG.toInt());
        assertThat(result.getOutput()).isEqualTo(Address.ZERO_ADDRESS().toBytes());
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
        ContractFactory factory = new ContractFactory();
        contract = factory.getPrecompiledContract(ctx, null);

        ecKey = ECKeyFac.inst().create();
        ecKey = ecKey.fromPrivate(Hex.decode("5a90d8e67da5d1dfbf17916ae83bae04ef334f53ce8763932eba2c1116a62426f" +
                "ff4317ae351bda5e4fa24352904a9366d3a89e38d1ffa51498ba9acfbc65724"));

        String rawMessage = "This is a message from outer space";
        String message = "\u0019Aion Signed Message:\n" + rawMessage.length() + rawMessage;
        hashedMessage = HashUtil.keccak256(message.getBytes());
        signature = ecKey.sign(hashedMessage);
    }

    private byte[] setupInput(int length) {
        byte[] input = new byte[length];
        System.arraycopy(hashedMessage, 0, input, 0, 32);
        System.arraycopy(ecKey.getPubKey(), 0, input, 32, 32);
        System.arraycopy(signature.getSignature(), 0, input, 64, (length == 128) ? 64 : 63);
        return input;
    }
}
