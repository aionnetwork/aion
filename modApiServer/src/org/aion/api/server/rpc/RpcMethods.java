package org.aion.api.server.rpc;

import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.blockchain.AionImpl;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RpcMethods {

    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());
    private ApiWeb3Aion api;
    private final Map<String, Map<String, RpcMethod>> groupMap;
    private Map<String, RpcMethod> enabledEndpoints;

    public RpcMethods(List<String> enabledGroups) {
        api = new ApiWeb3Aion(AionImpl.inst());

        // find a way to autogen options in config using this enum, without generating circular
        // module dependency (right now it's manual)
        groupMap = Map.ofEntries(
                Map.entry("ping", ping),
                Map.entry("web3", web3),
                Map.entry("net", net),
                Map.entry("debug", debug),
                Map.entry("personal", personal),
                Map.entry("eth", eth),
                Map.entry("stratum", stratum),
                Map.entry("ops", ops),
                Map.entry("priv", priv)
        );

        enabledEndpoints = composite(enabledGroups);
    }

    public RpcMethod get(String name) {
        return enabledEndpoints.get(name);
    }

    public void shutdown() {
        api.shutdown();
    }

    private Map<String, RpcMethod> composite(List<String> groups) {
        Map<String, RpcMethod> composite = new HashMap<>();

        // add the ping endpoint by default
        composite.putAll(ping);

        for(String group : groups) {
            Map<String, RpcMethod> g = null;
            try {
                g = groupMap.get(group.toLowerCase());
            } catch (Exception e) {
                LOG.debug("rpc-methods - unable to recognize api group name: " + group);
                continue;
            }
            // ok to have overlapping method key strings (as long as they also map to the same function)
            if (g != null)
                composite.putAll(g);
        }

        return composite;
    }

    // jdk8 lambdas infer interface method, making our constant declaration pretty.
    public interface RpcMethod {
        RpcMsg call(Object params);
    }

    /**
     * ops
     */
    private final Map<String, RpcMethod> ops = Map.ofEntries(
            Map.entry("ops_getAccountState", (params) -> api.ops_getAccountState(params)),
            Map.entry("ops_getChainHeadViewBestBlock", (params) -> api.ops_getChainHeadViewBestBlock()),
            Map.entry("ops_getTransaction", (params) -> api.ops_getTransaction(params)),
            Map.entry("ops_getBlock", (params) -> api.ops_getBlock(params)),
            Map.entry("ops_getChainHeadView", (params) -> api.ops_getChainHeadView()),
            Map.entry("eth_getBalance", (params) -> api.eth_getBalance(params)),
            Map.entry("eth_sendRawTransaction", (params) -> api.eth_sendRawTransaction(params)),
            Map.entry("eth_getBlockByNumber", (params) -> api.eth_getBlockByNumber(params)),
            Map.entry("eth_getBlockByHash", (params) -> api.eth_getBlockByHash(params)),
            Map.entry("eth_getTransactionByHash", (params) -> api.eth_getTransactionByHash(params))
    );

    /**
     * ping
     */
    private final Map<String, RpcMethod> ping = Map.ofEntries(
            Map.entry("ping", (params) -> new RpcMsg("pong"))
    );

    /**
     * web3
     */
    private final Map<String, RpcMethod> web3 = Map.ofEntries(
            Map.entry("web3_clientVersion", (params) -> api.web3_clientVersion()),
            Map.entry("web3_sha3", (params) -> api.web3_sha3(params))
    );

    /**
     * net
     */
    private final Map<String, RpcMethod> net = Map.ofEntries(
            Map.entry("net_version", (params) -> api.net_version()),
            Map.entry("net_listening", (params) -> api.net_listening()),
            Map.entry("net_peerCount", (params) -> api.net_peerCount())
    );

    /**
     * debug
     */
    private final Map<String, RpcMethod> debug = Map.ofEntries(
            Map.entry("debug_getBlocksByNumber", (params) -> api.debug_getBlocksByNumber(params))
    );

    /**
     * personal
     */
    private final Map<String, RpcMethod> personal = Map.ofEntries(
            Map.entry("personal_unlockAccount", (params) -> api.personal_unlockAccount(params)),
            Map.entry("personal_listAccounts", (params) -> api.eth_accounts()),
            Map.entry("personal_lockAccount", (params) -> api.personal_lockAccount(params)),
            Map.entry("personal_newAccount", (params) -> api.personal_newAccount(params))
    );

    /**
     * eth
     */
    private final Map<String, RpcMethod> eth = Map.ofEntries(
            Map.entry("eth_getCompilers", (params) -> api.eth_getCompilers()),
            Map.entry("eth_compileSolidity", (params) -> api.eth_compileSolidity(params)),

            Map.entry("eth_accounts", (params) -> api.eth_accounts()), // belongs to the personal api
            Map.entry("eth_blockNumber", (params) -> api.eth_blockNumber()),
            Map.entry("eth_coinbase", (params) -> api.eth_coinbase()),
            Map.entry("eth_call", (params) -> api.eth_call(params)),
            Map.entry("eth_getBalance", (params) -> api.eth_getBalance(params)),
            Map.entry("eth_getBlockByNumber", (params) -> api.eth_getBlockByNumber(params)),
            Map.entry("eth_getBlockByHash", (params) -> api.eth_getBlockByHash(params)),
            Map.entry("eth_getCode", (params) -> api.eth_getCode(params)),
            Map.entry("eth_estimateGas", (params) -> api.eth_estimateGas(params)),
            Map.entry("eth_sendTransaction", (params) -> api.eth_sendTransaction(params)),
            Map.entry("eth_sendRawTransaction", (params) -> api.eth_sendRawTransaction(params)),
            Map.entry("eth_getTransactionCount", (params) -> api.eth_getTransactionCount(params)),
            Map.entry("eth_getBlockTransactionCountByHash", (params) -> api.eth_getBlockTransactionCountByHash(params)),
            Map.entry("eth_getBlockTransactionCountByNumber", (params) -> api.eth_getBlockTransactionCountByNumber(params)),
            Map.entry("eth_getTransactionByHash", (params) -> api.eth_getTransactionByHash(params)),
            Map.entry("eth_getTransactionByBlockHashAndIndex", (params) -> api.eth_getTransactionByBlockHashAndIndex(params)),
            Map.entry("eth_getTransactionByBlockNumberAndIndex", (params) -> api.eth_getTransactionByBlockNumberAndIndex(params)),
            Map.entry("eth_getTransactionReceipt", (params) -> api.eth_getTransactionReceipt(params)),
            Map.entry("eth_syncing", (params) -> api.eth_syncing()),
            Map.entry("eth_protocolVersion", (params) -> api.eth_protocolVersion()),
            Map.entry("eth_mining", (params) -> api.eth_mining()),
            Map.entry("eth_hashrate", (params) -> api.eth_hashrate()),
            Map.entry("eth_submitHashrate", (params) -> api.eth_submitHashrate(params)),
            Map.entry("eth_gasPrice", (params) -> api.eth_gasPrice()),
            Map.entry("eth_sign", (params) -> api.eth_sign(params)),
            Map.entry("eth_getStorageAt", (params) -> api.eth_getStorageAt(params)),

            Map.entry("eth_newFilter", (params) -> api.eth_newFilter(params)),
            Map.entry("eth_newBlockFilter", (params) -> api.eth_newBlockFilter()),
            Map.entry("eth_newPendingTransactionFilter", (params) -> api.eth_newPendingTransactionFilter()),
            Map.entry("eth_uninstallFilter", (params) -> api.eth_uninstallFilter(params)),
            Map.entry("eth_getFilterChanges", (params) -> api.eth_getFilterChanges(params)),
            Map.entry("eth_getFilterLogs", (params) -> api.eth_getFilterChanges(params)),
            Map.entry("eth_getLogs", (params) -> api.eth_getLogs(params))
    );

    /**
     * stratum
     */
    private final Map<String, RpcMethod> stratum = Map.ofEntries(
            Map.entry("validateaddress", (params) -> api.stratum_validateaddress(params)),
            Map.entry("dumpprivkey", (params) -> api.stratum_dumpprivkey()),
            Map.entry("getdifficulty", (params) -> api.stratum_getdifficulty()),
            Map.entry("getinfo", (params) -> api.stratum_getinfo()),
            Map.entry("getmininginfo", (params) -> api.stratum_getmininginfo()),
            Map.entry("submitblock", (params) -> api.stratum_submitblock(params)),
            Map.entry("getblocktemplate", (params) -> api.stratum_getwork()),
            Map.entry("getHeaderByBlockNumber", (params) -> api.stratum_getHeaderByBlockNumber(params)),
            Map.entry("getMinerStats", (params) -> api.stratum_getMinerStats(params))
    );

    /**
     * priv
     */
    private final Map<String, RpcMethod> priv = Map.ofEntries(
            Map.entry("priv_peers", (params) -> api.priv_peers()),
            Map.entry("priv_p2pConfig", (params) -> api.priv_p2pConfig()),
            Map.entry("priv_getPendingTransactions", (params) -> api.priv_getPendingTransactions(params)),
            Map.entry("priv_getPendingSize", (params) -> api.priv_getPendingSize()),
            Map.entry("priv_dumpTransaction", (params) -> api.priv_dumpTransaction(params)),
            Map.entry("priv_dumpBlockByHash", (params) -> api.priv_dumpBlockByHash(params)),
            Map.entry("priv_dumpBlockByNumber", (params) -> api.priv_dumpBlockByNumber(params)),
            Map.entry("priv_shortStats", (params) -> api.priv_shortStats()),
            Map.entry("priv_config", (params) -> api.priv_config()),
            Map.entry("priv_syncPeers", (params) -> api.priv_syncPeers())
    );
}
