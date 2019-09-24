package org.aion.zero.impl.blockchain;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.zero.impl.blockchain.AionImpl.keyForCallandEstimate;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.aion.avm.provider.AvmProvider;
import org.aion.avm.provider.types.AvmConfigurations;
import org.aion.avm.provider.types.VmFatalException;
import org.aion.avm.stub.AvmVersion;
import org.aion.avm.stub.IAvmResourceFactory;
import org.aion.base.AccountState;
import org.aion.base.AionTransaction;
import org.aion.base.AionTxReceipt;
import org.aion.base.TransactionTypes;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.common.BlockCachingContext;
import org.aion.vm.common.BulkExecutor;
import org.slf4j.Logger;

// TODO: [unity] It require avm libs, might consider to remove the dependency later

/**
 * @implNote this class implemented for querying staking contract status and return the data to the kernel
 * for the block creating/verifying purpose.
 */
public class StakingContractHelper {
    private static AionAddress stakingContractAddr;
    private AionBlockchainImpl chain;
    private static final Logger LOG_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private static final Logger LOG_GEN = AionLoggerFactory.getLogger(LogEnum.GEN.toString());
    private static final AvmVersion LATEST_AVM_VERSION = AvmVersion.VERSION_2;

    /**
     * cached byte array for skipping the abi encode the contract method during the contract call.
     */
    private byte[] effectiveStake = null;
    private byte[] coinbaseForSigningAddress = null;

    private static boolean deployed = false;

    public StakingContractHelper(AionAddress contractDestination, AionBlockchainImpl _chain) {
        if (contractDestination == null || _chain == null) {
            throw new NullPointerException();
        }

        stakingContractAddr = contractDestination;
        chain = _chain;
    }

    /**
     * @implNote Check the staking contract has been deployed and set flag to true when first time get the correct the account state of the staking contract.
     */
    public boolean isContractDeployed() {
        if (!deployed) {
            Repository r = chain.getRepository().startTracking();
            AccountState as = (AccountState) r.getAccountState(stakingContractAddr);
            deployed = !Arrays.equals(as.getCodeHash(), EMPTY_DATA_HASH);
        }

        // Once found contract has been deployed, no need to ask the db again.
        return deployed;
    }

    static AionAddress getStakingContractAddress() {
        return stakingContractAddr;
    }

    /**
     * this method called by the kernel for querying the correct stakes in the staking contract by giving desired coinbase address and the block signing address.
     * @param signingAddress the block signing address
     * @param coinbase the staker's coinbase for receiving the block rewards
     * @return the stake amount of the staker
     */
    public BigInteger getEffectiveStake(AionAddress signingAddress, AionAddress coinbase) throws ClassNotFoundException, IOException, InstantiationException, IllegalAccessException {
        if (signingAddress == null || coinbase == null) {
            throw new NullPointerException();
        }

        if (!AvmProvider.tryAcquireLock(10, TimeUnit.MINUTES)) {
            throw new IllegalStateException("Failed to acquire the avm lock!");
        }

        AvmProvider.enableAvmVersion(LATEST_AVM_VERSION, AvmConfigurations.getProjectRootDirectory());
        IAvmResourceFactory resourceFactory = AvmProvider.getResourceFactory(LATEST_AVM_VERSION);

        if (this.effectiveStake == null) {
            this.effectiveStake = resourceFactory.newStreamingEncoder().encodeOneString("getEffectiveStake").getEncoding();
        }

        byte[] abi =
                ByteUtil.merge(
                        this.effectiveStake,
                        resourceFactory.newStreamingEncoder().encodeOneAddress(signingAddress).getEncoding(),
                        resourceFactory.newStreamingEncoder().encodeOneAddress(coinbase).getEncoding());

        AionTransaction callTx =
                AionTransaction.create(
                        keyForCallandEstimate,
                        BigInteger.ZERO.toByteArray(),
                        stakingContractAddr,
                        BigInteger.ZERO.toByteArray(),
                        abi,
                        2_000_000L,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT,
                        null);

        AionTxReceipt receipt = callConstant(callTx);

        if (receipt == null || Arrays.equals(receipt.getTransactionOutput(), new byte[0])) {
            // TODO: [unity] handle the error case.
            return BigInteger.ZERO;
        }

        BigInteger output = resourceFactory.newDecoder(receipt.getTransactionOutput()).decodeOneBigInteger();

        AvmProvider.disableAvmVersion(LATEST_AVM_VERSION);
        AvmProvider.releaseLock();

        return output;
    }

    private AionTxReceipt callConstant(AionTransaction tx) {
        Block block = chain.getBestBlock();

        RepositoryCache repository =
                chain.getRepository().getSnapshotTo(block.getStateRoot()).startTracking();

        try {
            // Booleans moved out here so their meaning is explicit.
            boolean isLocalCall = true;
            boolean incrementSenderNonce = false;
            boolean fork040enabled = true;
            boolean checkBlockEnergyLimit = false;

            return BulkExecutor.executeTransactionWithNoPostExecutionWork(
                            block.getDifficulty(),
                            block.getNumber(),
                            block.getTimestamp(),
                            block.getNrgLimit(),
                            block.getCoinbase(),
                            tx,
                            repository,
                            isLocalCall,
                            incrementSenderNonce,
                            fork040enabled,
                            checkBlockEnergyLimit,
                            LOG_VM,
                            BlockCachingContext.CALL,
                            block.getNumber())
                    .getReceipt();
        } catch (VmFatalException e) {
            LOG_GEN.error("Shutdown due to a VM fatal error.", e);
            System.exit(-1);
            return null;
        }
    }

    /**
     * This method for getting the coinbase address by giving the signing address
     * @param signingAddress the block signing address
     * @return the staker's coinbase relate with the signing address in the staking contract
     */
    public AionAddress getCoinbaseForSigningAddress(AionAddress signingAddress) throws ClassNotFoundException, IOException, InstantiationException, IllegalAccessException {
        if (signingAddress == null) {
            throw new NullPointerException();
        }

        if (!AvmProvider.tryAcquireLock(10, TimeUnit.MINUTES)) {
            throw new IllegalStateException("Failed to acquire the avm lock!");
        }

        AvmProvider.enableAvmVersion(LATEST_AVM_VERSION, AvmConfigurations.getProjectRootDirectory());
        IAvmResourceFactory resourceFactory = AvmProvider.getResourceFactory(LATEST_AVM_VERSION);

        if (this.coinbaseForSigningAddress == null) {
            this.coinbaseForSigningAddress = resourceFactory.newStreamingEncoder().encodeOneString("getCoinbaseAddressForSigningAddress").getEncoding();
        }

        byte[] abi =
                ByteUtil.merge(
                        this.coinbaseForSigningAddress,
                        resourceFactory.newStreamingEncoder().encodeOneAddress(signingAddress).getEncoding());

        AionTransaction callTx =
                AionTransaction.create(
                        keyForCallandEstimate,
                        BigInteger.ZERO.toByteArray(),
                        stakingContractAddr,
                        BigInteger.ZERO.toByteArray(),
                        abi,
                        2_000_000L,
                        10_000_000_000L,
                        TransactionTypes.DEFAULT,
                        null);

        AionTxReceipt receipt = callConstant(callTx);

        if (receipt == null || Arrays.equals(receipt.getTransactionOutput(), new byte[0])) {
            // TODO: [unity] handle the error case.
            return null;
        }

        AionAddress address = resourceFactory.newDecoder(receipt.getTransactionOutput()).decodeOneAddress();

        AvmProvider.disableAvmVersion(LATEST_AVM_VERSION);
        AvmProvider.releaseLock();
        return address;
    }
}
