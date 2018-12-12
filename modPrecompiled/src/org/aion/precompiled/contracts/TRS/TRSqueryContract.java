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
 *     Aion foundation.
 */
package org.aion.precompiled.contracts.TRS;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.type.IBlock;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.core.IBlockchain;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.vm.FastVmResultCode;
import org.aion.vm.FastVmTransactionResult;

/**
 * The TRSqueryContract is 1 of 3 inter-dependent but separate contracts that together make up the
 * public-facing TRS contract. A public-facing TRS contract can be owned by any user. In addition to
 * a regular user being able to own a public-facing TRS contract, there is also a special instance
 * of the public-facing TRS contract that is owned by The Aion Foundation itself, which differs from
 * the private TRS contract.
 *
 * <p>The public-facing TRS contract was split into 3 contracts mostly for user-friendliness, since
 * the TRS contract supports many operations, rather than have a single execute method and one very
 * large document specifying its use, the contract was split into 3 logical components instead.
 *
 * <p>The TRSqueryContract is the component of the public-facing TRS contract that users of the
 * contract (as well as the owner) interact with in order to make simple queries on the contract.
 * None of the operations supported here will change the state of the contract. This contract
 * extends StatefulPrecompiledContract not because it changes state but only for database access.
 *
 * <p>The following operations are supported: isStarted -- checks whether a TRS contract is live.
 * isLocked -- checks whether a TRS contract is locked. isDirectDeposit -- checks whether a
 * depositor can directly deposit to a TRS contract or not. period -- checks the current period that
 * a TRS contract is in. periodAt -- checks the period that a TRS contract is in at a specific
 * block. availableForWithdrawalAt -- checks the fraction of total withdrawable funds for a
 * contract.
 */
public final class TRSqueryContract extends AbstractTRS {

    /**
     * Constructs a new TRSqueryContract that will use repo as the database cache to update its
     * state with and is called by caller.
     *
     * @param repo The database cache.
     * @param caller The calling address.
     */
    public TRSqueryContract(
            IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo,
            AionAddress caller,
            IBlockchain blockchain) {

        super(repo, caller, blockchain);
    }

    /**
     * The input byte array provided to this method must have the following format:
     *
     * <p>[<1b - operation> | <arguments>]
     *
     * <p>where arguments is defined differently for different operations. The supported operations
     * along with their expected arguments are outlined as follows:
     *
     * <p><b>operation 0x0</b> - returns true iff the specified public-facing TRS contract is live.
     * Note that a contract with its funds open is not considered live. [<32b - contractAddress>]
     * total = 33 bytes where: contractAddress is the address of the public-facing TRS contract to
     * query.
     *
     * <p>conditions: none.
     *
     * <p>returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     * 0x0 for false.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x1</b> - returns true iff the specified public-facing TRS contract is
     * locked. If a contract is live then it must also be locked and so a live contract will return
     * true. Note that a contract with its funds open is not considered locked. [<32b -
     * contractAddress>] total = 33 bytes where: contractAddress is the address of the public-facing
     * TRS contract to query.
     *
     * <p>conditions: none.
     *
     * <p>returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     * 0x0 for false.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x2</b> - returns true iff the specified public-facing TRS contract has
     * direct deposits enabled. Note that this operation returns false if the contract has its funds
     * open. [<32b - contractAddress>] total = 33 bytes where: contractAddress is the address of the
     * public-facing TRS contract to query.
     *
     * <p>coditions: none.
     *
     * <p>returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     * 0x0 for false.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x3</b> - returns the current period that the specified public-facing TRS
     * contract is in. The current time that is used to determine this period is the timestamp of
     * the blockchain's best block. Note that if a contract's funds are open then the returned value
     * is meaningless. [<32b - contractAddress>] total = 33 bytes where: contractAddress is the
     * address of the public-facing TRS contract to query.
     *
     * <p>coditions: none.
     *
     * <p>returns: a byte array of length 4 that is the byte representation of a signed integer.
     * This value is equal to the period the contract is in at the current best block. The contract
     * is defined as being in period 0 at all times prior to the moment when the contract was made
     * live. Once the contract becomes live it is in period 1 and this proceeds until it is in
     * period P, the maximum period defined for the contract. From this point onwards the contract
     * remains in period P. The duration of a period is 30 days.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x4</b> - returns the period that the specified public-facing TRS contract is
     * in at the specified block number. Note that if a contract's funds are open then the returned
     * value is meaningless. [<32b - contractAddress> | <8b - blockNumber>] total = 41 bytes where:
     * contractAddress is the address of the public-facing TRS contract to query. blockNumber is the
     * block number at which time to assess what period the contract is in. This value will be
     * interpreted as signed.
     *
     * <p>coditions: blockNumber must be non-negative.
     *
     * <p>returns: a byte array of length 4 that is the byte representation of a signed integer.
     * This value is equal to the period the contract is in at the specified block. The contract is
     * defined as being in period 0 at all times prior to the moment when the contract was made
     * live. Once the contract becomes live it is in period 1 and this proceeds until it is in
     * period P, the maximum period defined for the contract. From this point onwards the contract
     * remains in period P. The duration of a period is 30 days.
     *
     * <p>~~~***~~~
     *
     * <p><b>operation 0x5</b> - returns the fraction of the caller's total owings that is
     * withdrawable at some time given by a block's timestamp. The total owings is the amount that
     * the caller will collect over the full lifetime of the contract. The fraction returned is
     * cumulative, so it represents the fraction of funds that the caller will have cumulatively
     * collected by the specified time if they withdraw in the corresponding period.
     *
     * <p>[<32b - contractAddress> | <8b - timestamp>] total = 41 bytes where: contractAddress is
     * the address of the public-facing TRS contract to query. timestamp is the timestamp (a long
     * value denoting seconds) of a block in the blockchain.
     *
     * <p>coditions: contractAddress must be a valid TRS contract address.
     *
     * <p>returns: a byte array representing a BigInteger (the resulting of BigInteger's toByteArray
     * method). The returned BigInteger must then have its decimal point shifted 18 places to the
     * left to reconstruct the appropriate fraction, accurate to 18 decimal places.
     *
     * @param input The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return the result of calling execute on the specified input.
     */
    @Override
    public FastVmTransactionResult execute(byte[] input, long nrgLimit) {
        if (input == null) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
        if (input.length == 0) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
        if (nrgLimit < COST) {
            return new FastVmTransactionResult(FastVmResultCode.OUT_OF_NRG, 0);
        }
        if (!isValidTxNrg(nrgLimit)) {
            return new FastVmTransactionResult(FastVmResultCode.INVALID_NRG_LIMIT, 0);
        }

        int operation = input[0];
        switch (operation) {
            case 0:
                return isStarted(input, nrgLimit);
            case 1:
                return isLocked(input, nrgLimit);
            case 2:
                return isDirectDepositEnabled(input, nrgLimit);
            case 3:
                return period(input, nrgLimit);
            case 4:
                return periodAt(input, nrgLimit);
            case 5:
                return availableForWithdrawalAt(input, nrgLimit);
            default:
                return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }
    }

    /**
     * Logic to query a public-facing TRS contract to determine whether or not it is live.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x0> | <32b - contractAddress>]
     * total = 33 bytes where: contractAddress is the address of the public-facing TRS contract.
     *
     * <p>conditions: none.
     *
     * <p>returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     * 0x0 for false.
     *
     * @param input The input to query a public-facing TRS contract for liveness.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private FastVmTransactionResult isStarted(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        byte[] result = new byte[1];
        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexAddress, len));
        if (!isOpenFunds(contract) && isContractLive(contract)) {
            result[0] = 0x1;
        }
        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, COST - nrgLimit, result);
    }

    /**
     * Logic to query a public-facing TRS contract to determine whether or not it is locked.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x0> | <32b - contractAddress>]
     * total = 33 bytes where: contractAddress is the address of the public-facing TRS contract.
     *
     * <p>conditions: none.
     *
     * <p>returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     * 0x0 for false.
     *
     * @param input The input to query a public-facing TRS contract for lockedness.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private FastVmTransactionResult isLocked(byte[] input, long nrgLimit) {
        // Some "constants".
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        byte[] result = new byte[1];
        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexAddress, len));
        if (!isOpenFunds(contract) && isContractLocked(contract)) {
            result[0] = 0x1;
        }
        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, COST - nrgLimit, result);
    }

    /**
     * Logic to query a public-facing TRS contract to determine whether or not direct deposits are
     * enabled for it.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x2> | <32b - contractAddress>]
     * total = 33 bytes where: contractAddress is the address of the public-facing TRS contract.
     *
     * <p>conditions: none.
     *
     * <p>returns: a byte array of length 1 with the only byte in the array set to 0x1 for true and
     * 0x0 for false.
     *
     * @param input The input to query a public-facing TRS contract for direct deposits.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private FastVmTransactionResult isDirectDepositEnabled(byte[] input, long nrgLimit) {
        // Some "constants"
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        byte[] result = new byte[1];
        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexAddress, len));
        if (!isOpenFunds(contract) && isDirDepositsEnabled(contract)) {
            result[0] = 0x1;
        }
        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, COST - nrgLimit, result);
    }

    /**
     * Logic to query a public-facing TRS contract to determine which period the contract is in
     * currently. That is, what period the contract is in at the time given by the timestamp of the
     * current best block in the blockchain.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x3> | <32b - contractAddress>]
     * total = 33 bytes where: contractAddress is the address of the public-facing TRS contract to
     * query.
     *
     * <p>coditions: none.
     *
     * <p>returns: a byte array of length 4 that is the byte representation of a signed integer.
     * This value is equal to the period the contract is in at the current best block. The contract
     * is defined as being in period 0 at all times prior to the moment when the contract was made
     * live. Once the contract becomes live it is in period 1 and this proceeds until it is in
     * period P, the maximum period defined for the contract. From this point onwards the contract
     * remains in period P.
     *
     * @param input The input to query a public-facing TRS contract for its period at the best
     *     block.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private FastVmTransactionResult period(byte[] input, long nrgLimit) {
        // Some "constants"
        final int indexAddress = 1;
        final int len = 33;

        if (input.length != len) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        // Grab the contract address and block number and determine the period.
        AionAddress contract = AionAddress.wrap(Arrays.copyOfRange(input, indexAddress, len));

        return determinePeriod(contract, blockchain.getBestBlock(), nrgLimit);
    }

    /**
     * Logic to query a public-facing TRS contract to determine which period the contract is in at
     * the specified block number.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x4> | <32b - contractAddress> |
     * <8b - blockNumber>] total = 41 bytes where: contractAddress is the address of the
     * public-facing TRS contract to query. blockNumber is the block number at which time to assess
     * what period the contract is in. This value will be interpreted as signed.
     *
     * <p>coditions: blockNumber must be non-negative.
     *
     * <p>returns: a byte array of length 4 that is the byte representation of a signed integer.
     * This value is equal to the period the contract is in at the specified block. The contract is
     * defined as being in period 0 at all times prior to the moment when the contract was made
     * live. Once the contract becomes live it is in period 1 and this proceeds until it is in
     * period P, the maximum period defined for the contract. From this point onwards the contract
     * remains in period P.
     *
     * @param input The input to query a public-facing TRS contract for its period at some block.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private FastVmTransactionResult periodAt(byte[] input, long nrgLimit) {
        // Some "constants"
        final int indexAddress = 1;
        final int indexBlockNum = 33;
        final int len = 41;

        if (input.length != len) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        // Grab the contract address and block number and determine the period.
        AionAddress contract =
                AionAddress.wrap(Arrays.copyOfRange(input, indexAddress, indexBlockNum));

        ByteBuffer blockBuf = ByteBuffer.allocate(Long.BYTES);
        blockBuf.put(Arrays.copyOfRange(input, indexBlockNum, len));
        blockBuf.flip();
        long blockNum = blockBuf.getLong();

        if (blockNum <= 0) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        return determinePeriod(contract, blockchain.getBlockByNumber(blockNum), nrgLimit);
    }

    /**
     * Logic to query a public-facing TRS contract to determine the fraction of total withdrawables
     * available at a specified time.
     *
     * <p>The input byte array format is defined as follows: [<1b - 0x5> | <32b - contractAddress> |
     * <8b - timestamp>] total = 41 bytes where: contractAddress is the address of the public-facing
     * TRS contract to query. timestamp is the timestamp (a long value denoting seconds) of a block
     * in the blockchain.
     *
     * <p>coditions: contractAddress must be a valid TRS contract address.
     *
     * <p>returns: a byte array representing a BigInteger (the resulting of BigInteger's toByteArray
     * method). The returned BigInteger must then have its decimal point shifted 18 places to the
     * left to reconstruct the appropriate fraction, accurate to 18 decimal places.
     *
     * @param input The input to query a public-facing TRS contract for the fraction of
     *     withdrawables.
     * @param nrgLimit The energy limit.
     * @return the result of executing this logic on the specified input.
     */
    private FastVmTransactionResult availableForWithdrawalAt(byte[] input, long nrgLimit) {
        // Some "constants"
        final int indexContract = 1;
        final int indexTimestamp = 33;
        final int len = 41;

        if (input.length != len) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        AionAddress contract =
                AionAddress.wrap(Arrays.copyOfRange(input, indexContract, indexTimestamp));
        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        // If a contract has its funds open then the fraction is always 1.
        if (isOpenFunds(contract)) {
            return new FastVmTransactionResult(
                    FastVmResultCode.SUCCESS,
                    COST - nrgLimit,
                    (BigDecimal.ONE.movePointRight(18)).toBigInteger().toByteArray());
        }

        // This operation is only well-defined when the contract has a start time. Thus the contract
        // must be in the following state: live.
        if (!isContractLive(contract)) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(Arrays.copyOfRange(input, indexTimestamp, len));
        buffer.flip();
        long timestamp = buffer.getLong();

        int period = calculatePeriod(contract, getContractSpecs(contract), timestamp);
        if (period >= getPeriods(getContractSpecs(contract))) {
            return new FastVmTransactionResult(
                    FastVmResultCode.SUCCESS,
                    COST - nrgLimit,
                    (BigDecimal.ONE.movePointRight(18)).toBigInteger().toByteArray());
        }

        if (timestamp < getTimestamp(contract)) {
            return new FastVmTransactionResult(
                    FastVmResultCode.SUCCESS,
                    COST - nrgLimit,
                    (BigDecimal.ZERO.movePointRight(18)).toBigInteger().toByteArray());
        }

        BigInteger owings = computeTotalOwed(contract, caller);
        BigInteger amtPerPeriod = computeAmountWithdrawPerPeriod(contract, caller);
        BigInteger specAmt = computeRawSpecialAmount(contract, caller);
        BigInteger withdrawable = (amtPerPeriod.multiply(BigInteger.valueOf(period))).add(specAmt);

        BigDecimal fraction =
                new BigDecimal(withdrawable)
                        .divide(new BigDecimal(owings), 18, RoundingMode.HALF_DOWN);

        fraction = fraction.movePointRight(18);
        return new FastVmTransactionResult(
                FastVmResultCode.SUCCESS, COST - nrgLimit, fraction.toBigInteger().toByteArray());
    }

    // <---------------------------------------HELPERS--------------------------------------------->

    /**
     * Attempts to determine the period that the TRS contract whose address is contract is in at the
     * time denoted by the timestamp of block.
     *
     * <p>If the contract does not exist or block is null then this method returns a result with an
     * internal error.
     *
     * <p>Otherwise the returned result is success and its output is a byte array of length 4 that
     * is equivalent to a signed integer and which represents the period that the contract is in.
     *
     * @param contract The TRS contract to query.
     * @param block The block.
     * @param nrg The energy.
     * @return the period the contract is in at time given by block's timestamp.
     */
    private FastVmTransactionResult determinePeriod(AionAddress contract, IBlock block, long nrg) {
        // If contract doesn't exist, return an error.
        ByteBuffer output = ByteBuffer.allocate(Integer.BYTES);

        byte[] specs = getContractSpecs(contract);
        if (specs == null) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        // If contract is not yet live we are in period 0.
        if (!isContractLive(contract)) {
            output.putInt(0);
            return new FastVmTransactionResult(
                    FastVmResultCode.SUCCESS, COST - nrg, output.array());
        }

        // Grab the timestamp of block number blockNum and calculate the period the contract is in.
        if (block == null) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0);
        }

        long blockTime = block.getTimestamp();
        int period = calculatePeriod(contract, specs, blockTime);
        output.putInt(period);

        return new FastVmTransactionResult(FastVmResultCode.SUCCESS, COST - nrg, output.array());
    }
}
