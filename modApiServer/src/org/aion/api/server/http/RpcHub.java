package org.aion.api.server.http;

import org.aion.api.server.IRpc;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.zero.impl.blockchain.AionImpl;
import org.json.JSONArray;
import org.slf4j.Logger;

/**
 * Serve as a mapper of IRpc.Method -> Function calls on api object
 * Use the enum pattern instead:
 * - https://stackoverflow.com/questions/41093356/how-to-call-enum-method-basis-on-what-type-it-is-passed?rq=1
 * - https://stackoverflow.com/questions/3631195/is-there-a-java-equivalent-to-c-function-pointer
 *
 * @author ali sharif
 */
public class RpcHub
{
    private static final Logger LOG = AionLoggerFactory.getLogger(LogEnum.API.name());
    private ApiWeb3Aion api;
    public RpcHub() {
        api = new ApiWeb3Aion(AionImpl.inst());
    }

    public RpcMsg process(final IRpc.Method method, final JSONArray params) throws Exception
    {
        if (LOG.isDebugEnabled())
            LOG.debug("<request mth=[{}] params={}>", method.name(), params.toString());

        RpcMsg response;

        if (method == null)
            return new RpcMsg(null, RpcError.METHOD_NOT_FOUND);

        switch (method) {

        /* -------------------------------------------------------------------------
        * web3
        */
            case web3_clientVersion: {
                response = api.web3_clientVersion();
                break;

            }
            case web3_sha3: {
                response = api.web3_sha3(params);
                break;
            }
        /* -------------------------------------------------------------------------
         * net
         */
            case net_version: {
                response = api.net_version();
                break;
            }
            case net_peerCount: {
                response = api.net_peerCount();
                break;
            }
            case net_listening: {
                response = api.net_listening();
                break;
            }
        /* -------------------------------------------------------------------------
         * eth
         */
            case eth_protocolVersion: {
                response = api.eth_protocolVersion();
                break;
            }
            case eth_syncing: {
                response = api.eth_syncing();
                break;
            }
            case eth_coinbase: {
                response = api.eth_coinbase();
                break;
            }
            case eth_mining: {
                response = api.eth_mining();
                break;
            }
            case eth_hashrate: {
                response = api.eth_hashrate();
                break;
            }
            case eth_submitHashrate: {
                response = api.eth_submitHashrate(params);
                break;
            }
            case eth_gasPrice: {
                response = api.eth_gasPrice();
                break;
            }
            case personal_listAccounts:
            case eth_accounts: {
                response = api.eth_accounts();
                break;
            }
            case eth_blockNumber: {
                response = api.eth_blockNumber();
                break;
            }
            case eth_getBalance: {
                response = api.eth_getBalance(params);
                break;
            }
            case eth_getStorageAt: {
                response = api.eth_getStorageAt(params);
                break;
            }
            case eth_getTransactionCount: {
                response = api.eth_getTransactionCount(params);
                break;
            }
            case eth_getBlockTransactionCountByHash: {
                response = api.eth_getBlockTransactionCountByHash(params);
                break;
            }
            case eth_getBlockTransactionCountByNumber: {
                response = api.eth_getBlockTransactionCountByNumber(params);
                break;
            }
            case eth_getCode: {
                response = api.eth_getCode(params);
                break;
            }
            case eth_sign: {
                response = api.eth_sign(params);
                break;
            }
            case eth_sendTransaction: {
                response = api.eth_sendTransaction(params);
                break;
            }
            case eth_sendRawTransaction: {
                response = api.eth_sendRawTransaction(params);
                break;
            }
            case eth_call: {
                response = api.eth_call(params);
                break;
            }
            case eth_estimateGas: {
                response = api.eth_estimateGas(params);
                break;
            }
            case eth_getBlockByHash: {
                response = api.eth_getBlockByHash(params);
                break;
            }
            case eth_getBlockByNumber: {
                response = api.eth_getBlockByNumber(params);
                break;
            }
            case eth_getTransactionByHash: {
                response = api.eth_getTransactionByHash(params);
                break;
            }
            case eth_getTransactionByBlockHashAndIndex: {
                response = api.eth_getTransactionByBlockHashAndIndex(params);
                break;
            }
            case eth_getTransactionByBlockNumberAndIndex: {
                response = api.eth_getTransactionByBlockNumberAndIndex(params);
                break;
            }
            case eth_getTransactionReceipt: {
                response = api.eth_getTransactionReceipt(params);
                break;
            }
        /* -------------------------------------------------------------------------
         * compiler
         */
            case eth_getCompilers: {
                response = api.eth_getCompilers();
                break;
            }
            case eth_compileSolidity: {
                response = api.eth_compileSolidity(params);
                break;
            }
        /* -------------------------------------------------------------------------
         * filters
         */
            case eth_newFilter: {
                response = api.eth_newFilter(params);
                break;
            }
            case eth_newBlockFilter: {
                response = api.eth_newBlockFilter();
                break;
            }
            case eth_newPendingTransactionFilter: {
                response = api.eth_newPendingTransactionFilter();
                break;
            }
            case eth_uninstallFilter: {
                response = api.eth_uninstallFilter(params);
                break;
            }
            case eth_getFilterLogs:
            case eth_getFilterChanges: {
                response = api.eth_getFilterChanges(params);
                break;
            }
            case eth_getLogs: {
                response = api.eth_getLogs(params);
                break;
            }
        /* -------------------------------------------------------------------------
         * personal
         */
            case personal_unlockAccount: {
                response = api.personal_unlockAccount(params);
                break;
            }
        /* -------------------------------------------------------------------------
         * debug
         */
            case debug_getBlocksByNumber: {
                response = api.debug_getBlocksByNumber(params);
                break;
            }
        /* -------------------------------------------------------------------------
         * stratum pool
         */
            case getinfo: {
                response = api.stratum_getinfo();
                break;
            }
            case getblocktemplate: {
                response = api.stratum_getblocktemplate();
                break;
            }
            case dumpprivkey: {
                response = api.stratum_dumpprivkey();
                break;
            }
            case validateaddress: {
                response = api.stratum_validateaddress(params);
                break;
            }
            case getdifficulty: {
                response = api.stratum_getdifficulty();
                break;
            }
            case getmininginfo: {
                response = api.stratum_getmininginfo();
                break;
            }
            case submitblock: {
                response = api.stratum_submitblock(params);
                break;
            }
            case getHeaderByBlockNumber: {
                response = api.stratum_getHeaderByBlockNumber(params);
                break;
            }
            case ping: {
                response = new RpcMsg("pong");
                break;
            }
            default: {
                response = null;
                break;
            }
        }

        return response;
    }
}
