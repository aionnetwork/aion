pragma solidity ^0.4.0;

contract A{
    event AE(uint128 value);
    event AEA(uint128 value);
    event AEB(uint128 value);

    function AA(address addr){
        AE(1);
        AEA(2);
        //B(addr).BB(addr, 1, 256, 128)
        addr.call.value(0)(bytes4(sha3("BB(address,uint128,bytes32,bytes32)")), addr, 1, 256, 128);
        AEB(3);
    }
}

contract B{
    event BE(address indexed _recipient, uint128 indexed _amount, bytes32 indexed _foreignNetworkId, bytes32 _foreignData);

    function BB(address _recipient, uint128 _amount, bytes32 _foreignNetworkId, bytes32 _foreignData){
        BE(_recipient, _amount, _foreignNetworkId, _foreignData);
    }
}