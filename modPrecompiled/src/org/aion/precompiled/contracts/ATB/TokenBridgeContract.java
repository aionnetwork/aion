package org.aion.precompiled.contracts.ATB;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.precompiled.ContractExecutionResult;
import org.aion.precompiled.type.StatefulPrecompiledContract;
import org.aion.vm.ExecutionContext;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.Arrays;

import static org.aion.precompiled.contracts.ATB.BridgeDeserializer.*;
import static org.aion.precompiled.contracts.ATB.BridgeUtilities.*;

public class TokenBridgeContract extends StatefulPrecompiledContract {

    private static final int BUNDLE_PARAM_ACC = 0;
    private static final int BUNDLE_PARAM_VAL = 1;
    private static final int BUNDLE_PARAM_SIG = 2;

    private static final long ENERGY_CONSUME = 21000L;

    // queries


    private final ExecutionContext context;
    private final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track;

    private final BridgeStorageConnector connector;
    private final BridgeController controller;

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
        this.controller = new BridgeController(this.connector, contractAddress, ownerAddress);
    }

    @Override
    public ContractExecutionResult execute(@Nonnull final byte[] input, final long nrgLimit) {
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
                ErrCode code = this.controller.
                        processBundles(this.context.caller().toBytes(), bundles, signatures);

                if (code != ErrCode.NO_ERROR)
                    return fail();
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

    private static ContractExecutionResult THROW =
            new ContractExecutionResult(ContractExecutionResult.ResultCode.FAILURE, 0);
    private ContractExecutionResult fail() {
        return THROW;
    }

    private ContractExecutionResult success() {
        long energyRemaining = this.context.nrgLimit() - ENERGY_CONSUME;
        return new ContractExecutionResult(ContractExecutionResult.ResultCode.SUCCESS, energyRemaining);
    }

    private ContractExecutionResult success(@Nonnull final byte[] response) {
        // should always be positive
        long energyRemaining = this.context.nrgLimit() - ENERGY_CONSUME;
        assert energyRemaining >= 0;
        return new ContractExecutionResult(ContractExecutionResult.ResultCode.SUCCESS, energyRemaining, response);
    }
}
