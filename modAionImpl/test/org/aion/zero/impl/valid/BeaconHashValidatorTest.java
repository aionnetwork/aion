package org.aion.zero.impl.valid;

import org.aion.base.AionTransaction;
import org.aion.mcf.blockchain.Block;
import org.aion.types.AionAddress;
import org.aion.util.bytes.ByteUtil;
import org.aion.zero.impl.blockchain.IAionBlockchain;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BeaconHashValidatorTest {
    @Test
    public void validateTxForBlockOnMainchainAndBeaconHashOnMainchain() {
        long fork050Number = 7;
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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);

        when(block.getNumber()).thenReturn(fork050Number - 1);
        assertWithMessage("any beacon hash should fail if block_number < fork050_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
        when(block.getNumber()).thenReturn(fork050Number);
        assertWithMessage("beacon hash on chain of new block should pass if block_number = fork050_number")
                .that(unit.validateTxForBlock(tx, block))
                .isTrue();
        when(block.getNumber()).thenReturn(fork050Number + 1);
        assertWithMessage("beacon hash on chain of new block should pass if block_number > fork050_number")
                .that(unit.validateTxForBlock(tx, block))
                .isTrue();
    }

    @Test
    public void validateTxForBlockOnMainchainAndBeaconHashNotOnMainchain() {
        long fork050Number = 13;
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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);

        when(block.getNumber()).thenReturn(fork050Number - 1);
        assertWithMessage("any beacon hash should fail if block_number < fork050_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
        when(block.getNumber()).thenReturn(fork050Number);
        assertWithMessage("beacon hash not on chain of new block should fail if block_number = fork050_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
        when(block.getNumber()).thenReturn(fork050Number + 1);
        assertWithMessage("beacon hash not on chain of new block should fail if block_number > fork050_number")
                .that(unit.validateTxForBlock(tx, block))
                .isFalse();
    }

    @Test
    public void validateTxForBlockOnMainchainAndBeaconHashNotGiven() {
        long fork050Number = 2;
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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);

        when(block.getNumber()).thenReturn(fork050Number - 1);
        assertWithMessage("validation should pass if beacon hash not present")
                .that(unit.validateTxForBlock(tx, block))
                .isTrue();
        when(block.getNumber()).thenReturn(fork050Number);
        assertWithMessage("validation should pass if beacon hash not present")
                .that(unit.validateTxForBlock(tx, block))
                .isTrue();
        when(block.getNumber()).thenReturn(fork050Number + 1);
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
        long fork050Number = 5;
        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        LinkedList<Block> sideChain = mockChain(fork050Number, 4, blockchain);

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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);
        assertWithMessage("beacon hash on chain of new block should pass if block_number = fork050_number")
                .that(unit.validateTxForBlock(tx, sideChain.get(0)))
                .isTrue();
        // not doing all the "if new block > fork050" and "new block < fork050" cases
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
        long fork050Number = 0;
        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        LinkedList<Block> sideChain = mockChain(0, 5, blockchain);
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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);
        assertWithMessage("beacon hash on chain of new block should pass if block_number = fork050_number")
                .that(unit.validateTxForBlock(tx, sideChain.get(0)))
                .isTrue();
        // not doing all the "if new block > fork050" and "new block < fork050" cases
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
        long fork050Number = 0;
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
        LinkedList<Block> sideChain = mockChain(0, 4, blockchain);

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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);
        assertWithMessage("beacon hash not on chain of new block should fail if block_number = fork050_number")
                .that(unit.validateTxForBlock(tx, sideChain.get(0)))
                .isFalse();
        // not doing all the "if new block > fork050" and "new block < fork050" cases
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
        long fork050Number = 0;
        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        byte[] nonExistentBlockhash = ByteUtil.hexStringToBytes(
                "0xbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbad0");

        LinkedList<Block> sideChain = mockChain(0, 4, blockchain);

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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);
        assertWithMessage("beacon hash not on chain of new block should fail if block_number = fork050_number")
                .that(unit.validateTxForBlock(tx, sideChain.get(0)))
                .isFalse();
    }

    @Test
    public void validateTxForPendingStateWhenBeaconHashOnMainchain() {
        long fork050Number = 7;
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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);

        when(bestBlock.getNumber()).thenReturn(fork050Number - 2);
        assertWithMessage("any beacon hash should fail for pending state if best_block_number+1 < fork050_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
        when(bestBlock.getNumber()).thenReturn(fork050Number - 1);
        assertWithMessage("mainchain hash should pass for pending state if best_block_number+1 = fork050_number")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
        when(bestBlock.getNumber()).thenReturn(fork050Number);
        assertWithMessage("mainchain hash should pass for pending state if best_block_number+1 > fork050_number")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
    }

    @Test
    public void validateTxForPendingStateWhenBeaconHashNotOnMainchain() {
        long fork050Number = 3;
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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);

        when(bestBlock.getNumber()).thenReturn(fork050Number - 2);
        assertWithMessage("any beacon hash should fail for pending state if best_block_number+1 < fork050_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
        when(bestBlock.getNumber()).thenReturn(fork050Number - 1);
        assertWithMessage("non-mainchain hash validation should fail for pending state if best_block_number+1 = fork050_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
        when(bestBlock.getNumber()).thenReturn(fork050Number);
        assertWithMessage("non-mainchain hash validation should fail for pending state if best_block_number+1 > fork050_number")
                .that(unit.validateTxForPendingState(tx))
                .isFalse();
    }

    @Test
    public void validateTxForPendingStateWithoutBeaconHash() {
        long fork050Number = 3;
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

        BeaconHashValidator unit = new BeaconHashValidator(blockchain, fork050Number);

        when(bestBlock.getNumber()).thenReturn(fork050Number - 2);
        assertWithMessage("validation should pass for pending state if beacon hash absent")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
        when(bestBlock.getNumber()).thenReturn(fork050Number - 1);
        assertWithMessage("validation should pass for pending state if beacon hash absent")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
        when(bestBlock.getNumber()).thenReturn(fork050Number);
        assertWithMessage("validation should pass for pending state if beacon hash absent")
                .that(unit.validateTxForPendingState(tx))
                .isTrue();
    }

    @Test
    public void isAfterFork050AllCombinations() {
        IAionBlockchain blockchain = mock(IAionBlockchain.class);

        assertWithMessage("isAfterFork050 should always be false if fork 0.5.0 disabled")
                .that(new BeaconHashValidator(blockchain, BeaconHashValidator.FORK_050_DISABLED)
                        .isAfterFork050(312l))
                .isFalse();
        assertWithMessage("isAfterFork050 should always be false if fork 0.5.0 disabled")
                .that(new BeaconHashValidator(blockchain, BeaconHashValidator.FORK_050_DISABLED)
                        .isAfterFork050(BeaconHashValidator.FORK_050_DISABLED))
                .isFalse();
        assertWithMessage("isAfterFork050 should be false if block_number < fork050_number")
                .that(new BeaconHashValidator(blockchain, 1337)
                        .isAfterFork050(1336))
                .isFalse();
        assertWithMessage("isAfterFork050 should be true if block_number = fork050_number")
                .that(new BeaconHashValidator(blockchain, 1337)
                        .isAfterFork050(1337))
                .isTrue();
        assertWithMessage("isAfterFork050 should be true if block_number > fork050_number")
                .that(new BeaconHashValidator(blockchain, 1337)
                        .isAfterFork050(1338))
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