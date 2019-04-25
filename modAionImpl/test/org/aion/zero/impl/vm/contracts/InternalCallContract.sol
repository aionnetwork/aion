pragma solidity ^0.4.15;

contract InternalCallContract {
    uint public amount;
    address public sender;

    function sendValueToContract() public payable {}

    function callAVM(address avmContract) public {
        require(avmContract.call(1));
    }

    function callBalanceTransfer(address receiver) public {
        receiver.transfer(1);
    }
}