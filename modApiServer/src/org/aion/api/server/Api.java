package org.aion.api.server;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import org.aion.api.server.types.CompiContrInfo;
import org.aion.api.server.types.CompiledContr;
import org.aion.vm.api.types.Address;
import org.aion.crypto.ECKey;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.account.AccountManager;
import org.aion.mcf.account.Keystore;
import org.aion.mcf.types.AbstractBlock;
import org.aion.solidity.Abi;
import org.aion.solidity.CompilationResult;
import org.aion.solidity.CompilationResult.Contract;
import org.aion.solidity.Compiler;
import org.aion.util.string.StringUtils;
import org.aion.zero.impl.blockchain.AionPendingStateImpl;
import org.slf4j.Logger;

public abstract class Api<B extends AbstractBlock<?, ?>> {

    protected static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());

    private final AccountManager ACCOUNT_MANAGER = AccountManager.inst();
    private final Compiler solc = Compiler.getInstance();
    protected final AionPendingStateImpl pendingState;

    // This is the constructor that should always be used, unless testing
    Api() {
        pendingState = AionPendingStateImpl.inst();
    }

    // Only for testing purposes
    @VisibleForTesting
    Api(AionPendingStateImpl ps) {
        pendingState = ps;
    }

    public abstract String getCoinbase();

    public abstract byte getApiVersion();

    // --Commented out by Inspection (02/02/18 6:55 PM):public abstract int
    // getProtocolVersion();

    // --Commented out by Inspection START (02/02/18 6:56 PM):
    // public String newAccount(final String _password) {
    // return Keystore.create(_password);
    // }
    // --Commented out by Inspection STOP (02/02/18 6:56 PM)

    protected boolean unlockAccount(
            final String _address, final String _password, final int _duration) {
        return this.ACCOUNT_MANAGER.unlockAccount(Address.wrap(_address), _password, _duration);
    }

    public boolean unlockAccount(
            final Address _address, final String _password, final int _duration) {
        return this.ACCOUNT_MANAGER.unlockAccount(_address, _password, _duration);
    }

    protected boolean lockAccount(final Address _addr, final String _password) {
        return this.ACCOUNT_MANAGER.lockAccount(_addr, _password);
    }

    public List<String> getAccounts() {
        return Keystore.accountsSorted();
    }

    protected ECKey getAccountKey(final String _address) {
        return ACCOUNT_MANAGER.getKey(Address.wrap(_address));
    }

    @SuppressWarnings("rawtypes")
    public abstract AbstractBlock getBestBlock();

    // --Commented out by Inspection (02/02/18 6:56 PM):public abstract B
    // getBlock(final String _bnOrId);

    public abstract B getBlock(final long _bn);

    public abstract B getBlockByHash(final byte[] hash);

    public abstract BigInteger getBalance(final String _address) throws Exception;

    protected Map<String, CompiledContr> contract_compileSolidity(final String _contract) {
        try {
            Compiler.Result res =
                    solc.compile(_contract.getBytes(), Compiler.Options.ABI, Compiler.Options.BIN);
            return processCompileRsp(res, _contract);
        } catch (IOException | NoSuchElementException ex) {
            LOG.debug("contract compile error");
            return null;
        }
    }

    protected Map<String, CompiledContr> contract_compileSolidityZip(
            final byte[] zipfile, String entrypoint) {
        try {
            Compiler.Result res =
                    solc.compileZip(
                            zipfile, entrypoint, Compiler.Options.ABI, Compiler.Options.BIN);
            return processCompileRsp(res, entrypoint);

        } catch (IOException | NoSuchElementException ex) {
            LOG.debug("contract compile error");
            return null;
        }
    }

    private Map<String, CompiledContr> processCompileRsp(Compiler.Result res, String source) {
        Map<String, CompiledContr> compiledContracts = new HashMap<String, CompiledContr>();
        if (res.isFailed()) {
            LOG.info("contract compile error: [{}]", res.errors);

            /*
             Enhance performance by separating the log threads and kernel TODO: Implement a
             queue for strings TODO: Put every LOG message onto the queue TODO: Use a thread
             service to process these message
            */
            CompiledContr ret = new CompiledContr();
            ret.error = res.errors;
            compiledContracts.put("compile-error", ret);
            return compiledContracts;
        }
        CompilationResult result = CompilationResult.parse(res.output);
        for (Entry<String, Contract> stringContractEntry : result.contracts.entrySet()) {
            CompiledContr ret = new CompiledContr();
            Contract Contract = stringContractEntry.getValue();
            ret.code = StringUtils.toJsonHex(Contract.bin);
            ret.info = new CompiContrInfo();
            ret.info.source = source;
            ret.info.language = "Solidity";
            ret.info.languageVersion = "0";
            ret.info.compilerVersion = result.version;
            ret.info.abiDefinition = Abi.fromJSON(Contract.abi).getEntries();
            compiledContracts.put(stringContractEntry.getKey(), ret);
        }
        return compiledContracts;
    }

    public String solcVersion() {
        try {
            return solc.getVersion();
        } catch (IOException e) {
            LOG.debug("get solc version error");
            return null;
        }
    }
}
