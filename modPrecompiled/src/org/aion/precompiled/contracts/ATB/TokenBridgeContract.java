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

public class TokenBridgeContract extends StatefulPrecompiledContract implements Transferrable {

    private static final int BUNDLE_PARAM_ACC = 0;
    private static final int BUNDLE_PARAM_VAL = 1;
    private static final int BUNDLE_PARAM_SIG = 2;

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
        this.controller = new BridgeController(this.connector, this.context.result(), contractAddress, ownerAddress);
        this.contractAddress = contractAddress;
    }

    @Override
    public ExecutionResult execute(@Nonnull final byte[] input, final long nrgLimit) {
        if (nrgLimit < ENERGY_CONSUME)
            return THROW;

        // as a preset, try to initialize before execution
        this.controller.initialize();

        byte[] signature = getSignature(input);
        if (signature == null)
            return THROW;

        BridgeFuncSig sig = BridgeFuncSig.getSignatureEnum(signature);
        if (sig == null)
            return THROW;

        switch(sig) {
            case SIG_CHANGE_OWNER: {
                byte[] address = parseAddressFromCall(input);
                if (address == null)
                    return fail();
                ErrCode code = this.controller.setNewOwner(this.context.caller().toBytes(), address);

                if (code != ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_ACCEPT_OWNERSHIP: {
                ErrCode code = this.controller.acceptOwnership(this.context.caller().toBytes());
                if (code !=  ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_RING_INITIALIZE: {
                byte[][] addressList = parseAddressList(input);

                if (addressList == null)
                    return fail();

                ErrCode code = this.controller.ringInitialize(this.context.caller().toBytes(), addressList);
                if (code != ErrCode.NO_ERROR)
                    return fail();
                return success();
            }
            case SIG_RING_ADD_MEMBER: {
                byte[] address = parseAddressFromCall(input);
                if (address == null)
                    return THROW;

                ErrCode code = this.controller.ringAddMember(this.context.caller().toBytes(), address);
                if (code != ErrCode.NO_ERROR)
                    return THROW;
                break;
            }
            case SIG_RING_REMOVE_MEMBER: {
                byte[] address = parseAddressFromCall(input);

                if (address == null)
                    return THROW;

                ErrCode code = this.controller.ringRemoveMember(this.context.caller().toBytes(), address);
                if (code != ErrCode.NO_ERROR)
                    return THROW;
                break;
            }
            case SIG_SUBMIT_BUNDLE: {
                // TODO: possible attack vector, unsecure deserialization
                byte[][][] bundleRequests = parseBundleRequest(input);

                if (bundleRequests == null)
                    return fail();

                byte[][] signatures = bundleRequests[BUNDLE_PARAM_SIG];

                // more of a sanity check
                if (bundleRequests[0].length != bundleRequests[1].length)
                    return fail();

                int bundleLen = bundleRequests[0].length;
                BridgeBundle[] bundles = new BridgeBundle[bundleLen];
                for (int i = 0; i < bundleLen; i++) {
                    bundles[i] = new BridgeBundle(
                            new BigInteger(1,bundleRequests[BUNDLE_PARAM_ACC][i]),
                            bundleRequests[BUNDLE_PARAM_VAL][i]);
                }
                BridgeController.ProcessedResults results = this.controller.
                        processBundles(this.context.caller().toBytes(), bundles, signatures);
                if (results.controllerResult != ErrCode.NO_ERROR)
                    return fail();

                // at this point we know that the execution was successful
                break;
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
                byte[] address = parseAddressFromCall(input);
                if (address == null)
                    return fail();
                return success(booleanToResultBytes(this.connector.getActiveMember(address)));
            }
            case PURE_ACTION_MAP:
                byte[] bundleHash = parseDwordFromCall(input);
                if (bundleHash == null)
                    return fail();
                return success(booleanToResultBytes(this.connector.getBundle(bundleHash)));
            default:
                return fail();
        }
        throw new RuntimeException("should never reach here");
    }

    private static ExecutionResult THROW =
            new ExecutionResult(ExecutionResult.ResultCode.FAILURE, 0);
    private ExecutionResult fail() {
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
                this.context.blockDifficulty(),
                this.context.result());
    }

    /**
     * Performs a transfer of value from one account to another, using a method that
     * mimicks to the best of it's ability the {@code CALL} opcode. There are some
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

        byte[] code = this.track.getCode(new Address(to));
        if (code != null && code.length != 0)
            return new ExecutionResult(ExecutionResult.ResultCode.FAILURE, 0);

        // otherwise prepare for a transfer
        ExecutionContext innerContext = assembleContext(to, value);
        IRepositoryCache cache = this.track.startTracking();
        // is a flush really needed here if we have no failure conditions?
        AionInternalTx tx = newInternalTx(innerContext);
        cache.addBalance(new Address(to), value);
        cache.addBalance(this.contractAddress, value.negate());
        cache.flush();
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
    private AionInternalTx newInternalTx(ExecutionContext context) {

        byte[] parentHash = context.transactionHash();
        int deep = context.depth();
        int idx = context.result().getInternalTransactions().size();

        return new AionInternalTx(parentHash,
                deep,
                idx,
                new DataWord(this.track.getNonce(context.caller())).getData(),
                context.caller(),
                context.address(),
                context.callValue().getData(),
                context.callData(),
                "call");
    }
}
