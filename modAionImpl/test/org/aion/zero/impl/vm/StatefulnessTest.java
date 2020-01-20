package org.aion.zero.impl.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.avm.stub.IContractFactory.AvmContract;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.crypto.AddressSpecs;
import org.aion.crypto.ECKey;
import org.aion.mcf.blockchain.Block;
import org.aion.zero.impl.core.ImportResult;
import org.aion.base.TransactionTypeRule;
import org.aion.types.AionAddress;
import org.aion.zero.impl.blockchain.StandaloneBlockchain;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.impl.types.AionBlockSummary;
import org.aion.base.AionTxReceipt;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// These tests are ignored for now because in order for them to pass we need the clock drift buffer
// time to be set to 2 seconds instead of 1. We still have to figure out how we are going to handle
// this... You can make this change locally to verify these tests pass.
@RunWith(Parameterized.class)
public class StatefulnessTest {
    private static TestResourceProvider resourceProvider;
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private AionAddress deployer;
    private long energyPrice = 10_000_000_000L;

    private byte txType;

    @Parameters
    public static Object[] data() {
        return new Object[] {TransactionTypes.DEFAULT, TransactionTypes.AVM_CREATE_CODE};
    }

    public StatefulnessTest(byte _txType) {
        txType = _txType;
    }

    @BeforeClass
    public static void setupAvm() throws Exception {
        resourceProvider = TestResourceProvider.initializeAndCreateNewProvider(AvmPathManager.getPathOfProjectRootDirectory());

        AvmTestConfig.supportOnlyAvmVersion1();
    }

    @AfterClass
    public static void tearDownAvm() throws Exception {
        AvmTestConfig.clearConfigurations();
        resourceProvider.close();
    }

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle =
                new StandaloneBlockchain.Builder()
                        .withDefaultAccounts()
                        .withValidatorConfiguration("simple")
                        .withAvmEnabled()
                        .build();
        this.blockchain = bundle.bc;
        this.deployerKey = bundle.privateKeys.get(0);
        this.deployer = new AionAddress(this.deployerKey.getAddress());
        TransactionTypeRule.allowAVMContractTransaction();
    }

    @After
    public void tearDown() {
        this.blockchain = null;
        this.deployerKey = null;
    }

    @Test
    public void testDeployContract() {
        AionTxReceipt receipt = deployContract(AvmVersion.VERSION_1);

        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            // Check the contract has the Avm prefix, and deployment succeeded.

            assertEquals(AddressSpecs.A0_IDENTIFIER, receipt.getTransactionOutput()[0]);
            assertTrue(receipt.isSuccessful());

        } else if (txType == TransactionTypes.DEFAULT) {
            assertEquals(0, receipt.getTransactionOutput().length);
            // FIXME: is the FVM behavior correct?
            assertTrue(receipt.isSuccessful());
        }
    }

    @Test
    public void testStateOfActorsAfterDeployment() {
        BigInteger deployerBalance = getBalance(this.deployer);
        BigInteger deployerNonce = getNonce(this.deployer);

        AionTxReceipt receipt = deployContract(AvmVersion.VERSION_1);

        // Check the contract has the Avm prefix, and deployment succeeded, and grab the address.
        BigInteger contractBalance = BigInteger.ZERO;
        BigInteger contractNonce = BigInteger.ZERO;
        if (txType == TransactionTypes.AVM_CREATE_CODE) {
            assertEquals(AddressSpecs.A0_IDENTIFIER, receipt.getTransactionOutput()[0]);
            AionAddress contract = new AionAddress(receipt.getTransactionOutput());
            contractBalance = getBalance(contract);
            contractNonce = this.blockchain.getRepository().getNonce(contract);
        }
        assertTrue(receipt.isSuccessful());

        BigInteger deployerBalanceAfterDeployment = getBalance(this.deployer);
        BigInteger deployerNonceAfterDeployment = getNonce(this.deployer);

        BigInteger deploymentEnergyCost =
                BigInteger.valueOf(receipt.getEnergyUsed())
                        .multiply(BigInteger.valueOf(this.energyPrice));

        // Check that balances and nonce are in agreement after the deployment.
        assertEquals(
                deployerBalance.subtract(deploymentEnergyCost), deployerBalanceAfterDeployment);
        assertEquals(deployerNonce.add(BigInteger.ONE), deployerNonceAfterDeployment);
        assertEquals(BigInteger.ZERO, contractBalance);
        assertEquals(BigInteger.ZERO, contractNonce);
    }

    @Test
    public void testUsingCallInContract() {
        AvmVersion version = AvmVersion.VERSION_1;

        if (txType == TransactionTypes.DEFAULT) {
            // skip this test cause the contract can't deploy successfully in the FVM.
            return;
        }

        AionTxReceipt receipt = deployContract(version);

        // Check the contract has the Avm prefix, and deployment succeeded, and grab the address.
        assertEquals(AddressSpecs.A0_IDENTIFIER, receipt.getTransactionOutput()[0]);
        assertTrue(receipt.isSuccessful());
        AionAddress contract = new AionAddress(receipt.getTransactionOutput());

        BigInteger deployerInitialNonce = getNonce(this.deployer);
        BigInteger contractInitialNonce = getNonce(contract);
        BigInteger deployerInitialBalance = getBalance(this.deployer);
        BigInteger contractInitialBalance = getBalance(contract);
        BigInteger fundsToSendToContract = BigInteger.valueOf(1000);

        // Transfer some value to the contract so we can do a 'call' from within it.
        receipt = transferValueTo(contract, fundsToSendToContract);
        assertTrue(receipt.isSuccessful());

        // verify that the sender and contract balances are correct after the transfer.
        BigInteger transferEnergyCost =
                BigInteger.valueOf(receipt.getEnergyUsed())
                        .multiply(BigInteger.valueOf(this.energyPrice));
        BigInteger deployerBalanceAfterTransfer =
                deployerInitialBalance.subtract(fundsToSendToContract).subtract(transferEnergyCost);
        BigInteger contractBalanceAfterTransfer = contractInitialBalance.add(fundsToSendToContract);
        BigInteger deployerNonceAfterTransfer = deployerInitialNonce.add(BigInteger.ONE);

        assertEquals(deployerBalanceAfterTransfer, getBalance(this.deployer));
        assertEquals(contractBalanceAfterTransfer, getBalance(contract));
        assertEquals(deployerNonceAfterTransfer, getNonce(this.deployer));
        assertEquals(contractInitialNonce, getNonce(contract));

        // Generate a random beneficiary to transfer funds to via the contract.
        AionAddress beneficiary = randomAddress();
        long valueForContractToSend = fundsToSendToContract.longValue() / 2;

        // Call the contract to send value using an internal call.
        receipt =
                callContract(version,
                        contract,
                        "transferValue",
                        beneficiary.toByteArray(),
                        valueForContractToSend);
        assertTrue(receipt.isSuccessful());

        // Verify the accounts have the expected state.
        BigInteger deployerBalanceAfterCall = getBalance(this.deployer);
        BigInteger contractBalanceAfterCall = getBalance(contract);
        BigInteger beneficiaryBalanceAfterCall = getBalance(beneficiary);

        BigInteger callEnergyCost =
                BigInteger.valueOf(receipt.getEnergyUsed())
                        .multiply(BigInteger.valueOf(this.energyPrice));

        assertEquals(
                deployerBalanceAfterTransfer.subtract(callEnergyCost), deployerBalanceAfterCall);
        assertEquals(
                contractBalanceAfterTransfer.subtract(BigInteger.valueOf(valueForContractToSend)),
                contractBalanceAfterCall);
        assertEquals(BigInteger.valueOf(valueForContractToSend), beneficiaryBalanceAfterCall);
        assertEquals(deployerNonceAfterTransfer.add(BigInteger.ONE), getNonce(this.deployer));

        // The contract nonce increases because it fires off an internal transaction.
        assertEquals(contractInitialNonce.add(BigInteger.ONE), getNonce(contract));
    }

    private AionTxReceipt deployContract(AvmVersion version) {
        byte[] jar = getJarBytes(version);
        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        getNonce(this.deployer).toByteArray(),
                        null,
                        new byte[0],
                        jar,
                        5_000_000,
                        this.energyPrice,
                        txType, null);

        return sendTransactions(transaction);
    }

    private AionTxReceipt callContract(AvmVersion version, AionAddress contract, String method, byte[] beneficiary, long valueToSend) {
        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        getNonce(this.deployer).toByteArray(),
                        contract,
                        new byte[0],
                        abiEncodeMethodCall(version, method, beneficiary, valueToSend),
                        2_000_000,
                        this.energyPrice,
                        TransactionTypes.DEFAULT, null);

        return sendTransactions(transaction);
    }

    private AionTxReceipt transferValueTo(AionAddress beneficiary, BigInteger value) {
        AionTransaction transaction =
                AionTransaction.create(
                        deployerKey,
                        getNonce(this.deployer).toByteArray(),
                        beneficiary,
                        value.toByteArray(),
                        new byte[0],
                        2_000_000,
                        this.energyPrice,
                        TransactionTypes.DEFAULT, null);

        return sendTransactions(transaction);
    }

    private AionTxReceipt sendTransactions(AionTransaction... transactions) {
        Block parentBlock = this.blockchain.getBestBlock();
        AionBlock block =
                this.blockchain.createBlock(
                        parentBlock,
                        Arrays.asList(transactions),
                        false,
                        parentBlock.getTimestamp());
        Pair<ImportResult, AionBlockSummary> connectResult =
                this.blockchain.tryToConnectAndFetchSummary(block);
        assertEquals(ImportResult.IMPORTED_BEST, connectResult.getLeft());
        return connectResult.getRight().getReceipts().get(0);
    }

    private byte[] getJarBytes(AvmVersion version) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : resourceProvider.factoryForVersion2;
        return factory.newContractFactory().getDeploymentBytes(AvmContract.STATEFULNESS);
    }

    private byte[] abiEncodeMethodCall(AvmVersion version, String method, byte[] beneficiary, long valueToSend) {
        IAvmResourceFactory factory = (version == AvmVersion.VERSION_1) ? resourceProvider.factoryForVersion1 : resourceProvider.factoryForVersion2;
        return factory.newStreamingEncoder().encodeOneString(method).encodeOneByteArray(beneficiary).encodeOneLong(valueToSend).getEncoding();
    }

    private BigInteger getBalance(AionAddress address) {
        return this.blockchain.getRepository().getBalance(address);
    }

    private BigInteger getNonce(AionAddress address) {
        return this.blockchain.getRepository().getNonce(address);
    }

    private AionAddress randomAddress() {
        byte[] bytes = RandomUtils.nextBytes(32);
        bytes[0] = (byte) 0xa0;
        return new AionAddress(bytes);
    }
}
