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
 * Contributors to the aion source files in decreasing order of code volume:
 * 
 *     Aion foundation.
 *     
 ******************************************************************************/

package org.aion.p2p.v0;

import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.aion.p2p.INode;

/*
 * 
 * @author Chris
 * p2p://{uuid}@{ip}:{port} eg: p2p://3e2cab6a-09dd-4771-b28d-6aa674009796@127.0.0.1:30303
 *              
 */

public final class Node implements INode{
    
    private static final String PATTERN_P2P = 
        "^p2p://[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" + 
        "@([01]?\\d\\d?|2[0-4]\\d|25[0-5]).([01]?\\d\\d?|2[0-4]\\d|25[0-5]).([01]?\\d\\d?|2[0-4]\\d|25[0-5]).([01]?\\d\\d?|2[0-4]\\d|25[0-5])" + 
        ":$";
    
    private static Pattern regexP2p = Pattern.compile(PATTERN_P2P);
    
    private boolean fromBootList;
       
    /**
     *  id != "" && version != "" && node on pending nodes => move to active nodes
     */
    private byte[] id; // 36 bytes
    
    private int idHash; 
    
    private int version;
    
    private byte[] ip; // 8 bytes
    
    private int port; // 4 bytes
    
    private long timestamp;
    
    private long bestBlockNumber;
    
    private byte[] bestBlockHash;
    
    private byte[] totalDifficulty;
    
    private SocketChannel channel;

    /**
     *  constructor for initial stage of connections from network
     */
    Node(boolean fromBootList, final byte[] _ip) {
        this.fromBootList = fromBootList;
        this.id = new byte[36];
        this.idHash = 0;
        this.version = 0;
        this.ip = _ip;
        this.port = 0;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0l;
    }
    
    /**
     *  constructor for initial stage of boot nodes from config
     */
    public Node(boolean fromBootList, final byte[] _id, final byte[] _ip, final int _port) {
        this.fromBootList = fromBootList;
        this.id = _id;        
        if(_id != null && _id.length == 36)
            this.idHash = Arrays.hashCode(_id);
        this.version = -1;
        this.ip = _ip;
        this.port = _port;
        this.timestamp = System.currentTimeMillis();
        this.bestBlockNumber = 0l;
    }
    
    void setFromBootList(final boolean _fromBootList) {
        this.fromBootList = _fromBootList;
    }
    
    void setId(final byte[] _id) {
        this.id = _id;
        if(_id != null && _id.length == 36)
            this.idHash = Arrays.hashCode(_id);
    }
    
    void setVersion(final int _version) {
        this.version = _version;
    }
    
    void setIp(final byte[] _ip) {
        this.ip = _ip;
    }
    
    void setPort(final int _port) {
        this.port = _port;
    }
    
    void setBestBlockHash(final byte[] _bestBlockHash) {
        this.bestBlockHash = _bestBlockHash;
    }
    
    void setTotalDifficulty(final byte[] _totalDifficulty) {
        this.totalDifficulty = _totalDifficulty;
    }
    
    /**
     * this method used to keep current node stage 
     * on either pending list or active list
     */
    void refreshTimestamp() {
        this.timestamp = System.currentTimeMillis();
    }
    
    void setChannel(final SocketChannel _channel) {
        this.channel = _channel;
    }
    
    boolean getIfFromBootList() {
        return this.fromBootList;
    }

    int getVersion() {
        return this.version;
    }
    
    public byte[] getIp() {
        return this.ip;
    }
    
    public int getPort() {
        return this.port;
    }
    
    long getTimestamp() {
        return this.timestamp;
    }  
    
    public SocketChannel getChannel() {
        return this.channel;
    }
        
    @Override
    public byte[] getId() {
        return this.id;
    }  

    @Override
    public int getIdHash() {
        return this.idHash;
    }
    
    @Override
    public long getBestBlockNumber() {
        return this.bestBlockNumber;
    }
    
    @Override
    public byte[] getBestBlockHash() {
        return this.bestBlockHash;
    }

    @Override
    public byte[] getTotalDifficulty() {
        return this.totalDifficulty;
    }
    
    @Override
    public void updateStatus(long _bestBlockNumber, final byte[] _bestBlockHash) {
        if(_bestBlockNumber > this.bestBlockNumber){
            this.bestBlockNumber = _bestBlockNumber;
            this.bestBlockHash = _bestBlockHash;
        }
    }

    public static Node parseEnode(final boolean fromBootList, final String _enode) {
        Node node = null;
        if(regexP2p.matcher(_enode) != null) {
            String[] arrs = _enode.split("@");
            byte[] _tempBytes = arrs[0].getBytes();
            if(_tempBytes.length != 42)
                return null;
            byte[] _id = Arrays.copyOfRange(_tempBytes, 6, 42);
            String[] subArrs = arrs[1].split(":");
            
            byte[] _ip = Helper.ipStrToBytes(subArrs[0]);
            int _port =  Integer.parseInt(subArrs[1]);
            node = new Node(fromBootList, _id, _ip, _port);
        }
        return node;
    }
}
