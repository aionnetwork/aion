package org.aion.precompiled.contracts.ATB;

import static org.aion.precompiled.contracts.ATB.BridgeController.ProcessedResults.processError;
import static org.aion.precompiled.contracts.ATB.BridgeController.ProcessedResults.processSuccess;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.HashUtil;
import org.aion.crypto.ISignature;
import org.aion.crypto.SignatureFac;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.PrecompiledUtilities;
import org.aion.vm.ExecutionHelper;
import org.aion.vm.ExecutionResult;

/**
 * Contains the functional components of the Aion Token Bridge, this class is removed
 * from concerns regarding communicate with outside world (external) and communicating
 * with the database.
 */
public class BridgeController {

    private final BridgeStorageConnector connector;
    private ExecutionHelper result;
    private final Address contractAddress;
    private final Address ownerAddress;
    private Transferrable transferrable;

    public BridgeController(@Nonnull final BridgeStorageConnector storageConnector,
                            @Nonnull final ExecutionHelper helper,
                            @Nonnull final Address contractAddress,
                            @Nonnull final Address ownerAddress) {
        this.connector = storageConnector;
        this.result = helper;
        this.contractAddress = contractAddress;
        this.ownerAddress = ownerAddress;
    }

    public void setTransferrable(Transferrable transferrable) {
        this.transferrable = transferrable;
    }

    /**
     * Loads in the stored state from the underlying repository
     */
    public void initialize() {
        if (this.connector.getInitialized())
            return;
        // otherwise initialize
        this.connector.setOwner(ownerAddress.toBytes());
    }

    // owner

    // guards/modifiers

    /**
     * Checks whether the given address is the owner of the contract or not.
     * @implNote assumes the address is non-null and properly formatted
     *
     * @param address to be checked for ownership
     * @return {@code true} if address is the owner {@code false} otherwise
     */
    private boolean isOwner(@Nonnull final byte[] address) {
        byte[] owner = this.connector.getOwner();
        if (owner == null)
            return false;
        return Arrays.equals(owner, address);
    }

    /**
     * Checks whether the given address is the intended newOwner of the contract
     * @param address to be checked for new ownership
     * @return {@code} true if the address is the intended new owner {@code false} otherwise
     */
    private boolean isNewOwner(@Nonnull final byte[] address) {
        byte[] newOwner = this.connector.getNewOwner();
        if (newOwner == null)
            return false;
        return Arrays.equals(newOwner, address);
    }

    // logic
    public ErrCode setNewOwner(@Nonnull final byte[] caller,
                               @Nonnull final byte[] newOwner) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;
        this.connector.setNewOwner(newOwner);
        return ErrCode.NO_ERROR;
    }

    public ErrCode acceptOwnership(@Nonnull final byte[] caller) {
        if (!isNewOwner(caller))
            return ErrCode.NOT_NEW_OWNER;
        this.connector.setOwner(caller);
        this.connector.setNewOwner(ByteUtil.EMPTY_WORD);

        emitChangedOwner(caller);
        return ErrCode.NO_ERROR;
    }

    // end owner

    // relayer

    public boolean isRelayer(@Nonnull final byte[] caller) {
        byte[] relayer = this.connector.getRelayer();
        if (relayer == null)
            return false;
        return Arrays.equals(caller, this.connector.getRelayer());
    }

    public ErrCode setRelayer(@Nonnull final byte[] caller,
                              @Nonnull final byte[] newOwner) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;
        this.connector.setRelayer(newOwner);
        return ErrCode.NO_ERROR;
    }

    // end relayer

    // ring

    private static int thresholdRatio(final int in) {
        return Math.max((in * 2) / 3, 1);
    }

    private boolean isRingLocked() {
        return this.connector.getRingLocked();
    }

    private boolean isRingMember(@Nonnull final byte[] address) {
        return this.connector.getActiveMember(address);
    }

    public ErrCode ringInitialize(@Nonnull final byte[] caller,
                                  @Nonnull final byte[][] members) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;

        if (isRingLocked())
            return ErrCode.RING_LOCKED;

        int thresh = thresholdRatio(members.length);

        this.connector.setMemberCount(members.length);
        this.connector.setMinThresh(thresh);

        for (byte[] m : members) {
            this.connector.setActiveMember(m, true);
        }
        this.connector.setRingLocked(true);
        return ErrCode.NO_ERROR;
    }

    public ErrCode ringAddMember(@Nonnull final byte[] caller,
                                 @Nonnull final byte[] address) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;

        if (!isRingLocked())
            return ErrCode.RING_NOT_LOCKED;

        if (isRingMember(address))
            return ErrCode.RING_MEMBER_EXISTS;

        int memberCount = this.connector.getMemberCount() + 1;
        int thresh = thresholdRatio(memberCount);

        this.connector.setActiveMember(address, true);
        this.connector.setMemberCount(memberCount);
        this.connector.setMinThresh(thresh);

        emitAddMember(address);
        return ErrCode.NO_ERROR;
    }

    public ErrCode ringRemoveMember(@Nonnull final byte[] caller,
                                    @Nonnull final byte[] address) {
        if (!isOwner(caller))
            return ErrCode.NOT_OWNER;

        if (!isRingLocked())
            return ErrCode.RING_NOT_LOCKED;

        if (!isRingMember(address))
            return ErrCode.RING_MEMBER_NOT_EXISTS;

        int memberCount = this.connector.getMemberCount() - 1;
        int thresh = thresholdRatio(memberCount);

        this.connector.setActiveMember(address, false);
        this.connector.setMemberCount(memberCount);
        this.connector.setMinThresh(thresh);

        emitRemoveMember(address);
        return ErrCode.NO_ERROR;
    }

    // end ring

    // bridge

    private boolean isWithinSignatureBounds(int signatureLength) {
        int thresh = this.connector.getMinThresh();
        return signatureLength >= thresh && signatureLength <= this.connector.getMemberCount();
    }

    /**
     * Assume bundleHash is not from external source, but rather
     * calculated on our side (on the I/O layer), when {@link BridgeBundle} list
     * was being created.
     *
     * @param bundles
     * @return {@code ErrCode} indicating whether operation was successful
     *
     * @implNote assume the inputs are properly formatted
     *
     * @implNote assumes the bundles will always have positive transfer values
     * so the check is omitted. Technically it is possible to have an account
     * transfer for {@code 0} amount of tokens. Assume that the bridge will
     * take care that does not occur
     *
     * @implNote does not currently place restrictions on size of bundles.
     * Since we're not charging cost for bundles we may want to look into
     * charging these properly.
     */
    public ProcessedResults processBundles(@Nonnull final byte[] caller,
                                           @Nonnull final byte[] blockHash,
                                           @Nonnull final BridgeBundle[] bundles,
                                           @Nonnull final byte[][] signatures) {
        if (!isRingLocked())
            return processError(ErrCode.RING_NOT_LOCKED);

        if (!isRelayer(caller))
            return processError(ErrCode.NOT_RELAYER);

        if (!isWithinSignatureBounds(signatures.length))
            return processError(ErrCode.INVALID_SIGNATURE_BOUNDS);

        // verify bundleHash
        byte[] hash = HashUtil.h256(blockHash);
        for (BridgeBundle b : bundles) {
            hash = HashUtil.h256(ByteUtil.merge(hash, b.recipient, b.transferValue.toByteArray()));
        }

        int signed = 0;
        for (byte[] sigBytes : signatures) {
            ISignature sig = SignatureFac.fromBytes(sigBytes);
            if (SignatureFac.verify(hash, sig) && this.connector.getActiveMember(sig.getAddress())) {
                signed++;
            }
        }

        int minThresh = this.connector.getMinThresh();
        if (signed < minThresh)
            return processError(ErrCode.NOT_ENOUGH_SIGNATURES);

        // otherwise, we're clear to proceed with transfers
        List<ExecutionResult> results = new ArrayList<>();
        for (BridgeBundle b : bundles) {

            /*
             * Tricky here, we distinguish between two types of failures here:
             *
             * 1) A balance failure indicates we've failed to load the bridge with
             * enough currency to execute, this means the whole transaction should
             * fail and cause the bridge to exit
             *
             * 2) Any other failure indicates that either the contract had code,
             * which means the contract is now considered null.
             *
             * For how this is documented, check the {@code Transferrable}
             * interface documentation.
             */
            ExecutionResult result = null;
            if ((result = transferrable.transfer(b.recipient, b.transferValue)).getResultCode()
                    == ExecutionResult.ResultCode.FAILURE)
                // no need to return list of transactions, since they're all being dropped
                return processError(ErrCode.INVALID_TRANSFER);

            // otherwise if transfer was successful
            if (result.getResultCode() == ExecutionResult.ResultCode.SUCCESS)
                emitDistributed(b.recipient, b.transferValue);
            results.add(result);
        }
        emitProcessedBundle(hash);
        return processSuccess(ErrCode.NO_ERROR, results);
    }

    private void addLog(List<byte[]> topics) {
        this.result.addLog(new Log(this.contractAddress, topics, null));
    }

    private void emitAddMember(@Nonnull final byte[] address) {
        List<byte[]> topics = Arrays.asList(BridgeEventSig.ADD_MEMBER.getHashed(), address);
        addLog(topics);
    }

    private void emitRemoveMember(@Nonnull final byte[] address) {
        List<byte[]> topics = Arrays.asList(BridgeEventSig.REMOVE_MEMBER.getHashed(), address);
        addLog(topics);
    }

    private void emitChangedOwner(@Nonnull final byte[] ownerAddress) {
        List<byte[]> topics = Arrays.asList(BridgeEventSig.CHANGE_OWNER.getHashed(), ownerAddress);
        addLog(topics);
    }

    // events
    private void emitDistributed(@Nonnull final byte[] recipient,
                                 @Nonnull final BigInteger value) {
        List<byte[]> topics = Arrays.asList(BridgeEventSig.DISTRIBUTED.getHashed(),
                recipient, PrecompiledUtilities.pad(value.toByteArray(), 32));
        addLog(topics);
    }

    private void emitProcessedBundle(@Nonnull final byte[] bundleHash) {
        List<byte[]> topics = Arrays.asList(BridgeEventSig.PROCESSED_BUNDLE.getHashed(), bundleHash);
        addLog(topics);
    }

    protected static class ProcessedResults {
        public ErrCode controllerResult;
        public List<ExecutionResult> internalResults;

        private ProcessedResults(ErrCode code, List<ExecutionResult> internalResults) {
            this.controllerResult = code;
            this.internalResults = internalResults;
        }

        static ProcessedResults processError(ErrCode code) {
            return new ProcessedResults(code, null);
        }

        static ProcessedResults processSuccess(ErrCode code, List<ExecutionResult> results) {
            return new ProcessedResults(code, results);
        }
    }
}
