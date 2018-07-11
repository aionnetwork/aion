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

import static org.aion.precompiled.contracts.ATB.BridgeDeserializer.parseAddressFromCall;
import static org.aion.precompiled.contracts.ATB.BridgeDeserializer.parseBundleRequest;
import static org.aion.precompiled.contracts.ATB.BridgeUtilities.getSignature;
import static org.aion.precompiled.contracts.ATB.BridgeUtilities.toSignature;

public class TokenBridgeContract extends StatefulPrecompiledContract {

    // function signatures
    // state changes
    private static final byte[] SIG_CHANGE_OWNER = toSignature("changeOwner(address)");
    private static final byte[] SIG_ACCEPT_OWNERSHIP = toSignature("acceptOwnership");
    private static final byte[] SIG_RING_INITIALIZE = toSignature("initializeRing(address[])");
    private static final byte[] SIG_ADD_RING_MEMBER = toSignature("addRingMember(address)");
    private static final byte[] SIG_REMOVE_RING_MEMBER = toSignature("removeRingMember(address)");
    private static final byte[] SIG_SUBMIT_BUNDLE = toSignature("submitBundle(address[],uint128[],bytes32[],bytes32[]");

    private static final int BUNDLE_PARAM_ACC = 0;
    private static final int BUNDLE_PARAM_VAL = 1;
    private static final int BUNDLE_PARAM_SIG = 2;

    // queries


    private final ExecutionContext context;
    private final IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track;

    private final BridgeStorageConnector connector;
    private final BridgeController controller;

    // some useful defaults
    private static ContractExecutionResult THROW = new ContractExecutionResult(ContractExecutionResult.ResultCode.FAILURE, 0);

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
        // as a preset, try to initialize before execution
        this.controller.initialize();
        // giant switch statement
        // TODO: could optimize by using a reverse hashmap, then switch on enums
        byte[] signature = getSignature(input);

        if (Arrays.equals(signature, SIG_CHANGE_OWNER)) {
            byte[] address = parseAddressFromCall(input);

            if (address == null)
                return THROW;

            this.controller.setNewOwner(this.context.caller().toBytes(), address);
        } else if (Arrays.equals(signature, SIG_ACCEPT_OWNERSHIP)) {
            this.controller.acceptOwnership(this.context.caller().toBytes());
        } else if (Arrays.equals(signature, SIG_RING_INITIALIZE)) {

            // TODO

        } else if (Arrays.equals(signature, SIG_ADD_RING_MEMBER)) {
            byte[] address = parseAddressFromCall(input);
            if (address == null)
                return THROW;

            BridgeController.ErrCode code = this.controller.ringAddMember(this.context.caller().toBytes(), address);

            if (code != BridgeController.ErrCode.NO_ERROR)
                return THROW;
        } else if (Arrays.equals(signature, SIG_REMOVE_RING_MEMBER)) {
            byte[] address = parseAddressFromCall(input);

            if (address == null)
                return THROW;
            BridgeController.ErrCode code = this.controller.ringRemoveMember(this.context.caller().toBytes(), address);
        } else if (Arrays.equals(signature, SIG_SUBMIT_BUNDLE)) {
            // TODO: possible attack vector, unsecure deserialization
            byte[][][] bundleRequests = parseBundleRequest(input);

            if (bundleRequests == null)
                return THROW;

            byte[][] signatures = bundleRequests[BUNDLE_PARAM_SIG];

            // more of a sanity check
            if (bundleRequests[0].length != bundleRequests[1].length)
                return THROW;

            int bundleLen = bundleRequests[0].length;
            BridgeBundle[] bundles = new BridgeBundle[bundleLen];
            for (int i = 0; i < bundleLen; i++) {
                bundles[i] = new BridgeBundle(
                        new BigInteger(1,bundleRequests[BUNDLE_PARAM_ACC][i]),
                        bundleRequests[BUNDLE_PARAM_VAL][i]);
            }
            BridgeController.ErrCode code = this.controller.
                    processBundles(this.context.caller().toBytes(), bundles, signatures);

            if (code != BridgeController.ErrCode.NO_ERROR)
                return THROW;
        }
        return THROW;
    }
}
