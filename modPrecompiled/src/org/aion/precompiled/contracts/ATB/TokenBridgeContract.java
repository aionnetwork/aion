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

package org.aion.precompiled.contracts.ATB;

import static org.aion.precompiled.contracts.ATB.BridgeDeserializer.parseAddressFromCall;
import static org.aion.precompiled.contracts.ATB.BridgeDeserializer.parseAddressList;
import static org.aion.precompiled.contracts.ATB.BridgeDeserializer.parseBundleRequest;
import static org.aion.precompiled.contracts.ATB.BridgeDeserializer.parseDwordFromCall;
import static org.aion.precompiled.contracts.ATB.BridgeUtilities.booleanToResultBytes;
import static org.aion.precompiled.contracts.ATB.BridgeUtilities.getSignature;
import static org.aion.precompiled.contracts.ATB.BridgeUtilities.intToResultBytes;
import static org.aion.precompiled.contracts.ATB.BridgeUtilities.orDefaultDword;

import java.math.BigInteger;
import javax.annotation.Nonnull;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.zero.types.AionInternalTx;

public class TokenBridgeContract extends StatefulPrecompiledContract implements Transferable {

    private static final long ENERGY_CONSUME = 21000L;

    // queries

    private final TransactionContext context;
    private final IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> track;

    private final BridgeStorageConnector connector;
    private final BridgeController controller;
    private final AionAddress contractAddress;

    // some useful defaults
    // TODO: add passing returns (need more though on gas consumption)

    public TokenBridgeContract(
            @Nonnull final TransactionContext context,
            @Nonnull final IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> track,
            @Nonnull final AionAddress ownerAddress,
            @Nonnull final AionAddress contractAddress) {
        super(track);
        this.context = context;
        this.track = track;
        this.connector = new BridgeStorageConnector(this.track, contractAddress);
        this.controller =
                new BridgeController(
                        this.connector,
                        this.context.getSideEffects(),
                        contractAddress,
                        ownerAddress);
        this.controller.setTransferable(this);

        this.contractAddress = contractAddress;
    }

    public BridgeController getController() {
        return this.controller;
    }

    public BridgeStorageConnector getConnector() {
        return this.connector;
    }

    public boolean isInitialized() {
        return this.connector.getInitialized();
    }

    @Override
    public PrecompiledTransactionResult execute(@Nonnull final byte[] input, final long nrgLimit) {
        if (nrgLimit < ENERGY_CONSUME) return THROW;

        // as a preset, try to initialize before execution
        // this should be placed before the 0 into return, rationale is that we want to
        // activate the contract the first time the owner interacts with it. Which is
        // exactly what sending the contract currency entails
        this.controller.initialize();

        // acts as a pseudo fallback function
        if (input.length == 0) {
            return success();
        }

        byte[] signature = getSignature(input);
        if (signature == null) return THROW;

        BridgeFuncSig sig = BridgeFuncSig.getSignatureEnum(signature);
        if (sig == null) return THROW;

        switch (sig) {
            case SIG_CHANGE_OWNER:
                {
                    if (!isFromAddress(this.connector.getOwner())) return fail();

                    byte[] address = parseAddressFromCall(input);
                    if (address == null) return fail();

                    ErrCode code =
                            this.controller.setNewOwner(
                                    this.context.getSenderAddress().toBytes(), address);

                    if (code != ErrCode.NO_ERROR) return fail();
                    return success();
                }
            case SIG_ACCEPT_OWNERSHIP:
                {
                    ErrCode code =
                            this.controller.acceptOwnership(
                                    this.context.getSenderAddress().toBytes());
                    if (code != ErrCode.NO_ERROR) return fail();
                    return success();
                }
            case SIG_RING_INITIALIZE:
                {
                    if (!isFromAddress(this.connector.getOwner())) return fail();

                    byte[][] addressList = parseAddressList(input);

                    if (addressList == null) return fail();

                    ErrCode code =
                            this.controller.ringInitialize(
                                    this.context.getSenderAddress().toBytes(), addressList);
                    if (code != ErrCode.NO_ERROR) return fail();
                    return success();
                }
            case SIG_RING_ADD_MEMBER:
                {
                    if (!isFromAddress(this.connector.getOwner())) return fail();

                    byte[] address = parseAddressFromCall(input);
                    if (address == null) return fail();

                    ErrCode code =
                            this.controller.ringAddMember(
                                    this.context.getSenderAddress().toBytes(), address);
                    if (code != ErrCode.NO_ERROR) return fail();
                    return success();
                }
            case SIG_RING_REMOVE_MEMBER:
                {
                    if (!isFromAddress(this.connector.getOwner())) return fail();

                    byte[] address = parseAddressFromCall(input);

                    if (address == null) return fail();

                    ErrCode code =
                            this.controller.ringRemoveMember(
                                    this.context.getSenderAddress().toBytes(), address);
                    if (code != ErrCode.NO_ERROR) return fail();
                    return success();
                }
            case SIG_SET_RELAYER:
                {
                    if (!isFromAddress(this.connector.getOwner())) return fail();

                    byte[] address = parseAddressFromCall(input);
                    if (address == null) return fail();
                    ErrCode code =
                            this.controller.setRelayer(
                                    this.context.getSenderAddress().toBytes(), address);

                    if (code != ErrCode.NO_ERROR) return fail();
                    return success();
                }
            case SIG_SUBMIT_BUNDLE:
                {
                    if (!isFromAddress(this.connector.getRelayer())) return fail();

                    BundleRequestCall bundleRequests = parseBundleRequest(input);

                    if (bundleRequests == null) return fail();

                    // ATB-4, as part of the changes we now
                    // pass in the transactionHash of the call
                    // into the contract, this will be logged so that
                    // we can refer to it at a later time.
                    BridgeController.ProcessedResults results =
                            this.controller.processBundles(
                                    this.context.getSenderAddress().toBytes(),
                                    this.context.getTransactionHash(),
                                    bundleRequests.blockHash,
                                    bundleRequests.bundles,
                                    bundleRequests.signatures);

                    if (results.controllerResult != ErrCode.NO_ERROR) return fail();
                    return success();
                }
            case PURE_OWNER:
                return success(orDefaultDword(this.connector.getOwner()));
            case PURE_NEW_OWNER:
                return success(orDefaultDword(this.connector.getNewOwner()));
            case PURE_RING_LOCKED:
                return success(booleanToResultBytes(this.connector.getRingLocked()));
            case PURE_MIN_THRESH:
                return success(intToResultBytes(this.connector.getMinThresh()));
            case PURE_MEMBER_COUNT:
                return success(intToResultBytes(this.connector.getMemberCount()));
            case PURE_RING_MAP:
                {
                    byte[] address2 = parseAddressFromCall(input);
                    if (address2 == null) return fail();
                    return success(booleanToResultBytes(this.connector.getActiveMember(address2)));
                }
            case PURE_ACTION_MAP:
                byte[] bundleHash = parseDwordFromCall(input);
                if (bundleHash == null) return fail();
                return success(orDefaultDword(this.connector.getBundle(bundleHash)));
            case PURE_RELAYER:
                // ATB-5 Add in relayer getter
                return success(orDefaultDword(this.connector.getRelayer()));
            default:
                return fail();
        }
        // throw new RuntimeException("should never reach here");
    }

    private static final PrecompiledTransactionResult THROW =
            new PrecompiledTransactionResult(PrecompiledResultCode.FAILURE, 0);

    private PrecompiledTransactionResult fail() {
        this.context.getSideEffects().markAllInternalTransactionsAsRejected();
        return THROW;
    }

    private PrecompiledTransactionResult success() {
        long energyRemaining = this.context.getTransactionEnergyLimit() - ENERGY_CONSUME;
        return new PrecompiledTransactionResult(PrecompiledResultCode.SUCCESS, energyRemaining);
    }

    private PrecompiledTransactionResult success(@Nonnull final byte[] response) {
        // should always be positive
        long energyRemaining = this.context.getTransactionEnergyLimit() - ENERGY_CONSUME;
        assert energyRemaining >= 0;
        return new PrecompiledTransactionResult(
                PrecompiledResultCode.SUCCESS, energyRemaining, response);
    }

    private boolean isFromAddress(byte[] address) {
        if (address == null) return false;
        return this.context.getSenderAddress().equals(AionAddress.wrap(address));
    }

    /**
     * Performs a transfer of value from one account to another, using a method that mimics to the
     * best of it's ability the {@code CALL} opcode. There are some assumptions that become
     * important for any caller to know:
     *
     * @implNote this method will check that the recipient account has no code. This means that we
     *     <b>cannot</b> do a transfer to any contract account.
     * @implNote assumes that the {@code fromValue} derived from the track will never be null.
     * @param to recipient address
     * @param value to be sent (in base units)
     * @return {@code true} if value was performed, {@code false} otherwise
     */
    public PrecompiledTransactionResult transfer(
            @Nonnull final byte[] to, @Nonnull final BigInteger value) {
        // some initial checks, treat as failure
        if (this.track.getBalance(this.contractAddress).compareTo(value) < 0)
            return new PrecompiledTransactionResult(PrecompiledResultCode.FAILURE, 0);

        // assemble an internal transaction
        AionAddress from = this.contractAddress;
        AionAddress recipient = new AionAddress(to);
        BigInteger nonce = this.track.getNonce(from);
        DataWord valueToSend = new DataWord(value);
        byte[] dataToSend = new byte[0];
        AionInternalTx tx = newInternalTx(from, recipient, nonce, valueToSend, dataToSend, "call");

        // add transaction to result
        this.context.getSideEffects().addInternalTransaction(tx);

        // increase the nonce and do the transfer without executing code
        this.track.incrementNonce(from);
        this.track.addBalance(from, value.negate());
        this.track.addBalance(recipient, value);

        // construct result
        return new PrecompiledTransactionResult(PrecompiledResultCode.SUCCESS, 0);
    }

    /**
     * Creates a new internal transaction.
     *
     * <p>NOTE: copied from {@code Callback}
     */
    private AionInternalTx newInternalTx(
            AionAddress from,
            AionAddress to,
            BigInteger nonce,
            DataWord value,
            byte[] data,
            String note) {
        byte[] parentHash = context.getTransactionHash();
        int depth = context.getTransactionStackDepth();
        int index = context.getSideEffects().getInternalTransactions().size();

        return new AionInternalTx(
                parentHash,
                depth,
                index,
                new DataWord(nonce).getData(),
                from,
                to,
                value.getData(),
                data,
                note);
    }
}
