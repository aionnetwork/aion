package org.aion.precompiled.contracts;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.vm.IDataWord;
import org.aion.crypto.ECKeyFac;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.ContractExecutionResult.ResultCode;
import org.aion.precompiled.type.StatefulPrecompiledContract;

/**
 * A Token Release Schedule pre-compiled contract. This contract allows tokens to be locked into a
 * non-negotiable release schedule over a period of time.
 *
 * @author nick nadeau
 */
public class TokenReleaseScheduleContract extends StatefulPrecompiledContract {
    private static final long COST = 21000L;    // here temporarily.
    private final Address caller;

    // At this stage we won't be querying db since all details aren't hammered out yet. Temp state
    // will be saved here for now..
    public Map<Address, Map<Address, BigInteger>> tempState;   // maps a TRS contract to an account-balance map.
    public Map<Address, Address> tempOwner;    // maps a TRS contract to the address that owns it.
    public Map<Address, BigInteger> tempPeriod;    // maps a TRS contract to a period value.
    public Map<Address, PeriodUnit> tempUnit;  // maps a TRS contract to a period unit.
    public Map<Address, BigDecimal> tempSpecMult;  // maps a TRS contract to the special multiplier.
    public enum PeriodUnit { SECONDS, MINUTES, HOURS, DAYS }

    /**
     * Constructs a new TokenReleaseSchedule pre-compiled contract.
     *
     * @param track The repository.
     * @param caller The address of the calling account.
     */
    public TokenReleaseScheduleContract(
        IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track, Address caller) {

        super(track);
        this.caller = caller;

        this.tempState = new HashMap<>();
        this.tempOwner = new HashMap<>();
        this.tempPeriod = new HashMap<>();
        this.tempUnit = new HashMap<>();
        this.tempSpecMult = new HashMap<>();
    }

    /**
     * The input parameter of this method is a byte array whose bytes should be supplied in the
     * below formats depending on the desired operation.
     *
     * The format of the input byte array is as follows:
     *
     * [<1b - operation> | <arguments>]
     *
     * Where arguments is determined by operation. The following operations and corresponding
     * arguments are supported:
     *
     * operation 0x0 - create a new Token Release Schedule contract.
     *   arguments: [<4b - period> | <1b - period unit> | <1b - special percent> | <1b - precision>]
     *   total: 8 bytes
     *   where period is the duration of a single period for the new contract and period unit
     *   signifies the unit by which to interpret period (as defined below) and special percent is
     *   the percentage of the total savings to be distributed in the special one-off event and
     *   precision is the number of decimal places by which to interpret special percent. The
     *   special percent parameter must be a number in the range [0,1] after precision is applied to
     *   it.
     *   we provide the following period units:
     *     0x0 - seconds
     *     0x1 - minutes
     *     0x2 - hours
     *     0x3 - days
     *   Note that both period and special percent are unsigned.
     *
     * Returns a ContractExecutionResult as the result of executing the TRS with the provided input
     * and nrgLimit.
     *
     * @param input The input arguments for the contract.
     * @param nrgLimit The energy limit.
     * @return the result of the execution on the provided arguments.
     */
    @Override
    public ContractExecutionResult execute(byte[] input, long nrgLimit) {
        if (input == null) { return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0); }
        if (nrgLimit < COST) { return new ContractExecutionResult(ResultCode.OUT_OF_NRG, 0); }
        if (input.length < 1) { return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0); }

        int operation = input[0];
        switch (operation) {
            case 0: return create(input, nrgLimit);
            default: return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
    }

    /**
     * Creates a new TRS contract that is owned by the caller of this method. We expect to receive
     * input in the following format:
     *
     * [<1b - operation> | <4b - period> | <1b - period unit> | <1b - special percent> | <1b - precision>]
     * where period unit supports the following:
     *   0x0 - seconds
     *   0x1 - minutes
     *   0x2 - hours
     *   0x3 - days
     *
     * @param input The input arguments to create a new TRS contract.
     * @return the result of the attempt to create the new contract.
     */
    private ContractExecutionResult create(byte[] input, long nrgLimit) {
        if (input.length != 8) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }
        BigInteger period = new BigInteger(Arrays.copyOfRange(input, 1, 5));
        period = period.compareTo(BigInteger.ZERO) < 0 ? period.negate() : period;

        PeriodUnit unit;
        switch (input[5]) {
            case 0x0:
                unit = PeriodUnit.SECONDS;
                break;
            case 0x1:
                unit = PeriodUnit.MINUTES;
                break;
            case 0x2:
                unit = PeriodUnit.HOURS;
                break;
            case 0x3:
                unit = PeriodUnit.DAYS;
                break;
            default: unit = null;
        }
        if (unit == null) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        int percent = (int) input[6];
        int precision = (int) input[7];
        BigDecimal fraction = new BigDecimal(BigInteger.valueOf(percent), precision);
        if (fraction.compareTo(BigDecimal.ZERO) < 0) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        } else if (fraction.compareTo(new BigDecimal("100")) > 0) {
            return new ContractExecutionResult(ResultCode.INTERNAL_ERROR, 0);
        }

        Address contract = new Address(ECKeyFac.inst().create().getAddress());
        this.tempOwner = new HashMap<>();
        this.tempOwner.put(contract, this.caller);
        this.tempState = new HashMap<>();
        this.tempState.put(contract, new HashMap<>());
        this.tempPeriod = new HashMap<>();
        this.tempPeriod.put(contract, period);
        this.tempUnit = new HashMap<>();
        this.tempUnit.put(contract, unit);
        this.tempSpecMult = new HashMap<>();
        this.tempSpecMult.put(contract, fraction);

        return new ContractExecutionResult(ResultCode.SUCCESS, nrgLimit - COST, contract.toBytes());
    }

    private ContractExecutionResult init() {
        //TODO
        return null;
    }

    private ContractExecutionResult lock() {
        //TODO
        return null;
    }

    private ContractExecutionResult start() {
        //TODO
        return null;
    }

    private ContractExecutionResult refund() {
        //TODO
        return null;
    }

    private ContractExecutionResult deposit() {
        //TODO
        return null;
    }

    private ContractExecutionResult withdraw() {
        //TODO
        return null;
    }

    private ContractExecutionResult period() {
        //TODO
        return null;
    }

    private void nullify() {
        //TODO
    }

}
