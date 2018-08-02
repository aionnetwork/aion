package org.aion.precompiled.contracts;

import org.aion.base.type.Address;
import org.aion.base.type.IExecutionResult;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IPrecompiledContract;

/**
 * Precompiled contract that verifies a signed message with the Ed25519 signature algorithm
 */
public class EDVerifyContract implements IPrecompiledContract {
    // set to a default cost for now, this will need to be adjusted
    private final static long COST = 21000L;

    /**
     * TODO: may want return the actual public key/address
     *
     * Method takes as input a byte array representing the edverify method parameters represented in hexadecimal
     * from the solidity binary as follows
     *
     * edverify(bytes32 hash, bytes32 signature_part_1, bytes32 signature_part_2, bytes32 public_key)
     *
     *                                              input
     *                                                |
     * | message hash (32 bytes) | signature part 1 (32 bytes) | signature part 2 (32 bytes) | public key (32 bytes) |
     *
     * where
     *      - message hash = keccak hashed message
     *      - signature part 1 = first 32 bytes of the ED25519 signature bytes
     *      - signature part 2 = last 32 bytes of the ED25519 signature bytes
     *      - public key = public key of the ED25519 key pair
     *
     * @param input The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return byte array of length 1 with the byte set to: 1 if the message was signed with the public key of the account
     * identified with the public key,
     *                                                      0 otherwise
     */
    @Override
    public IExecutionResult execute(byte[] input, long nrgLimit) {
        byte[] result = new byte[1];

        if (COST > nrgLimit) {
            result[0] = (byte) 0;
            return new ExecutionResult(ExecutionResult.ResultCode.OUT_OF_NRG, 0, result);
        }
        byte[] msg = new byte[32];
        byte[] pubKey = new byte[32];
        byte[] sig = new byte[64];

        try {
            System.arraycopy(input, 0, msg, 0, 32);
            System.arraycopy(input, 32, sig, 0, 64);
            System.arraycopy(input, 96, pubKey, 0, 32);
        } catch (Exception e) {
            System.out.println("Invalid input. Check if size is correct: " + e.getMessage());
            result[0] = (byte) 0;
            return new ExecutionResult(ExecutionResult.ResultCode.INTERNAL_ERROR, 0, result);
        }

        try {
            boolean verify = ECKeyEd25519.verify(msg, sig, pubKey);
            result[0] = verify ? (byte) 1 : (byte) 0;
            return new ExecutionResult(ExecutionResult.ResultCode.SUCCESS, nrgLimit - COST, result);
        } catch (Throwable e) {
            result[0] = (byte) 0;
            return new ExecutionResult(ExecutionResult.ResultCode.FAILURE, 0, result);
        }
    }
}
