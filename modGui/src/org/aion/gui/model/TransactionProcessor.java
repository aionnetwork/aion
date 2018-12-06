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

import static org.aion.gui.model.ApiReturnCodes.r_tx_Included_VALUE;
import static org.aion.gui.model.ApiReturnCodes.r_tx_Init_VALUE;
import static org.aion.gui.model.ApiReturnCodes.r_tx_NewPending_VALUE;
import static org.aion.gui.model.ApiReturnCodes.r_tx_Pending_VALUE;
import static org.aion.gui.model.ApiReturnCodes.r_tx_Recved_VALUE;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.aion.api.type.Block;
import org.aion.api.type.BlockDetails;
import org.aion.api.type.MsgRsp;
import org.aion.api.type.TxArgs;
import org.aion.api.type.TxDetails;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.TypeConverter;
import org.aion.gui.events.EventPublisher;
import org.aion.log.AionLoggerFactory;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.connector.dto.BlockDTO;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.connector.dto.TransactionResponseDTO;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.TransactionDTO;
import org.aion.wallet.exception.ValidationException;
import org.slf4j.Logger;

/** Provides */
public class TransactionProcessor extends AbstractAionApiClient {
    private final AccountManager accountManager;
    private final ExecutorService backgroundExecutor;
    private final BalanceRetriever balanceRetriever;
    private final ConsoleManager consoleManager;

    private static final int BLOCK_BATCH_SIZE = 300;
    private static final List<Integer> ACCEPTED_TRANSACTION_RESPONSE_STATUSES =
            Arrays.asList(
                    r_tx_Init_VALUE,
                    r_tx_Recved_VALUE,
                    r_tx_NewPending_VALUE,
                    r_tx_Pending_VALUE,
                    r_tx_Included_VALUE);
    private static final Logger LOG = AionLoggerFactory.getLogger(org.aion.log.LogEnum.GUI.name());

    /**
     * Constructor
     *
     * @param kernelConnection
     * @param accountManager
     * @param balanceRetriever
     * @param executor
     */
    public TransactionProcessor(
            KernelConnection kernelConnection,
            AccountManager accountManager,
            BalanceRetriever balanceRetriever,
            ExecutorService executor,
            ConsoleManager consoleManager) {
        super(kernelConnection);
        this.accountManager = accountManager;
        this.balanceRetriever = balanceRetriever;
        this.backgroundExecutor = executor;
        this.consoleManager = consoleManager;
    }

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    public TransactionProcessor(
            KernelConnection kernelConnection,
            AccountManager accountManager,
            BalanceRetriever balanceRetriever) {
        this(
                kernelConnection,
                accountManager,
                balanceRetriever,
                Executors.newFixedThreadPool(getCores()),
                new ConsoleManager());
    }

    public Future<?> processTxnsFromBlockAsync(
            final BlockDTO lastSafeBlock, final Set<String> addresses) {
        return backgroundExecutor.submit(
                () -> processTransactionsFromBlock(lastSafeBlock, addresses));
    }

    public Set<TransactionDTO> getLatestTransactions(final String address) {
        try {
            backgroundExecutor.submit(this::processTransactionsFromOldestRegisteredSafeBlock).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        // TODO: consider waiting on the submit Future before returning
        return accountManager.getTransactions(address);
    }

    public void processTransactionsOnReconnectAsync() {
        backgroundExecutor.submit(
                () -> {
                    final Set<String> addresses = accountManager.getAddresses();
                    final BlockDTO oldestSafeBlock =
                            accountManager.getOldestSafeBlock(addresses, i -> {});
                    processTransactionsFromBlock(oldestSafeBlock, addresses);
                });
    }

    private void processTransactionsFromBlock(
            final BlockDTO lastSafeBlock, final Set<String> addresses) {
        if (!apiIsConnected()) {
            LOG.trace(
                    "Will not process transactions from block: {} for addresses: {} because API is disconnected or no addresses",
                    lastSafeBlock,
                    addresses);
            return;
        }
        if (addresses.isEmpty()) {
            return;
        }

        final long latest = getLatestBlock().getNumber();
        final long previousSafe = lastSafeBlock != null ? lastSafeBlock.getNumber() : 0;
        LOG.debug(
                "Processing transactions from block: {} to block: {}, for addresses: {}",
                previousSafe,
                latest,
                addresses);
        if (previousSafe > 0) {
            final Block lastSupposedSafe = getBlock(previousSafe);
            if (!Arrays.equals(lastSafeBlock.getHash(), (lastSupposedSafe.getHash().toBytes()))) {
                EventPublisher.fireFatalErrorEncountered(
                        "A re-organization happened too far back. Please restart Wallet!");
            }
            removeTransactionsFromBlock(addresses, previousSafe);
        }
        for (long i = latest; i > previousSafe; i -= BLOCK_BATCH_SIZE) {
            List<Long> blockBatch =
                    LongStream.iterate(i, j -> j - 1)
                            .limit(BLOCK_BATCH_SIZE)
                            .boxed()
                            .collect(Collectors.toList());
            List<BlockDetails> blk = getBlockDetailsByNumbers(blockBatch);
            blk.forEach(getBlockDetailsConsumer(addresses));
        }
        final long newSafeBlockNumber = latest - BLOCK_BATCH_SIZE;
        final Block newSafe;
        if (newSafeBlockNumber > 0) {
            newSafe = getBlock(newSafeBlockNumber);
            for (String address : addresses) {
                accountManager.updateLastSafeBlock(
                        address, new BlockDTO(newSafe.getNumber(), newSafe.getHash().toBytes()));
            }
        }
        LOG.debug("finished processing for addresses: {}", addresses);
    }

    private Block getLatestBlock() {
        final Block block;
        if (apiIsConnected()) {
            final long latest = callApi(api -> api.getChain().blockNumber()).getObject();
            block = callApi(api -> api.getChain().getBlockByNumber(latest)).getObject();
        } else {
            block = null;
        }
        return block;
    }

    private Block getBlock(final long blockNumber) {
        return callApi(api -> api.getChain().getBlockByNumber(blockNumber)).getObject();
    }

    private void removeTransactionsFromBlock(final Set<String> addresses, final long previousSafe) {
        for (String address : addresses) {
            final List<TransactionDTO> txs =
                    new ArrayList<TransactionDTO>(accountManager.getTransactions(address));
            final Iterator<TransactionDTO> iterator = txs.iterator();
            final List<TransactionDTO> oldTxs = new ArrayList<>();
            while (iterator.hasNext()) {
                final TransactionDTO t = iterator.next();
                if (t.getBlockNumber() > previousSafe) {
                    oldTxs.add(t);
                }
            }
            accountManager.removeTransactions(address, oldTxs);
        }
    }

    private List<BlockDetails> getBlockDetailsByNumbers(final List<Long> numbers) {
        return callApi(api -> api.getAdmin().getBlockDetailsByNumber(numbers)).getObject();
    }

    private Consumer<BlockDetails> getBlockDetailsConsumer(final Set<String> addresses) {
        return blockDetails -> {
            if (blockDetails != null) {
                final long timestamp = blockDetails.getTimestamp();
                final long blockNumber = blockDetails.getNumber();
                for (final String address : addresses) {
                    final List<TransactionDTO> newTxs =
                            blockDetails
                                    .getTxDetails()
                                    .stream()
                                    .filter(
                                            t ->
                                                    TypeConverter.toJsonHex(t.getFrom().toString())
                                                                    .equals(address)
                                                            || TypeConverter.toJsonHex(
                                                                            t.getTo().toString())
                                                                    .equals(address))
                                    .map(t -> mapTransaction(t, timestamp, blockNumber))
                                    .collect(Collectors.toList());
                    accountManager.addTransactions(address, newTxs);
                }
            }
        };
    }

    private TransactionDTO mapTransaction(
            final TxDetails transaction, final long timeStamp, final long blockNumber) {
        if (transaction == null) {
            return null;
        }
        return new TransactionDTO(
                transaction.getFrom().toString(),
                transaction.getTo().toString(),
                transaction.getTxHash().toString(),
                TypeConverter.StringHexToBigInteger(
                        TypeConverter.toJsonHex(transaction.getValue())),
                transaction.getNrgConsumed(),
                transaction.getNrgPrice(),
                timeStamp,
                blockNumber,
                transaction.getNonce(),
                transaction.getTxIndex());
    }

    public TransactionResponseDTO sendTransaction(final SendTransactionDTO dto)
            throws ValidationException {
        if (dto == null || !dto.validate()) {
            throw new ValidationException("Invalid transaction request data");
        }
        if (dto.estimateValue().compareTo(getBalance(dto.getFrom())) >= 0) {
            throw new ValidationException("Insufficient funds");
        }
        return sendTransactionInternal(dto);
    }

    private BigInteger getBalance(final String address) {
        return balanceRetriever.getBalance(address);
    }

    private TransactionResponseDTO sendTransactionInternal(final SendTransactionDTO dto) {
        final BigInteger latestTransactionNonce = getLatestTransactionNonce(dto.getFrom());
        TxArgs txArgs =
                new TxArgs.TxArgsBuilder()
                        .from(new AionAddress(TypeConverter.toJsonHex(dto.getFrom())))
                        .to(new AionAddress(TypeConverter.toJsonHex(dto.getTo())))
                        .value(dto.getValue())
                        .nonce(latestTransactionNonce)
                        .data(new ByteArrayWrapper(dto.getData()))
                        .nrgPrice(dto.getNrgPrice())
                        .nrgLimit(dto.getNrg())
                        .createTxArgs();
        final MsgRsp response;

        consoleManager.addLog(
                "Sending transaction",
                ConsoleManager.LogType.TRANSACTION,
                ConsoleManager.LogLevel.INFO);
        response =
                callApi(
                                api ->
                                        api.getTx()
                                                .sendSignedTransaction(
                                                        txArgs,
                                                        new ByteArrayWrapper(
                                                                (accountManager.getAccount(
                                                                                dto.getFrom()))
                                                                        .getPrivateKey())))
                        .getObject();

        final TransactionResponseDTO transactionResponseDTO = mapTransactionResponse(response);
        final int responseStatus = transactionResponseDTO.getStatus();
        if (!ACCEPTED_TRANSACTION_RESPONSE_STATUSES.contains(responseStatus)) {
            accountManager.addTimedOutTransaction(dto);
        }
        return transactionResponseDTO;
    }

    private BigInteger getLatestTransactionNonce(final String address) {
        if (apiIsConnected()) {
            return callApi(api -> api.getChain().getNonce(AionAddress.wrap(address))).getObject();
        } else {
            return BigInteger.ZERO;
        }
    }

    private TransactionResponseDTO mapTransactionResponse(final MsgRsp response) {
        return new TransactionResponseDTO(
                response.getStatus(), response.getTxHash(), response.getError());
    }

    private void processTransactionsFromOldestRegisteredSafeBlock() {
        final Set<String> addresses = accountManager.getAddresses();
        final Consumer<Iterator<String>> nullSafeBlockFilter = Iterator::remove;
        final BlockDTO oldestSafeBlock =
                accountManager.getOldestSafeBlock(addresses, nullSafeBlockFilter);
        if (oldestSafeBlock != null) {
            processTransactionsFromBlock(oldestSafeBlock, addresses);
        }
    }

    private static int getCores() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores > 1) {
            cores = cores / 2;
        }
        return cores;
    }
}
