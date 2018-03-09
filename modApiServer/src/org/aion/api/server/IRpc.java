/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.api.server;

public interface IRpc {
    
    enum Method {
        
        /**
         * eth
         */
        eth_accounts,                
        eth_blockNumber,
        eth_coinbase,
        eth_compileSolidity,
        eth_call,
        eth_getBalance,
        eth_getBlockByNumber,
        eth_getBlockByHash,
        eth_getCode,
        eth_getFilterChanges,
        eth_getFilterLogs,
        eth_getTransactionByHash,
        eth_getTransactionReceipt,
        eth_getTransactionCount,
        eth_estimateGas,
        eth_sendTransaction,
        eth_sendRawTransaction,
        eth_newBlockFilter,
        eth_newFilter,
        eth_syncing,
        eth_uninstallFilter,

        /**
         * net
         */
        net_listening,
        net_peerCount,

        /**
         * debug
         */
        debug_getBlocksByNumber,

        /**
         * personal
         */
        personal_unlockAccount,
        
        /**
         * stratum pool - custom json-rpc endpoints
         */
        validateaddress,
        dumpprivkey,
        getdifficulty,
        getinfo,
        getmininginfo,
        submitblock,
        getblocktemplate,
        getHeaderByBlockNumber,
        ping;
        
        public static boolean contains(String target) {
            for (Method c : Method.values()) {
                if (c.name().equals(target)) 
                    return true;
            }

            return false;
        }
    }
    
}