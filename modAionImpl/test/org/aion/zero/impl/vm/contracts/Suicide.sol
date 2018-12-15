pragma solidity ^0.4.15;

contract Suicide {
    function f(address addr) {
        suicide(addr);
    }
}