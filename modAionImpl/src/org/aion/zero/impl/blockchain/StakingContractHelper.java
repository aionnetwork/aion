package org.aion.zero.impl.blockchain;

import static org.aion.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.aion.zero.impl.blockchain.AionImpl.keyForCallandEstimate;

import avm.Address;
import java.math.BigInteger;
import java.util.Arrays;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIEncoder;
import org.aion.base.AionTransaction;
import org.aion.base.TransactionTypes;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.mcf.types.AionTxReceipt;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.util.types.AddressUtils;
import org.aion.vm.BlockCachingContext;
import org.aion.vm.BulkExecutor;
import org.aion.vm.exception.VMException;
import org.aion.zero.impl.AionBlockchainImpl;
import org.slf4j.Logger;


// TODO: [unity] It require avm libs, might consider to remove the dependency later
public class StakingContractHelper {
    private static AionAddress stakingContractAddr;
    private AionBlockchainImpl chain;
    private static final Logger LOG_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private static final Logger LOG_GEN = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    private static byte[] getEffectiveStake = ABIEncoder.encodeOneString("getEffectiveStake");
    private static byte[] getCoinbaseForSigningAddress = ABIEncoder.encodeOneString("getCoinbaseAddressForSigningAddress");

    private static boolean deployed = false;

    public StakingContractHelper(
            AionAddress contractDestination, AionBlockchainImpl _chain) {

        if (contractDestination == null) {
            throw new IllegalStateException("The contract destination can't be null");
        }

        if (_chain == null) {
            throw new IllegalStateException("The chain instance can't be null");
        }

        stakingContractAddr = contractDestination;
        chain = _chain;
    }

    public boolean isContractDeployed() {

        if (!deployed) {
            Repository r = chain.getRepository().startTracking();
            AccountState as = (AccountState) r.getAccountState(stakingContractAddr);
            deployed = !Arrays.equals(as.getCodeHash(), EMPTY_DATA_HASH);
        }

        //Once found contract has been deployed, no need to ask the db again.
        return deployed;
    }

    static AionAddress getStakingContractAddress() {
        return stakingContractAddr;
    }

    public long getEffectiveStake(AionAddress signingAddress, AionAddress coinbase) {
        if (signingAddress == null || coinbase == null) {
            throw new NullPointerException();
        }

        byte[] abi =
                ByteUtil.merge(
                        getEffectiveStake,
                        ABIEncoder.encodeOneAddress(new Address(signingAddress.toByteArray())),
                        ABIEncoder.encodeOneAddress(new Address(coinbase.toByteArray())));

        AionTransaction callTx =
                AionTransaction.create(
                    keyForCallandEstimate,
                    BigInteger.ZERO.toByteArray(),
                    stakingContractAddr,
                    BigInteger.ZERO.toByteArray(),
                    abi,
                    2_000_000L,
                    10_000_000_000L,
                    TransactionTypes.DEFAULT);

        AionTxReceipt receipt = callConstant(callTx);

        if (receipt == null || Arrays.equals(receipt.getTransactionOutput(), new byte[0])) {
            // TODO: [unity] handle the error case.
            return 0;
        }

        return new ABIDecoder(receipt.getTransactionOutput()).decodeOneLong();
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
        } catch (VMException e) {
            LOG_GEN.error("Shutdown due to a VM fatal error.", e);
            System.exit(-1);
            return null;
        } finally {
            repository.rollback();
        }
    }

    public AionAddress getCoinbaseForSigningAddress(AionAddress signingAddress) {

        if (signingAddress == null) {
            throw new NullPointerException();
        }

        byte[] abi =
            ByteUtil.merge(
                getCoinbaseForSigningAddress,
                ABIEncoder.encodeOneAddress(new Address(signingAddress.toByteArray())));

        AionTransaction callTx =
            AionTransaction.create(
                keyForCallandEstimate,
                BigInteger.ZERO.toByteArray(),
                stakingContractAddr,
                BigInteger.ZERO.toByteArray(),
                abi,
                2_000_000L,
                10_000_000_000L,
                TransactionTypes.DEFAULT);

        AionTxReceipt receipt = callConstant(callTx);

        if (receipt == null || Arrays.equals(receipt.getTransactionOutput(), new byte[0])) {
            // TODO: [unity] handle the error case.
            return null;
        }

        return AddressUtils.wrapAddress(new ABIDecoder(receipt.getTransactionOutput()).decodeOneAddress().toString());
    }
}
