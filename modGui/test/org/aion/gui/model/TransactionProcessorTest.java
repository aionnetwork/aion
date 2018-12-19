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
package org.aion.gui.model;

import static org.aion.gui.model.ApiReturnCodes.r_tx_Init_VALUE;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.aion.api.IAionAPI;
import org.aion.api.type.ApiMsg;
import org.aion.api.type.Block;
import org.aion.api.type.BlockDetails;
import org.aion.api.type.MsgRsp;
import org.aion.api.type.TxArgs;
import org.aion.api.type.TxDetails;
import org.aion.base.type.AionAddress;
import org.aion.base.type.Hash256;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.connector.dto.BlockDTO;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.connector.dto.TransactionResponseDTO;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.TransactionDTO;
import org.aion.wallet.exception.ValidationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class TransactionProcessorTest {
    private IAionAPI api;
    private KernelConnection kernelConnection;
    private AccountManager accountManager;
    private BalanceRetriever balanceRetriever;
    private ExecutorService executor;
    private ConsoleManager consoleManager;

    @Before
    public void before() {
        this.api = mock(IAionAPI.class, RETURNS_DEEP_STUBS);
        this.kernelConnection = mock(KernelConnection.class);
        this.accountManager = mock(AccountManager.class, RETURNS_DEEP_STUBS);
        this.balanceRetriever = mock(BalanceRetriever.class);
        this.executor = Executors.newFixedThreadPool(1);
        this.consoleManager = mock(ConsoleManager.class);
        when(kernelConnection.getApi()).thenReturn(api);
    }

    @Test
    public void testProcessTxnsFromBlockAsyncWhenApiNotConnected() throws Exception {
        when(api.isConnected()).thenReturn(false);
        TransactionProcessor unit =
                new TransactionProcessor(
                        kernelConnection,
                        accountManager,
                        balanceRetriever,
                        executor,
                        consoleManager);
        Future<?> future =
                unit.processTxnsFromBlockAsync(
                        mock(BlockDTO.class), Collections.singleton("anyAddress"));
        future.get(); // wait for completion
        verifyZeroInteractions(accountManager);
        verifyZeroInteractions(balanceRetriever);
    }

    @Test
    public void testProcessTxnsFromBlockAsyncWhenAddressesEmpty() throws Exception {
        when(api.isConnected()).thenReturn(true);
        TransactionProcessor unit =
                new TransactionProcessor(
                        kernelConnection,
                        accountManager,
                        balanceRetriever,
                        executor,
                        consoleManager);
        Future<?> future =
                unit.processTxnsFromBlockAsync(mock(BlockDTO.class), Collections.emptySet());
        future.get(); // wait for completion
        verifyZeroInteractions(accountManager);
        verifyZeroInteractions(balanceRetriever);
    }

    @Test
    public void testProcessTxnsFromBlockAsync() throws Exception {
        when(api.isConnected()).thenReturn(true);
        String addressToProcess =
                "0xc0ffee1111111111111111111111111111111111111111111111111111111111";
        String latestSafeHash = "hash1234567890123456789012345678";

        String irrelevantAddress1 =
                "0x8badf00d99999999999999999999999999999999999999999999999999999999";
        String irrelevantAddress2 =
                "0x8badf00d88888888888888888888888888888888888888888888888888888888";
        String irrelevantHash = "1000000000000000000000000000000010000000000000000000000000000000";

        // set up mocks for the "getLatestBlock" call
        long apiLatestBlockN = 1337;
        Block latestBlock = mock(Block.class);
        ApiMsg blockNumberApiMsg = mock(ApiMsg.class);
        ApiMsg latestBlockApiMsg = mock(ApiMsg.class);
        when(api.getChain().blockNumber()).thenReturn(blockNumberApiMsg);
        when(blockNumberApiMsg.getObject()).thenReturn(apiLatestBlockN);
        when(api.getChain().getBlockByNumber(apiLatestBlockN)).thenReturn(latestBlockApiMsg);
        when(latestBlockApiMsg.getObject()).thenReturn(latestBlock);
        when(latestBlock.getNumber()).thenReturn(apiLatestBlockN);

        // set up mocks for latest safe block
        long latestSafeBlockN = 1201;
        BlockDTO latestSafeBlockDTO = new BlockDTO(latestSafeBlockN, latestSafeHash.getBytes());
        ApiMsg safeBlockApiMsg = mock(ApiMsg.class);
        Block latestSafeblock = mock(Block.class);
        when(api.getChain().getBlockByNumber(latestSafeBlockN)).thenReturn(safeBlockApiMsg);
        when(safeBlockApiMsg.getObject()).thenReturn(latestSafeblock);
        when(latestSafeblock.getHash()).thenReturn(new Hash256(latestSafeHash.getBytes()));

        // set up mocks for the "remove transactions from block" part
        TransactionDTO tNew = mock(TransactionDTO.class);
        TransactionDTO tOld = mock(TransactionDTO.class);
        when(tNew.getBlockNumber())
                .thenReturn(
                        latestSafeBlockN
                                - 1); // why are the ones with a lower block number considered
        // 'newer'?
        when(tOld.getBlockNumber()).thenReturn(latestSafeBlockN + 1);
        when(accountManager.getTransactions(addressToProcess))
                .thenReturn(
                        new HashSet<>() {
                            {
                                add(tNew);
                                add(tOld);
                            }
                        });

        // set up mocks for the "add new transactions" part
        ApiMsg blockDetailsApiMsg = mock(ApiMsg.class);
        when(api.getAdmin().getBlockDetailsByNumber(anyList())).thenReturn(blockDetailsApiMsg);
        List<BlockDetails> blockDetails =
                new LinkedList<>() {
                    {
                        // in real-life, this number of BlockDetails in this list would be the same
                        // as min(BLOCK_BATCH_SIZE, latest - safest)
                        // but for the purposes of this test, doesn't matter.  just check that
                        // whatever comes back from the API, we process
                        // correctly
                        BlockDetails.BlockDetailsBuilder btBuilder =
                                new BlockDetails.BlockDetailsBuilder()
                                        .bloom(ByteArrayWrapper.wrap(new byte[] {}))
                                        .extraData(ByteArrayWrapper.wrap(new byte[] {}))
                                        .solution(ByteArrayWrapper.wrap(new byte[] {}))
                                        .txDetails(null /* gets filled in later */)
                                        .parentHash(new Hash256(irrelevantHash))
                                        .hash(new Hash256(irrelevantHash))
                                        .nonce(new BigInteger("1"))
                                        .difficulty(new BigInteger("1"))
                                        .miner(new AionAddress(irrelevantAddress1))
                                        .stateRoot(new Hash256(irrelevantHash))
                                        .txTrieRoot(new Hash256(irrelevantHash))
                                        .receiptTxRoot(new Hash256(irrelevantHash))
                                        .totalDifficulty(new BigInteger("1"));
                        TxDetails.TxDetailsBuilder tdBuilder =
                                new TxDetails.TxDetailsBuilder()
                                        .from(null /* gets filled in later */)
                                        .to(null /* gets filled in later */)
                                        .contract(new AionAddress(irrelevantAddress2))
                                        .txHash(new Hash256(irrelevantHash))
                                        .value(new BigInteger("1"))
                                        .nonce(new BigInteger("1"))
                                        .data(ByteArrayWrapper.wrap(new byte[] {}));
                        add(
                                btBuilder
                                        .timestamp(724892l /*not used*/)
                                        .number(12l /*not used*/)
                                        .txDetails(
                                                Arrays.asList( // we'll use a unique number for
                                                        // value for verification later
                                                        tdBuilder
                                                                .from(
                                                                        new AionAddress(
                                                                                addressToProcess)) // should get added
                                                                .to(
                                                                        new AionAddress(
                                                                                irrelevantAddress1))
                                                                .value(new BigInteger("10000001"))
                                                                .createTxDetails(),
                                                        tdBuilder
                                                                .from(
                                                                        new AionAddress(
                                                                                irrelevantAddress1)) // should get
                                                                .to(
                                                                        new AionAddress(
                                                                                addressToProcess))
                                                                .value(new BigInteger("20000001"))
                                                                .createTxDetails(),
                                                        tdBuilder
                                                                .from(
                                                                        new AionAddress(
                                                                                irrelevantAddress1))
                                                                .to(
                                                                        new AionAddress(
                                                                                irrelevantAddress2))
                                                                .value(new BigInteger("30000001"))
                                                                .createTxDetails()))
                                        .createBlockDetails());
                    }
                };
        when(blockDetailsApiMsg.getObject()).thenReturn(blockDetails);

        // set up mocks for the "new safe block number" part
        int newSafeBlockNumber = 1037; // 1037 = latest - BLOCK_BATCH_SIZE = 1337 - 300
        ApiMsg newSafeBlockApiMsg = mock(ApiMsg.class);
        when(api.getChain().getBlockByNumber(newSafeBlockNumber)).thenReturn(newSafeBlockApiMsg);
        Block newSafeBlock = mock(Block.class);
        when(newSafeBlockApiMsg.getObject()).thenReturn(newSafeBlock);
        when(newSafeBlock.getNumber()).thenReturn((long) newSafeBlockNumber);
        when(newSafeBlock.getHash()).thenReturn(new Hash256(irrelevantHash));

        // run the test
        TransactionProcessor unit =
                new TransactionProcessor(
                        kernelConnection,
                        accountManager,
                        balanceRetriever,
                        executor,
                        consoleManager);
        Future<?> future =
                unit.processTxnsFromBlockAsync(
                        latestSafeBlockDTO, Collections.singleton(addressToProcess));
        future.get(); // block until completion

        // verify remove transactions from block part
        ArgumentCaptor<Collection> removedTxns = ArgumentCaptor.forClass(Collection.class);
        verify(accountManager).removeTransactions(eq(addressToProcess), removedTxns.capture());
        assertThat(removedTxns.getValue().size(), is(1));
        assertThat(removedTxns.getValue().iterator().next(), is(tOld));

        // verify the "getBlockDetailsConsumer" (add transactions) part
        ArgumentCaptor<List> newTxns = ArgumentCaptor.forClass(List.class);
        verify(accountManager).addTransactions(eq(addressToProcess), newTxns.capture());
        assertThat(newTxns.getValue().size(), is(2));
        assertThat(
                ((TransactionDTO) newTxns.getValue().get(0)).getValue(),
                is(blockDetails.get(0).getTxDetails().get(0).getValue()));
        assertThat(
                ((TransactionDTO) newTxns.getValue().get(1)).getValue(),
                is(blockDetails.get(0).getTxDetails().get(1).getValue()));

        // verify the "set new safe block" part
        ArgumentCaptor<BlockDTO> newSafeBlockDto = ArgumentCaptor.forClass(BlockDTO.class);
        verify(accountManager).updateLastSafeBlock(eq(addressToProcess), newSafeBlockDto.capture());
        assertThat(newSafeBlockDto.getValue().getNumber(), is(newSafeBlock.getNumber()));
        assertThat(newSafeBlockDto.getValue().getHash(), is(newSafeBlock.getHash().toBytes()));
    }

    @Test
    public void testGetLatestTransactions() {
        String addressToGet = "0xc0ffee1111111111111111111111111111111111111111111111111111111111";
        Set<TransactionDTO> accountManagerTransactions =
                Collections.singleton(mock(TransactionDTO.class));
        when(accountManager.getTransactions(addressToGet)).thenReturn(accountManagerTransactions);
        Set<String> accountManagerAddresses = Collections.singleton("someAddress");
        when(accountManager.getAddresses()).thenReturn(accountManagerAddresses);

        TransactionProcessor unit =
                new TransactionProcessor(
                        kernelConnection,
                        accountManager,
                        balanceRetriever,
                        executor,
                        consoleManager);
        Set<TransactionDTO> result = unit.getLatestTransactions(addressToGet);
        assertThat(result, is(accountManagerTransactions));

        // Leave accountManager.getOldestSafeBlock(addresses, nullSafeBlockFilter) to return null
        // so that processTransactionsFromBlock doesn't get called.  That path was already tested
        // in testProcessTxnsFromBlockAsync()

        // No way to ensure the executor has completed executing the Runnable that invokes the below
        // call
        // So commenting this out for now
        //        verify(accountManager).getOldestSafeBlock(ArgumentMatchers.any(),
        // ArgumentMatchers.any());
    }

    @Test(expected = ValidationException.class)
    public void testSendTransactionWhenDtoNull() throws Exception {
        TransactionProcessor unit =
                new TransactionProcessor(
                        kernelConnection,
                        accountManager,
                        balanceRetriever,
                        executor,
                        consoleManager);
        unit.sendTransaction(null);
    }

    @Test(expected = ValidationException.class)
    public void testSendTransactionWhenZeroBalance() throws Exception {
        String senderAddress = "0xc0ffee1111111111111111111111111111111111111111111111111111111111";
        SendTransactionDTO sendTransactionDTO = mock(SendTransactionDTO.class);
        when(sendTransactionDTO.validate()).thenReturn(true);
        BigInteger estimateValue = new BigInteger("0");
        when(sendTransactionDTO.estimateValue()).thenReturn(estimateValue);
        when(sendTransactionDTO.getFrom()).thenReturn(senderAddress);
        when(balanceRetriever.getBalance(senderAddress)).thenReturn(new BigInteger("0"));

        TransactionProcessor unit =
                new TransactionProcessor(
                        kernelConnection,
                        accountManager,
                        balanceRetriever,
                        executor,
                        consoleManager);
        unit.sendTransaction(sendTransactionDTO);
    }

    @Test
    public void testSendTransaction() throws Exception {
        when(api.isConnected()).thenReturn(true);
        String senderAddress = "0xa0c0ffee11111111111111111111111111111111111111111111111111111111";
        String receiverAddress =
                "0xa0cafecafe111111111111111111111111111111111111111111111111111111";
        BigInteger value = new BigInteger("10000000");
        BigInteger nrgPrice = new BigInteger("10000000000");
        long nrg = 21_000L;
        BigInteger latestNonce = new BigInteger("1337");

        // set up input
        SendTransactionDTO dto = new SendTransactionDTO();
        dto.setFrom(senderAddress);
        dto.setTo(receiverAddress);
        dto.setValue(value);
        dto.setNrgPrice(nrgPrice);
        dto.setNrg(nrg);
        when(balanceRetriever.getBalance(senderAddress))
                .thenReturn(new BigInteger("100000000000000000000"));

        // mock getting latest transaction nonce
        ApiMsg latestNonceApiMsg = mock(ApiMsg.class);
        when(api.getChain().getNonce(AionAddress.wrap(senderAddress)))
                .thenReturn(latestNonceApiMsg);
        when(latestNonceApiMsg.getObject()).thenReturn(latestNonce);

        // mock responding to the send request
        ApiMsg sendSignedTransactionApiMsg = mock(ApiMsg.class);
        byte[] privateKey = "pk".getBytes();
        when(accountManager.getAccount(senderAddress).getPrivateKey()).thenReturn(privateKey);
        ArgumentCaptor<TxArgs> argsArgumentCaptor = ArgumentCaptor.forClass(TxArgs.class);
        when(api.getTx()
                        .sendSignedTransaction(
                                argsArgumentCaptor.capture(),
                                eq(ByteArrayWrapper.wrap(privateKey))))
                .thenReturn(sendSignedTransactionApiMsg);

        MsgRsp sendSignedTransactionMsgResp = mock(MsgRsp.class);
        when(sendSignedTransactionApiMsg.getObject()).thenReturn(sendSignedTransactionMsgResp);

        when(sendSignedTransactionMsgResp.getStatus()).thenReturn((byte) r_tx_Init_VALUE);
        Hash256 txHash =
                new Hash256("0x8badf00d99999999999999999999999999999999999999999999999999999999");
        when(sendSignedTransactionMsgResp.getTxHash()).thenReturn(txHash);
        when(sendSignedTransactionMsgResp.getError()).thenReturn("NotAnError");

        TransactionProcessor unit =
                new TransactionProcessor(
                        kernelConnection,
                        accountManager,
                        balanceRetriever,
                        executor,
                        consoleManager);
        TransactionResponseDTO response = unit.sendTransaction(dto);
        assertThat(response.getStatus(), is((byte) r_tx_Init_VALUE));
        assertThat(response.getTxHash(), is(txHash));
        assertThat(response.getError(), is("NotAnError"));
    }
}
