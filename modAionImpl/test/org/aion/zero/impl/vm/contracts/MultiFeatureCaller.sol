pragma solidity ^0.4.8;

contract MultiFeatureContract {

    function getMessage() public constant returns (string) { }

    function doAction(uint8, bool) public returns (uint128) { }

    function fundMe() public payable { }

    function yankMyFunds(uint128) public { }

    function sendMyFunds(address, uint128) public { }

}

contract MultiFeatureCaller {
    MultiFeatureContract otherContract;

    function MultiFeatureCaller() public payable {

    }

    function setOtherContractAddress(address addr) public {
        otherContract = MultiFeatureContract(addr);
    }

    function sendFunds(address recipient, uint128 funds) public {
        otherContract.sendMyFunds(recipient, funds);
    }

    function callPrecompiled(address precomp, bytes32 data) public {
        precomp.call(data);
    }
}