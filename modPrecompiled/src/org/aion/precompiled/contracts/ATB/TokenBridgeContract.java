package org.aion.precompiled.contracts.ATB;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.zero.types.AionInternalTx;

import javax.annotation.Nonnull;
import java.math.BigInteger;

import static org.aion.precompiled.contracts.ATB.BridgeDeserializer.*;
import static org.aion.precompiled.contracts.ATB.BridgeUtilities.*;

public class TokenBridgeContract extends StatefulPrecompiledContract implements Transferable {

    private static final long ENERGY_CONSUME = 21000L;

    // queries

    private final ExecutionContext context;
    private final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track;

    private final BridgeStorageConnector connector;
    private final BridgeController controller;
    private final Address contractAddress;

    // some useful defaults
    // TODO: add passing returns (need more though on gas consumption)

    public TokenBridgeContract(@Nonnull final ExecutionContext context,
                               @Nonnull final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track,
                               @Nonnull final Address ownerAddress,
                               @Nonnull final Address contractAddress) {
        super(track);
        this.context = context;
        this.track = track;
        this.connector = new BridgeStorageConnector(this.track, contractAddress);
        this.controller = new BridgeController(this.connector, this.context.helper(), contractAddress, ownerAddress);
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
    public ExecutionResult execute(@Nonnull final byte[] input, final long nrgLimit) {
        if (nrgLimit < ENERGY_CONSUME)
            return THROW;

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
        if (signature == null)
            return THROW;

        BridgeFuncSig sig = BridgeFuncSig.getSignatureEnum(signature);
        if (sig == null)
            return THROW;

        switch(sig) {
            case SIG_CHANGE_OWNER: {
                if (!isFromAddress(this.connector.getOwner()))
                    return fail();

                byte[] address = parseAddressFromCall(input);
                if (address == null)
                    return fail();

                ErrCode code = this.controller.setNewOwner(this.context.sender().toBytes(), address);

                if (code != ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_ACCEPT_OWNERSHIP: {
                ErrCode code = this.controller.acceptOwnership(this.context.sender().toBytes());
                if (code !=  ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_RING_INITIALIZE: {
                if (!isFromAddress(this.connector.getOwner()))
                    return fail();

                byte[][] addressList = parseAddressList(input);

                if (addressList == null)
                    return fail();

                ErrCode code = this.controller.ringInitialize(this.context.sender().toBytes(), addressList);
                if (code != ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_RING_ADD_MEMBER: {
                if (!isFromAddress(this.connector.getOwner()))
                    return fail();

                byte[] address = parseAddressFromCall(input);
                if (address == null)
                    return fail();

                ErrCode code = this.controller.ringAddMember(this.context.sender().toBytes(), address);
                if (code != ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_RING_REMOVE_MEMBER: {
                if (!isFromAddress(this.connector.getOwner()))
                    return fail();

                byte[] address = parseAddressFromCall(input);

                if (address == null)
                    return fail();

                ErrCode code = this.controller.ringRemoveMember(this.context.sender().toBytes(), address);
                if (code != ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_SET_RELAYER: {
                if (!isFromAddress(this.connector.getOwner()))
                    return fail();

                byte[] address = parseAddressFromCall(input);
                if (address == null)
                    return fail();
                ErrCode code = this.controller.setRelayer(this.context.sender().toBytes(), address);

                if (code != ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_SUBMIT_BUNDLE: {
                if (!isFromAddress(this.connector.getRelayer()))
                    return fail();

                // TODO: possible attack vector, unsecure deserialization
                BundleRequestCall bundleRequests = parseBundleRequest(input);

                if (bundleRequests == null)
                    return fail();

                // ATB-4, as part of the changes we now
                // pass in the transactionHash of the call
                // into the contract, this will be logged so that
                // we can refer to it at a later time.
                BridgeController.ProcessedResults results = this.controller.processBundles(
                        this.context.sender().toBytes(),
                        this.context.transactionHash(),
                        bundleRequests.blockHash,
                        bundleRequests.bundles,
                        bundleRequests.signatures);

                if (results.controllerResult != ErrCode.NO_ERROR)
                    return fail();
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
            case PURE_RING_MAP: {
                byte[] address2 = parseAddressFromCall(input);
                if (address2 == null)
                    return fail();
                return success(booleanToResultBytes(this.connector.getActiveMember(address2)));
            }
            case PURE_ACTION_MAP:
                byte[] bundleHash = parseDwordFromCall(input);
                if (bundleHash == null)
                    return fail();
                return success(orDefaultDword(this.connector.getBundle(bundleHash)));
            case PURE_RELAYER:
                // ATB-5 Add in relayer getter
                return success(orDefaultDword(this.connector.getRelayer()));
            default:
                return fail();
        }
        // throw new RuntimeException("should never reach here");
    }

    private static final ExecutionResult THROW =
            new ExecutionResult(ExecutionResult.ResultCode.FAILURE, 0);
    private ExecutionResult fail() {
        this.context.helper().rejectInternalTransactions();
        return THROW;
    }

    private ExecutionResult success() {
        long energyRemaining = this.context.nrgLimit() - ENERGY_CONSUME;
        return new ExecutionResult(ExecutionResult.ResultCode.SUCCESS, energyRemaining);
    }

    private ExecutionResult success(@Nonnull final byte[] response) {
        // should always be positive
        long energyRemaining = this.context.nrgLimit() - ENERGY_CONSUME;
        assert energyRemaining >= 0;
        return new ExecutionResult(ExecutionResult.ResultCode.SUCCESS, energyRemaining, response);
    }


    private boolean isFromAddress(byte[] address) {
        if (address == null)
            return false;
        return this.context.sender().equals(Address.wrap(address));
    }

    private ExecutionContext assembleContext(@Nonnull final byte[] recipient,
                                             @Nonnull final BigInteger value) {
        return new ExecutionContext(
                this.context.transactionHash(),
                new Address(recipient),
                this.context.origin(),
                this.contractAddress,
                this.context.nrgPrice(),
                ENERGY_CONSUME,
                new DataWord(value),
                ByteUtil.EMPTY_BYTE_ARRAY,
                this.context.depth() + 1,
                0,
                0,
                this.context.blockCoinbase(),
                this.context.blockNumber(),
                this.context.blockTimestamp(),
                this.context.blockNrgLimit(),
                this.context.blockDifficulty());
    }

    /**
     * Performs a transfer of value from one account to another, using a method that
     * mimics to the best of it's ability the {@code CALL} opcode. There are some
     * assumptions that become important for any caller to know:
     *
     * @implNote this method will check that the recipient account has no code. This
     * means that we <b>cannot</b> do a transfer to any contract account.
     *
     * @implNote assumes that the {@code fromValue} derived from the track will never
     * be null.
     *
     * @param to recipient address
     * @param value to be sent (in base units)
     * @return {@code true} if value was performed, {@code false} otherwise
     */
    public ExecutionResult transfer(@Nonnull final byte[] to,
                                    @Nonnull final BigInteger value) {
        // some initial checks, treat as failure
        if (this.track.getBalance(this.contractAddress).compareTo(value) < 0)
            return new ExecutionResult(ExecutionResult.ResultCode.FAILURE, 0);

        // otherwise prepare for a transfer
        ExecutionContext innerContext = assembleContext(to, value);

        AionInternalTx tx = newInternalTx(innerContext);
        this.context.helper().addInternalTransaction(tx);

        IRepositoryCache cache = this.track;
        cache.addBalance(new Address(to), value);
        cache.addBalance(this.contractAddress, value.negate());
        return new ExecutionResult(ExecutionResult.ResultCode.SUCCESS, 0);
    }

    /**
     * Derived from {@link org.aion.fastvm.Callback}
     *
     * TODO: best if transaction execution path is unified
     * TODO: what is "call"?
     *
     * @param context execution context to generate internal transaction
     * @return {@code internal transaction}
     */
    @SuppressWarnings("JavadocReference")
    private AionInternalTx newInternalTx(ExecutionContext context) {

        byte[] parentHash = context.transactionHash();
        int deep = context.depth();
        int idx = context.helper().getInternalTransactions().size();

        return new AionInternalTx(parentHash,
                deep,
                idx,
                new DataWord(this.track.getNonce(context.sender())).getData(),
                context.sender(),
                context.address(),
                context.callValue().getData(),
                context.callData(),
                "call");
    }
}
