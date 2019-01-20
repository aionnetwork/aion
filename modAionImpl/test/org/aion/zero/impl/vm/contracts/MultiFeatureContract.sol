pragma solidity ^0.4.8;

contract MultiFeatureContract {
    string private message = 'Oops, im dead...';

    //constructor(string yourMsg) public payable {
    function MultiFeatureContract() public payable {
        message = 'Im alive!';
    }

    function getMessage() public constant returns (string) {
        return message;
    }

    function doAction(uint8 num, bool isOk) public constant returns (uint128) {
        uint128 action = 1111;
        if (isOk) {
            action = action + num;
        } else {
            action = action - num;
        }
        return action;
    }

    function fundMe() public payable {

    }

    function yankMyFunds(uint128 funds) public {
        if (!msg.sender.send(funds)) {
            throw;
        }
    }

    function sendMyFunds(address addr, uint128 funds) public {
        if (!addr.send(funds)) {
            throw;
        }
    }

}