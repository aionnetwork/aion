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
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.db.RepositoryCache;
import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.types.InternalTransaction;
import org.aion.types.InternalTransaction.RejectedStatus;

public class TokenBridgeContract implements PrecompiledContract, Transferable {

    private static final long ENERGY_CONSUME = 21000L;

    // queries

    private final PrecompiledTransactionContext context;
    private final RepositoryCache<AccountState, IBlockStoreBase<?, ?>> track;

    private final BridgeStorageConnector connector;
    private final BridgeController controller;
    private final AionAddress contractAddress;

    // some useful defaults
    // TODO: add passing returns (need more though on gas consumption)

    public TokenBridgeContract(
            @Nonnull final PrecompiledTransactionContext context,
            @Nonnull final RepositoryCache<AccountState, IBlockStoreBase<?, ?>> track,
            @Nonnull final AionAddress ownerAddress,
            @Nonnull final AionAddress contractAddress) {
        this.track = track;
        this.context = context;
        this.connector = new BridgeStorageConnector(this.track, contractAddress);
        this.controller =
                new BridgeController(
                        this.connector, this.context.getLogs(), contractAddress, ownerAddress);
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
                                    this.context.senderAddress.toByteArray(), address);

                    if (code != ErrCode.NO_ERROR) return fail();
                    return success();
                }
            case SIG_ACCEPT_OWNERSHIP:
                {
                    ErrCode code =
                            this.controller.acceptOwnership(
                                    this.context.senderAddress.toByteArray());
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
                                    this.context.senderAddress.toByteArray(), addressList);
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
                                    this.context.senderAddress.toByteArray(), address);
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
                                    this.context.senderAddress.toByteArray(), address);
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
                                    this.context.senderAddress.toByteArray(), address);

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
                                    this.context.senderAddress.toByteArray(),
                                    this.context.copyOfTransactionHash(),
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
        this.context.markAllInternalTransactionsAsRejected();
        return THROW;
    }

    private PrecompiledTransactionResult success() {
        long energyRemaining = this.context.transactionEnergy - ENERGY_CONSUME;
        return new PrecompiledTransactionResult(PrecompiledResultCode.SUCCESS, energyRemaining);
    }

    private PrecompiledTransactionResult success(@Nonnull final byte[] response) {
        // should always be positive
        long energyRemaining = this.context.transactionEnergy - ENERGY_CONSUME;
        assert energyRemaining >= 0;
        return new PrecompiledTransactionResult(
                PrecompiledResultCode.SUCCESS, energyRemaining, response);
    }

    private boolean isFromAddress(byte[] address) {
        if (address == null) return false;
        return this.context.senderAddress.equals(new AionAddress(address));
    }

    /**
     * Performs a transfer of value from one account to another, using a method that mimics to the
     * best of it's ability the {@code CALL} opcode. There are some assumptions that become
     * important for any caller to know:
     *
     * @implNote this method will check that the recipient account has no code. This means that we
     *     <b>cannot</b> do a transfer to any contract account.
     * @implNote assumes that the {@code fromValue} derived from the track will never be null.
     * @param dest recipient address
     * @param value to be sent (in base units)
     * @return {@code true} if value was performed, {@code false} otherwise
     */
    public PrecompiledTransactionResult transfer(
            @Nonnull final byte[] dest, @Nonnull final BigInteger value) {
        // some initial checks, treat as failure
        if (this.track.getBalance(this.contractAddress).compareTo(value) < 0)
            return new PrecompiledTransactionResult(PrecompiledResultCode.FAILURE, 0);

        // assemble an internal transaction
        AionAddress sender = this.contractAddress;
        AionAddress destination = new AionAddress(dest);
        BigInteger nonce = this.track.getNonce(sender);
        byte[] dataToSend = new byte[0];
        InternalTransaction tx =
                InternalTransaction.contractCallTransaction(
                        RejectedStatus.NOT_REJECTED,
                        sender,
                        destination,
                        nonce,
                        value,
                        dataToSend,
                        0L,
                        1L);

        // add transaction to result
        this.context.addInternalTransaction(tx);

        // increase the nonce and do the transfer without executing code
        this.track.incrementNonce(sender);
        this.track.addBalance(sender, value.negate());
        this.track.addBalance(destination, value);

        // construct result
        return new PrecompiledTransactionResult(PrecompiledResultCode.SUCCESS, 0);
    }
}
