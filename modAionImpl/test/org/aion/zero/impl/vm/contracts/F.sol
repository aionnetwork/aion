pragma solidity ^0.4.15;

contract F {

    event A(uint a);

    uint a;

    function f(uint n) {
        A(a);
        a = n;
        if (n > 1) {
            this.call(bytes4(keccak256("f(uint128)")), n - 1);
        }
        if (n == 1) {
            revert();
        }
        A(a);
    }

    function g(uint n) {
        A(a);
        a = n;
        if (n > 1) {
            this.call(bytes4(keccak256("g(uint128)")), n - 1);
        }
        if (n == 3) {
            revert();
        }
        A(a);
    }

    function h(uint n) {
        A(a);
        a = n;
        if (n > 1) {
            this.call(bytes4(keccak256("h(uint128)")), n - 1);
        }
        A(a);
    }
}