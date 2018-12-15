pragma solidity ^0.4.8;

// This contract was taken from: https://ethereum.stackexchange.com/questions/3667/difference-between-call-callcode-and-delegatecall

contract D {
  uint public n;
  address public sender;

  function callSetN(address _e, uint _n) public {
    _e.call(bytes4(keccak256("setN(uint128)")), _n); // E's storage is set, D is not modified
  }

  function callcodeSetN(address _e, uint _n) public payable {
    _e.callcode(bytes4(keccak256("setN(uint128)")), _n); // D's storage is set, E is not modified
  }

  function delegatecallSetN(address _e, uint _n) public payable {
    _e.delegatecall(bytes4(keccak256("setN(uint128)")), _n); // D's storage is set, E is not modified
  }

  function getN() public returns (uint) {
      return n;
  }
}

contract E {
  uint public n;
  address public sender;

  function setN(uint _n) public payable {
    n = _n;
    sender = msg.sender;
    // msg.sender is D if invoked by D's callcodeSetN. None of E's storage is updated
    // msg.sender is C if invoked by C.foo(). None of E's storage is updated

    // the value of "this" is D, when invoked by either D's callcodeSetN or C.foo()
  }

  function getN() public returns (uint) {
    return n;
  }
}