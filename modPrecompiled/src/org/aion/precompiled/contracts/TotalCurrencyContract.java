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
package org.aion.precompiled.contracts;

import java.math.BigInteger;
import org.aion.base.type.AionAddress;
import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.util.BIUtil;
import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.crypto.ed25519.Ed25519Signature;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.api.interfaces.Address;
import org.aion.base.vm.IDataWord;

/** A pre-compiled contract for retrieving and updating the total amount of currency. */
public class TotalCurrencyContract extends StatefulPrecompiledContract {
    // set to a default cost for now, this will need to be adjusted
    private static final long COST = 21000L;

    private Address address;
    private Address ownerAddress;

    /**
     * Constructs a new TotalCurrencyContract.
     *
     * @param track
     * @param address
     * @param ownerAddress
     */
    public TotalCurrencyContract(
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track,
            Address address,
            Address ownerAddress) {
        super(track);
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
    public TransactionResult execute(byte[] input, long nrg) {
        // query portion (pure)
        if (input.length == 1) {
            return queryNetworkBalance(input[0], nrg);
        } else {
            return executeUpdateTotalBalance(input, nrg);
        }
    }

    private TransactionResult queryNetworkBalance(int input, long nrg) {
        if (nrg < COST) {
            // TODO: should this cost be the same as updating state (probably not?)
            return new TransactionResult(ResultCode.OUT_OF_ENERGY, 0);
        }

        IDataWord balanceData = this.track.getStorageValue(this.address, new DataWord(input));
        return new TransactionResult(ResultCode.SUCCESS, nrg - COST, balanceData.getData());
    }

    private TransactionResult executeUpdateTotalBalance(byte[] input, long nrg) {
        // update total portion
        if (nrg < COST) {
            return new TransactionResult(ResultCode.OUT_OF_ENERGY, 0);
        }

        if (input.length < 114) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // process input data
        int offset = 0;
        DataWord chainId = new DataWord(input[0]);
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
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        byte[] payload = new byte[18];
        System.arraycopy(input, 0, payload, 0, 18);
        boolean b = ECKeyEd25519.verify(payload, sig.getSignature(), sig.getPubkey(null));

        if (!b) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // verify public key matches owner
        if (!this.ownerAddress.equals(AionAddress.wrap(sig.getAddress()))) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        // payload processing
        IDataWord totalCurr = this.track.getStorageValue(this.address, chainId);
        BigInteger totalCurrBI =
                totalCurr == null ? BigInteger.ZERO : BIUtil.toBI(totalCurr.getData());
        BigInteger value = BIUtil.toBI(amount);

        if (signum != 0x0 && signum != 0x1) {
            return new TransactionResult(ResultCode.FAILURE, 0);
        }

        BigInteger finalValue;
        if (signum == 0x0) {
            // addition
            finalValue = totalCurrBI.add(value);
        } else {
            // subtraction
            if (value.compareTo(totalCurrBI) > 0) {
                return new TransactionResult(ResultCode.FAILURE, 0);
            }

            finalValue = totalCurrBI.subtract(value);
        }

        // store result and successful exit
        this.track.addStorageRow(this.address, chainId, new DataWord(finalValue.toByteArray()));
        return new TransactionResult(ResultCode.SUCCESS, nrg - COST);
    }
}
