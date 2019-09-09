package org.aion.precompiled.contracts;

import java.math.BigInteger;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.IPrecompiledDataWord;
import org.aion.precompiled.type.IExternalStateForPrecompiled;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.precompiled.type.PrecompiledDataWord;
import org.aion.types.AionAddress;
import org.aion.types.TransactionStatus;
import org.aion.util.biginteger.BIUtil;

/** A pre-compiled contract for retrieving and updating the total amount of currency. */
public class TotalCurrencyContract implements PrecompiledContract {
    // set to a default cost for now, this will need to be adjusted
    private static final long COST = 21000L;
    private final IExternalStateForPrecompiled externalState;

    private AionAddress address;
    private AionAddress ownerAddress;

    /**
     * Constructs a new TotalCurrencyContract.
     *
     * @param externalState
     * @param address
     * @param ownerAddress
     */
    public TotalCurrencyContract(
            IExternalStateForPrecompiled externalState,
            AionAddress address,
            AionAddress ownerAddress) {
        this.externalState = externalState;
        this.address = address;
        this.ownerAddress = ownerAddress;
    }

    /**
     * Define the input data format as the following:
     *
     * <p>
     *
     * <pre>{@code
     * [<1b - chainId> | <1b - signum> | <16b - uint128 amount> | <96b signature>]
     * total: 1 + 1 + 16 + 96 = 114
     *
     * }</pre>
     *
     * <p>Where the chainId is intended to be our current chainId, in the case of the first AION
     * network this should be set to 1. Note the presence of signum byte (bit) to check for addition
     * or subtraction
     *
     * <p>Note: as a consequence of us storing the pk and signature as part of the call we can send
     * a transaction to this contract from any address. As long as we hold the private key preset in
     * this contract.
     *
     * <p>Within the contract, the storage is modelled as the following:
     *
     * <pre>{@code
     * [1, total]
     * [2, total]
     * [3, total]
     * ...
     *
     * }</pre>
     *
     * <p>Therefore retrieval should be relatively simple. There is also a retrieval (query)
     * function provided, given that the input length is 4. In such a case, the input value is
     * treated as a integer indicating the chainId, and thus the corresponding offset in the storage
     * row to query the value from.
     *
     * <p>
     *
     * <pre>{@code
     * [<1b - chainId>]
     *
     * }</pre>
     */
    @Override
    public PrecompiledTransactionResult execute(byte[] input, long nrg) {
        // query portion (pure)
        if (input.length == 1) {
            return queryNetworkBalance(input[0], nrg);
        } else {
            return executeUpdateTotalBalance(input, nrg);
        }
    }

    private PrecompiledTransactionResult queryNetworkBalance(int input, long nrg) {
        if (nrg < COST) {
            // TODO: should this cost be the same as updating state (probably not?)
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("OUT_OF_NRG"), 0);
        }

        IPrecompiledDataWord balanceData =
                this.externalState.getStorageValue(this.address, PrecompiledDataWord.fromInt(input));
        return new PrecompiledTransactionResult(
            TransactionStatus.successful(), nrg - COST, balanceData.copyOfData());
    }

    private PrecompiledTransactionResult executeUpdateTotalBalance(byte[] input, long nrg) {
        // update total portion
        if (nrg < COST) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("OUT_OF_NRG"), 0);
        }

        if (input.length < 114) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("FAILURE"), 0);
        }

        // process input data
        int offset = 0;
        PrecompiledDataWord chainId = PrecompiledDataWord.fromInt(input[0]);
        offset++;

        byte signum = input[1];
        offset++;

        byte[] amount = new byte[16];
        byte[] sign = new byte[96];

        System.arraycopy(input, offset, amount, 0, 16);
        offset += 16;
        System.arraycopy(input, offset, sign, 0, 96);

        // verify signature is correct
        Ed25519Signature sig = Ed25519Signature.fromBytes(sign);
        if (sig == null) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("FAILURE"), 0);
        }

        byte[] payload = new byte[18];
        System.arraycopy(input, 0, payload, 0, 18);
        boolean b = ECKeyEd25519.verify(payload, sig.getSignature(), sig.getPubkey(null));

        if (!b) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("FAILURE"), 0);
        }

        // verify public key matches owner
        if (!this.ownerAddress.equals(new AionAddress(sig.getAddress()))) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("FAILURE"), 0);
        }

        // payload processing
        IPrecompiledDataWord totalCurr = this.externalState.getStorageValue(this.address, chainId);
        BigInteger totalCurrBI =
                totalCurr == null ? BigInteger.ZERO : BIUtil.toBI(totalCurr.copyOfData());
        BigInteger value = BIUtil.toBI(amount);

        if (signum != 0x0 && signum != 0x1) {
            return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("FAILURE"), 0);
        }

        BigInteger finalValue;
        if (signum == 0x0) {
            // addition
            finalValue = totalCurrBI.add(value);
        } else {
            // subtraction
            if (value.compareTo(totalCurrBI) > 0) {
                return new PrecompiledTransactionResult(TransactionStatus.nonRevertedFailure("FAILURE"), 0);
            }

            finalValue = totalCurrBI.subtract(value);
        }

        // store result and successful exit
        this.externalState.addStorageValue(
                this.address,
                chainId,
                PrecompiledDataWord.fromBytes(finalValue.toByteArray()));
        return new PrecompiledTransactionResult(TransactionStatus.successful(), nrg - COST);
    }
}
