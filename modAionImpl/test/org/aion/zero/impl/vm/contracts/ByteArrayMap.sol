pragma solidity ^0.4.8;

contract ByteArrayMap {

    mapping(uint => bytes) public data;

    function f() {
        bytes memory tmp = new bytes(1024);
        tmp[0] = 'a';
        tmp[1023] = 'b';

        data[32] = tmp;
    }

    function g() constant returns (bytes) {
        return data[32];
    }
}