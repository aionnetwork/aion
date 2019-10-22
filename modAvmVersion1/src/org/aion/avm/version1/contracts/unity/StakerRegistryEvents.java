package org.aion.avm.version1.contracts.unity;

import avm.Address;
import avm.Blockchain;
import org.aion.avm.userlib.AionUtilities;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;

import java.math.BigInteger;

public class StakerRegistryEvents {

    protected static void registeredStaker(Address identityAddress, Address managementAddress,
                                 Address signingAddress, Address coinbaseAddress) {

        Blockchain.log("StakerRegistered".getBytes(),
                identityAddress.toByteArray(),
                signingAddress.toByteArray(),
                coinbaseAddress.toByteArray(),
                managementAddress.toByteArray());
    }

    protected static void setSigningAddress(Address identityAddress, Address newAddress) {
        Blockchain.log("SigningAddressSet".getBytes(),
                identityAddress.toByteArray(),
                newAddress.toByteArray());
    }

    protected static void setCoinbaseAddress(Address identityAddress, Address newAddress) {
        Blockchain.log("CoinbaseAddressSet".getBytes(),
                identityAddress.toByteArray(),
                newAddress.toByteArray());
    }

    protected static void transferredStake(long id, Address fromStaker, Address toStaker, BigInteger amount, BigInteger fee) {
        byte[] data = new byte[(32 + 2) * 2];
        new ABIStreamingEncoder(data).encodeOneBigInteger(amount).encodeOneBigInteger(fee);

        Blockchain.log("StakeTransferred".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                fromStaker.toByteArray(),
                toStaker.toByteArray(),
                data);
    }

    protected static void finalizedUnbond(long id) {
        Blockchain.log("UnbondFinalized".getBytes(),
                BigInteger.valueOf(id).toByteArray());
    }

    protected static void finalizedTransfer(long id) {
        Blockchain.log("TransferFinalized".getBytes(),
                BigInteger.valueOf(id).toByteArray());
    }

    protected static void bonded(Address identityAddress, BigInteger amount) {
        Blockchain.log("Bonded".getBytes(),
                identityAddress.toByteArray(),
                amount.toByteArray());
    }

    protected static void unbonded(long id, Address staker, Address recipient, BigInteger amount, BigInteger fee) {
        byte[] data = new byte[(32 + 2) * 2];
        new ABIStreamingEncoder(data).encodeOneBigInteger(amount).encodeOneBigInteger(fee);

        Blockchain.log("Unbonded".getBytes(),
                AionUtilities.padLeft(BigInteger.valueOf(id).toByteArray()),
                staker.toByteArray(),
                recipient.toByteArray(),
                data);
    }

    protected static void changedState(Address staker, boolean state){
        Blockchain.log("StateChanged".getBytes(),
                staker.toByteArray(),
                new byte[]{(byte) (state ? 1 : 0)});
    }

    protected static void stakerRegistryDeployed(BigInteger minSelfStake, long signingAddressCoolingPeriod, long undelegateLockUpPeriod, long transferLockUpPeriod) {
        Blockchain.log("StakerRegistryDeployed".getBytes(),
                AionUtilities.padLeft(minSelfStake.toByteArray()),
                AionUtilities.padLeft(BigInteger.valueOf(signingAddressCoolingPeriod).toByteArray()),
                AionUtilities.padLeft(BigInteger.valueOf(undelegateLockUpPeriod).toByteArray()),
                BigInteger.valueOf(transferLockUpPeriod).toByteArray());
    }
}
