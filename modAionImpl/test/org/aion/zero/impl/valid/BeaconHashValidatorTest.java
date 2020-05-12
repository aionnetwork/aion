package org.aion.zero.impl.valid;

import org.aion.base.AionTransaction;
import org.aion.zero.impl.types.Block;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.blockchain.IAionBlockchain;
import org.aion.zero.impl.forks.ForkUtility;
import org.junit.Test;

import java.util.LinkedList;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BeaconHashValidatorTest {
    @Test
    public void validateTxForBlockOnMainchainAndBeaconHashOnMainchain() {
        long unityForkNumber = 7;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        byte[] goodBlockHash = ByteUtil.hexStringToBytes("0xcafecafecafecafecafecafecafecafecafecafecafecafecafecafecafecafe");
        when(blockchain.isMainChain(goodBlockHash)).thenReturn(true);

        Block block = mock(Block.class);
        byte[] blockParent = ByteUtil.hexStringToBytes("0x1333333333333333333333333333333333333333333333333333333333333337");
        when(block.getParentHash()).thenReturn(blockParent);
        when(blockchain.isMainChain(blockParent)).thenReturn(true);

        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                goodBlockHash                               // beacon hash - exists in blockstore
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);

        when(block.getNumber()).thenReturn(unityForkNumber - 1);
        assertWithMessage("any beacon hash should fail if block_number < unityFork_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
        when(block.getNumber()).thenReturn(unityForkNumber);
        assertWithMessage("beacon hash on chain of new block should fail if block_number = unityFork_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
        when(block.getNumber()).thenReturn(unityForkNumber + 1);
        assertWithMessage("beacon hash on chain of new block should pass if block_number > unityFork_number")
                .that(unit.validateTxForBlock(tx, block))
                .isTrue();
    }

    @Test
    public void validateTxForBlockOnMainchainAndBeaconHashNotOnMainchain() {
        long unityForkNumber = 13;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        byte[] nonExistentBlockhash = ByteUtil.hexStringToBytes("0xbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbad0");
        when(blockchain.isMainChain(nonExistentBlockhash)).thenReturn(false);

        Block block = mock(Block.class);
        byte[] blockParent = ByteUtil.hexStringToBytes("0x1333333333333333333333333333333333333333333333333333333333333337");
        when(block.getParentHash()).thenReturn(blockParent);
        when(blockchain.isMainChain(blockParent)).thenReturn(true);

        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                nonExistentBlockhash                        // beacon hash - does not exist in blockstore
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);

        when(block.getNumber()).thenReturn(unityForkNumber - 1);
        assertWithMessage("any beacon hash should fail if block_number < unityFork_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
        when(block.getNumber()).thenReturn(unityForkNumber);
        assertWithMessage("beacon hash not on chain of new block should fail if block_number = unityFork_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
        when(block.getNumber()).thenReturn(unityForkNumber + 1);
        assertWithMessage("beacon hash not on chain of new block should fail if block_number > unityFork_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
    }

    @Test
    public void validateTxForBlockOnMainchainAndBeaconHashNotGiven() {
        long unityForkNumber = 2;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        Block block = mock(Block.class);
        byte[] blockParent = ByteUtil.hexStringToBytes("0x1333333333333333333333333333333333333333333333333333333333333337");
        when(block.getParentHash()).thenReturn(blockParent);
        when(blockchain.isMainChain(blockParent)).thenReturn(true);

        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                null                                        // beacon hash - not present
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);

        when(block.getNumber()).thenReturn(unityForkNumber - 1);
        assertWithMessage("validation should pass if beacon hash not present")
                .that(unit.validateTxForBlock(tx, block))
                .isTrue();
        when(block.getNumber()).thenReturn(unityForkNumber);
        assertWithMessage("validation should pass if beacon hash not present")
                .that(unit.validateTxForBlock(tx, block))
                .isTrue();
        when(block.getNumber()).thenReturn(unityForkNumber + 1);
        assertWithMessage("validation should pass if beacon hash not present")
                .that(unit.validateTxForBlock(tx, block))
                .isTrue();
    }

    @Test
    public void validateTxForBlockOnSidechainAndBeaconHashOnSideChain() {
        /*
         * Visualization of this case (x is the block of beacon hash , * is the new block):
         *
         *   o--o--o---o--o     main chain
         *         |
         *          \--x--o--*  side chain
         */
        long unityForkNumber = 5;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        LinkedList<Block> sideChain = mockChain(unityForkNumber, 4, blockchain);

        byte[] beaconHash = new byte[32];
        System.arraycopy(sideChain.get(3).getHash(), 0, beaconHash, 0, 32);
        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                beaconHash                                  // beacon hash
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);
        assertWithMessage("beacon hash on chain of new block should pass if block_number = unityFork_number")
                .that(unit.validateTxForBlock(tx, sideChain.get(0)))
                .isTrue();
        // not doing all the "if new block > unityFork" and "new block < unityFork" cases
        // for the side chain test cases because those paths already checked by the
        // mainchain tests
    }

    @Test
    public void validateTxForBlockOnSidechainAndBeaconHashOnMainchainBeforeSidechainFork() {
        /*
         * Visualization of this case (x is the block of beacon hash , * is the new block):
         *
         *   o--x--o---o--o     main chain
         *         |
         *          \--o--o--*  side chain
         */
        long unityForkNumber = 2;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        LinkedList<Block> sideChain = mockChain(unityForkNumber, 7, blockchain);
        when(blockchain.isMainChain(sideChain.get(2).getHash(), sideChain.get(2).getNumber())).thenReturn(true);
        when(blockchain.isMainChain(sideChain.get(4).getHash())).thenReturn(true);

        byte[] beaconHash = new byte[32];
        System.arraycopy(sideChain.get(4).getHash(), 0, beaconHash, 0, 32);

        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                beaconHash                                  // beacon hash
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);
        assertWithMessage("beacon hash on chain of new block should pass if block_number = unityFork_number")
                .that(unit.validateTxForBlock(tx, sideChain.get(0)))
                .isTrue();
        // not doing all the "if new block > unityFork" and "new block < unityFork" cases
        // for the side chain test cases because those paths already checked by the
        // mainchain tests
    }

    @Test
    public void validateTxForBlockOnSidechainAndBeaconHashOnMainchainAfterSidechainFork() {
        /*
         * Visualization of this case (x is the block of beacon hash , * is the new block):
         *
         *   o--o--o---x--o     main chain
         *         |
         *          \--o--o--*  side chain
         */
        long unityForkNumber = 2;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        // mocking up a block as if it were on the main chain but not the sidechain
        Block mainchainOnlyBlock = mock(Block.class);
        long mainchainOnlyBlockNum = 3l;
        byte[] mainchainOnlyBlockHash = ByteUtil.hexStringToBytes(
                "0xcafecafecafecafecafecafecafecafecafecafecafecafecafecafecafecafe");
        when(mainchainOnlyBlock.getNumber()).thenReturn(mainchainOnlyBlockNum);
        when(blockchain.isMainChain(mainchainOnlyBlockHash)).thenReturn(true);
        when(blockchain.getBlockByHash(mainchainOnlyBlockHash)).thenReturn(mainchainOnlyBlock);

        // need to set up the side chain so that its head block number is > mainchainOnlyBlockNum
        LinkedList<Block> sideChain = mockChain(unityForkNumber, 6, blockchain);

        byte[] beaconHash = new byte[32];
        System.arraycopy(mainchainOnlyBlockHash, 0, beaconHash, 0, 32);
        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                beaconHash                                  // beacon hash
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);
        assertWithMessage("beacon hash not on chain of new block should fail if block_number = unityFork_number")
                .that(unit.validateTxForBlock(tx, sideChain.get(0)))
                .isFalse();
        // not doing all the "if new block > unityFork" and "new block < unityFork" cases
        // for the side chain test cases because those paths already checked by the
        // mainchain tests
    }

    @Test
    public void validateTxForBlockOnSidechainAndBeaconHashNotInDb() {
        /*
         * Visualization of this case (beacon hash not in any chain, * is the new block):
         *
         *   o--o--o---o--o     main chain
         *         |
         *          \--o--o--*  side chain
         */
        long unityForkNumber = 2;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        byte[] nonExistentBlockhash = ByteUtil.hexStringToBytes(
                "0xbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbad0");

        LinkedList<Block> sideChain = mockChain(unityForkNumber, 6, blockchain);

        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                nonExistentBlockhash                        // beacon hash
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);
        assertWithMessage("beacon hash not on chain of new block should fail if block_number = unityFork_number")
                .that(unit.validateTxForBlock(tx, sideChain.get(0)))
                .isFalse();
    }

    @Test
    public void validateTxForPendingStateWhenBeaconHashOnMainchain() {
        long unityForkNumber = 7;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);
        Block bestBlock = mock(Block.class);

        byte[] goodBlockHash = ByteUtil.hexStringToBytes(
                "0xcafecafecafecafecafecafecafecafecafecafecafecafecafecafecafecafe");
        when(blockchain.isMainChain(goodBlockHash)).thenReturn(true);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                goodBlockHash                               // beacon hash - exists in blockstore
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);

        when(bestBlock.getNumber()).thenReturn(unityForkNumber - 2);
        assertWithMessage("any beacon hash should fail for pending state if best_block_number+1 < unityFork_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
        when(bestBlock.getNumber()).thenReturn(unityForkNumber - 1);
        assertWithMessage("mainchain hash should fail for pending state if best_block_number+1 == unityFork_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
        when(bestBlock.getNumber()).thenReturn(unityForkNumber);
        assertWithMessage("mainchain hash should pass for pending state if best_block_number+1 > unityFork_number")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
    }

    @Test
    public void validateTxForPendingStateWhenBeaconHashNotOnMainchain() {
        long unityForkNumber = 3;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);
        Block bestBlock = mock(Block.class);

        byte[] badBlockhash = ByteUtil.hexStringToBytes(
                "0xbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbad0");
        when(blockchain.isMainChain(badBlockhash)).thenReturn(false);
        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                badBlockhash                                // beacon hash - does not exist in blockstore
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);

        when(bestBlock.getNumber()).thenReturn(unityForkNumber - 2);
        assertWithMessage("any beacon hash should fail for pending state if best_block_number+1 < unityFork_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
        when(bestBlock.getNumber()).thenReturn(unityForkNumber - 1);
        assertWithMessage("non-mainchain hash validation should fail for pending state if best_block_number+1 = unityFork_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
        when(bestBlock.getNumber()).thenReturn(unityForkNumber);
        assertWithMessage("non-mainchain hash validation should fail for pending state if best_block_number+1 > unityFork_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
    }

    @Test
    public void validateTxForPendingStateWithoutBeaconHash() {
        long unityForkNumber = 3;
        ForkUtility forkUtility = new ForkUtility();
        forkUtility.enableUnityFork(unityForkNumber);

        IAionBlockchain blockchain = mock(IAionBlockchain.class);
        Block bestBlock = mock(Block.class);

        when(blockchain.getBestBlock()).thenReturn(bestBlock);

        AionTransaction tx = AionTransaction.createWithoutKey(
                new byte[] { 1 },                           // nonce - any val
                new AionAddress(ByteUtil.hexStringToBytes(  // sender - any val
                        "0xa000000000000000000000000000000000000000000000000000000000000000")),
                new AionAddress(ByteUtil.hexStringToBytes(  // destination - any val
                        "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
                new byte[] { 1 },                           // value - any val
                new byte[] { },                             // data - any val
                1l,                                         // energyLimit - any val
                1l,                                         // energyPrice - any val
                (byte) 1,                                   // type - any val
                null                                        // beacon hash - absent
        );

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, forkUtility);

        when(bestBlock.getNumber()).thenReturn(unityForkNumber - 2);
        assertWithMessage("validation should pass for pending state if beacon hash absent")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
        when(bestBlock.getNumber()).thenReturn(unityForkNumber - 1);
        assertWithMessage("validation should pass for pending state if beacon hash absent")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
        when(bestBlock.getNumber()).thenReturn(unityForkNumber);
        assertWithMessage("validation should pass for pending state if beacon hash absent")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
    }

    @Test
    public void isUnityForkActiveAllCombinations() {
        // unity fork disabled
        ForkUtility forkUtility = new ForkUtility();
        assertWithMessage("isUnityForkActive should always be false if fork 0.5.0 disabled")
                .that(forkUtility.isUnityForkActive(312l))
                .isFalse();
        assertWithMessage("isUnityForkActive should always be false if fork 0.5.0 disabled")
                .that(forkUtility.isUnityForkActive(Long.MAX_VALUE))
                .isFalse();

        // unity fork enabled after block 1337
        forkUtility.enableUnityFork(1337);
        assertWithMessage("isUnityForkActive should be false if block_number < unityFork_number")
                .that(forkUtility.isUnityForkActive(1336))
                .isFalse();
        assertWithMessage("isUnityForkActive should be false if block_number = unityFork_number")
                .that(forkUtility.isUnityForkActive(1337))
                .isFalse();
        assertWithMessage("isUnityForkActive should be true if block_number > unityFork_number")
                .that(forkUtility.isUnityForkActive(1338))
                .isTrue();
    }

    /**
     * Create mocks of {@link Block}s that behave as if they were one chain (i.e one
     * is the parent of the preceding one); set up the given mockBlockchain to behave
     * as if those blocks are in the blockchain.
     *
     * @return mocked blocks that form a chain with the head as first element */
    private static LinkedList<Block> mockChain(long initialNumber,
                                               int howMany,
                                               IAionBlockchain mockBlockchain) {
        LinkedList<Block> ret = new LinkedList<>();
        byte[] lastHash = null;

        for(long ix = initialNumber; ix < initialNumber + howMany; ++ix) {
            Block block = mock(Block.class);
            byte[] hash = ByteUtil.hexStringToBytes(String.format("0x%064x", ix));
            when(mockBlockchain.getBlockByHash(hash)).thenReturn(block);
            when(block.getNumber()).thenReturn((long)ix);
            when(block.getHash()).thenReturn(hash);
            when(block.getParentHash()).thenReturn(lastHash);

            lastHash = hash;
            ret.push(block);
        }

        return ret;
    }
}