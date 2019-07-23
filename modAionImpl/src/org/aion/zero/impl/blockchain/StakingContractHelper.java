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
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.blockchain.Block;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.Repository;
import org.aion.mcf.db.RepositoryCache;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.vm.BlockCachingContext;
import org.aion.vm.BulkExecutor;
import org.aion.vm.exception.VMException;
import org.aion.zero.impl.AionBlockchainImpl;
import org.aion.zero.types.AionTxReceipt;
import org.slf4j.Logger;


// TODO: [unity] It require avm libs, might consider to remove the dependency later
public class StakingContractHelper {
    private static AionAddress stakingContractAddr;
    private static ECKey stakerKey;
    private static AionAddress stakerAddress;
    private AionBlockchainImpl chain;
    private static final Logger LOG_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private static final Logger LOG_GEN = AionLoggerFactory.getLogger(LogEnum.GEN.toString());

    private static byte[] abiGetVote = ABIEncoder.encodeOneString("getVote");
    private static byte[] abiRegister = ABIEncoder.encodeOneString("register");

    private static boolean deployed = false;

    public StakingContractHelper(
            AionAddress contractDestination, ECKey key, AionBlockchainImpl _chain) {

        if (contractDestination == null) {
            throw new IllegalStateException("The contract destination can't be null");
        }

        if (_chain == null) {
            throw new IllegalStateException("The chain instance can't be null");
        }

        if (key == null) {
            throw new IllegalStateException("The stakerKey can't be null");
        }

        stakingContractAddr = contractDestination;
        stakerKey = key;
        stakerAddress = new AionAddress(stakerKey.getAddress());
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

    public long callGetVote(AionAddress stakerAddress) {

        byte[] abi =
                ByteUtil.merge(
                        abiGetVote,
                        ABIEncoder.encodeOneAddress(new Address(stakerAddress.toByteArray())));

        AionTransaction callTx =
                new AionTransaction(
                        BigInteger.ZERO.toByteArray(),
                        stakerAddress,
                        stakingContractAddr,
                        BigInteger.ZERO.toByteArray(),
                        abi,
                        2_000_000,
                        TransactionTypes.DEFAULT);

        AionTxReceipt receipt = callConstant(callTx);

        if (receipt == null || Arrays.equals(receipt.getTransactionOutput(), new byte[0])) {
            // TODO: [unity] handle the error case.
            return 0;
        }

        return new ABIDecoder(receipt.getTransactionOutput()).decodeOneLong();
    }

    private AionTxReceipt callConstant(AionTransaction tx) {

        if (tx.getSignature() == null) {
            tx.sign(keyForCallandEstimate);
        }

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

    public byte[] callRegister() {

        AionTransaction callTx =
                new AionTransaction(
                        chain.getRepository().getNonce(stakerAddress).toByteArray(),
                        stakingContractAddr,
                        BigInteger.ZERO.toByteArray(),
                        abiRegister,
                        2_000_000,
                        10_000_000_000L);

        callTx.sign(stakerKey);

        return callTx.getEncoded();
    }
}
