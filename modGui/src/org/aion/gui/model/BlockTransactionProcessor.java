package org.aion.gui.model;

import org.aion.api.impl.internal.Message;
import org.aion.api.type.Block;
import org.aion.api.type.BlockDetails;
import org.aion.api.type.MsgRsp;
import org.aion.api.type.TxArgs;
import org.aion.api.type.TxDetails;
import org.aion.base.type.Address;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.TypeConverter;
import org.aion.gui.events.EventPublisher;
import org.aion.gui.model.dto.BalanceDto;
import org.aion.wallet.account.AccountManager;
import org.aion.wallet.connector.dto.BlockDTO;
import org.aion.wallet.connector.dto.SendTransactionDTO;
import org.aion.wallet.connector.dto.TransactionResponseDTO;
import org.aion.wallet.console.ConsoleManager;
import org.aion.wallet.dto.TransactionDTO;
import org.aion.wallet.exception.ValidationException;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class BlockTransactionProcessor extends AbstractAionApiClient {
    private final AccountManager accountManager;
    private final ExecutorService backgroundExecutor;
    private final BalanceDto balanceDto;

    private static final int BLOCK_BATCH_SIZE = 300;

    private static final Logger LOG = org.aion.log.AionLoggerFactory
            .getLogger(org.aion.log.LogEnum.GUI.name());

    /**
     * Constructor
     *
     * @param kernelConnection connection containing the API instance to interact with
     */
    public BlockTransactionProcessor(KernelConnection kernelConnection,
                                     AccountManager accountManager,
                                     BalanceDto balanceDto) {
        super(kernelConnection);
        this.accountManager = accountManager;
        this.backgroundExecutor = Executors.newFixedThreadPool(getCores()); // TODO should be injected
        this.balanceDto = balanceDto;
    }

    private AccountManager getAccountManager() {
        return accountManager;
    }

    public void processTxnsFromBlockAsync(final BlockDTO lastSafeBlock, final Set<String> addresses) {
        backgroundExecutor.submit(() -> processTxnsFromBlockAsync(lastSafeBlock, addresses));
    }

    // original version from ApiBlockchainConnector.java of aion_ui
    private void processTransactionsFromBlock(final BlockDTO lastSafeBlock, final Set<String> addresses) {
        if(!apiIsConnected()) {
            LOG.warn("WIll not process transactions from block: {} for addresses: {} because API is disconnected or no addresses",
                    lastSafeBlock, addresses);
            return;
        }

        if (!addresses.isEmpty()) {
            final long latest = getLatestBlock().getNumber();
            final long previousSafe = lastSafeBlock != null ? lastSafeBlock.getNumber() : 0;
            LOG.debug("Processing transactions from block: {} to block: {}, for addresses: {}", previousSafe, latest, addresses);
            if (previousSafe > 0) {
                final Block lastSupposedSafe = getBlock(previousSafe);
                if (!Arrays.equals(lastSafeBlock.getHash(), (lastSupposedSafe.getHash().toBytes()))) {
                    EventPublisher.fireFatalErrorEncountered("A re-organization happened too far back. Please restart Wallet!");
                }
                removeTransactionsFromBlock(addresses, previousSafe);
            }
            for (long i = latest; i > previousSafe; i -= BLOCK_BATCH_SIZE) {
                List<Long> blockBatch = LongStream.iterate(i, j -> j - 1).limit(BLOCK_BATCH_SIZE).boxed().collect(Collectors.toList());
                List<BlockDetails> blk = getBlockDetailsByNumbers(blockBatch);
                blk.forEach(getBlockDetailsConsumer(addresses));
            }
            final long newSafeBlockNumber = latest - BLOCK_BATCH_SIZE;
            final Block newSafe;
            if (newSafeBlockNumber > 0) {
                newSafe = getBlock(newSafeBlockNumber);
                for (String address : addresses) {
                    getAccountManager().updateLastSafeBlock(address, new BlockDTO(newSafe.getNumber(), newSafe.getHash().toBytes()));
                }
            }
            LOG.debug("finished processing for addresses: {}", addresses);
        }

    }

//    private Block getLatestBlock_() {
//        final Block block;
//        lock();
//        try {
//            if (API.isConnected()) {
//                final long latest = API.getChain().blockNumber().getObject();
//                block = API.getChain().getBlockByNumber(latest).getObject();
//            } else {
//                block = null;
//            }
//        } finally {
//            unLock();
//        }
//        return block;
//    }

    private Block getLatestBlock() {
        final Block block;
        if(apiIsConnected()) {
            final long latest = callApi(api -> api.getChain().blockNumber()).getObject();
            block = callApi(api -> api.getChain().getBlockByNumber(latest)).getObject();
        } else {
            block = null;
        }
        return block;
    }

//    private Block getBlock_(final long blockNumber) {
//        final Block lastSupposedSafe;
//        lock();
//        try {
//            lastSupposedSafe = API.getChain().getBlockByNumber(blockNumber).getObject();
//        } finally {
//            unLock();
//        }
//        return lastSupposedSafe;
//    }

    private Block getBlock(final long blockNumber) {
        return callApi(api -> api.getChain().getBlockByNumber(blockNumber)).getObject();
    }

    private void removeTransactionsFromBlock(final Set<String> addresses, final long previousSafe) {
        for (String address : addresses) {
            final List<TransactionDTO> txs = new ArrayList<TransactionDTO>(getAccountManager().getTransactions(address));
            final Iterator<TransactionDTO> iterator = txs.iterator();
            final List<TransactionDTO> oldTxs = new ArrayList<>();
            while (iterator.hasNext()) {
                final TransactionDTO t = iterator.next();
                if (t.getBlockNumber() > previousSafe) {
                    oldTxs.add(t);
                } else {
                    break;
                }
            }
            getAccountManager().removeTransactions(address, oldTxs);
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
                    final List<TransactionDTO> newTxs = blockDetails.getTxDetails().stream()
                            .filter(t -> TypeConverter.toJsonHex(t.getFrom().toString()).equals(address)
                                    || TypeConverter.toJsonHex(t.getTo().toString()).equals(address))
                            .map(t -> mapTransaction(t, timestamp, blockNumber))
                            .collect(Collectors.toList());
                    getAccountManager().addTransactions(address, newTxs);
                }
            }
        };
    }

    private TransactionDTO mapTransaction(final TxDetails transaction, final long timeStamp, final long blockNumber) {
        if (transaction == null) {
            return null;
        }
        return new TransactionDTO(
                transaction.getFrom().toString(),
                transaction.getTo().toString(),
                transaction.getTxHash().toString(),
                TypeConverter.StringHexToBigInteger(TypeConverter.toJsonHex(transaction.getValue())),
                transaction.getNrgConsumed(),
                transaction.getNrgPrice(),
                timeStamp,
                blockNumber,
                transaction.getNonce(),
                transaction.getTxIndex());
    }

    private int getCores() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores > 1) {
            cores = cores / 2;
        }
        return cores;
    }

    // --- stuff from from aion_ui ApiBlockchainConnector#sendTransactionInternal and BlockchainConnector#sendTrasaction
    public final TransactionResponseDTO sendTransaction(final SendTransactionDTO dto) throws ValidationException {
        if (dto == null || !dto.validate()) {
            throw new ValidationException("Invalid transaction request data");
        }
        if (dto.estimateValue().compareTo(getBalance(dto.getFrom())) >= 0) {
            throw new ValidationException("Insufficient funds");
        }
        return sendTransactionInternal(dto);
    }

    public final BigInteger getBalance(final String address) {
        balanceDto.setAddress(address);
        balanceDto.loadFromApi();
        return balanceDto.getBalance();
    }

    protected TransactionResponseDTO sendTransactionInternal(final SendTransactionDTO dto) {
        final BigInteger latestTransactionNonce = getLatestTransactionNonce(dto.getFrom());
        TxArgs txArgs = new TxArgs.TxArgsBuilder()
                .from(new Address(TypeConverter.toJsonHex(dto.getFrom())))
                .to(new Address(TypeConverter.toJsonHex(dto.getTo())))
                .value(dto.getValue())
                .nonce(latestTransactionNonce)
                .data(new ByteArrayWrapper(dto.getData()))
                .nrgPrice(dto.getNrgPrice())
                .nrgLimit(dto.getNrg())
                .createTxArgs();
        final MsgRsp response;

        ConsoleManager.addLog("Sending transaction", ConsoleManager.LogType.TRANSACTION, ConsoleManager.LogLevel.INFO);
        response = callApi(api -> api.getTx().sendSignedTransaction(
                txArgs,
                new ByteArrayWrapper((getAccountManager().getAccount(dto.getFrom())).getPrivateKey())
                )).getObject();

//        lock();
//        try {
//            ConsoleManager.addLog("Sending transaction", ConsoleManager.LogType.TRANSACTION, ConsoleManager.LogLevel.INFO);
//            response = API.getTx().sendSignedTransaction(
//                    txArgs,
//                    new ByteArrayWrapper((getAccountManager().getAccount(dto.getFrom())).getPrivateKey())
//            ).getObject();
//        } finally {
//            unLock();
//        }

        final TransactionResponseDTO transactionResponseDTO = mapTransactionResponse(response);
        final int responseStatus = transactionResponseDTO.getStatus();
        if (!ACCEPTED_TRANSACTION_RESPONSE_STATUSES.contains(responseStatus)) {
            getAccountManager().addTimedOutTransaction(dto);
        }
        return transactionResponseDTO;
    }

    private BigInteger getLatestTransactionNonce(final String address) {
        if(apiIsConnected()) {
            return callApi(api -> api.getChain().getNonce(Address.wrap(address))).getObject();
        } else {
            return BigInteger.ZERO;
        }
//        lock();
//        try {
//            if (API.isConnected()) {
//                txCount = API.getChain().getNonce(Address.wrap(address)).getObject();
//            } else {
//                txCount = BigInteger.ZERO;
//            }
//        } finally {
//            unLock();
//        }
//        return txCount;
    }

    private TransactionResponseDTO mapTransactionResponse(final MsgRsp response) {
        return new TransactionResponseDTO(response.getStatus(), response.getTxHash(), response.getError());
    }

    private static final List<Integer> ACCEPTED_TRANSACTION_RESPONSE_STATUSES = Arrays.asList(Message.Retcode.r_tx_Init_VALUE, Message.Retcode.r_tx_Recved_VALUE, Message.Retcode.r_tx_NewPending_VALUE, Message.Retcode.r_tx_Pending_VALUE, Message.Retcode.r_tx_Included_VALUE);

}
