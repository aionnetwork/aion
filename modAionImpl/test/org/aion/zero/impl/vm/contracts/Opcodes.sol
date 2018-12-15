pragma solidity ^0.4.15;

contract Caller {

    function f(address addr) {
        addr.call(bytes4(sha3("g()")));
        addr.callcode(bytes4(sha3("g()")));
        addr.delegatecall(bytes4(sha3("g()")));
    }

}

contract Callee {
    event Env(address owner, address caller, address origin, uint value);

    function g() {
        Env(this, msg.sender, tx.origin, msg.value);
    }

}