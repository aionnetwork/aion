package org.aion.precompiled.contracts;

import org.aion.base.type.Address;
import org.aion.base.type.IExecutionResult;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;

import java.util.Arrays;

/**
 * Precompiled contract that verifies a signed message with the Ed25519 signature algorithm
 */
public class EDVerifyContract implements IPrecompiledContract {
    // set to a default cost for now, this will need to be adjusted
    private final static long COST = 21000L;

    /**
     * <p>
     * Method takes as input a byte array representing the edverify method parameters represented in hexadecimal
     * from the solidity binary as follows
     * <p>
     * edverify(bytes32 hash, bytes32 public_key, bytes signature)
     * <p>
     * input
     * |
     * | message hash (32 bytes) | public key (32 bytes) | signature bytes (64 bytes) |
     * <p>
     * where
     * - message hash = keccak 256 hashed message
     * - public key = public key of the ED25519 key pair
     * - signature = 64 bytes of the ED25519 signature
     *
     * @param input    The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return byte array of length 32 containing: the address of the account that signed the message  if the message was
     *                                              signed with the public key of the account sent as a parameter
     * <p>
     *                                              ZERO_ADDRESS 32 byte array of 0 otherwise
     */
    @Override
    public IExecutionResult execute(byte[] input, long nrgLimit) {
        if (COST > nrgLimit) {
            return new ExecutionResult(ExecutionResult.ResultCode.OUT_OF_NRG, 0, Address.ZERO_ADDRESS().toBytes());
        }
        byte[] msg = new byte[32];
        byte[] pubKey = new byte[32];
        byte[] sig = new byte[64];

        try {
            System.arraycopy(input, 0, msg, 0, 32);
            System.arraycopy(input, 32, pubKey, 0, 32);
            System.arraycopy(input, 64, sig, 0, 64);
        } catch (Exception e) {
            //TODO: Logging ?
            System.out.println("Invalid input. Check if size is correct: " + e.getMessage());
            return new ExecutionResult(ExecutionResult.ResultCode.INTERNAL_ERROR, 0, Address.ZERO_ADDRESS().toBytes());
        }

        try {
            boolean verify = ECKeyEd25519.verify(msg, sig, pubKey);
            if (verify) {
                Ed25519Signature signature = new Ed25519Signature(pubKey, sig);
                byte[] result = Arrays.copyOf(signature.getAddress(), Address.ADDRESS_LEN);
                return new ExecutionResult(ExecutionResult.ResultCode.SUCCESS, nrgLimit - COST, result);
            } else {
                return new ExecutionResult(ExecutionResult.ResultCode.SUCCESS, nrgLimit - COST, Address.ZERO_ADDRESS().toBytes());
            }
        } catch (Throwable e) {
            return new ExecutionResult(ExecutionResult.ResultCode.FAILURE, 0, Address.ZERO_ADDRESS().toBytes());
        }
    }
}
