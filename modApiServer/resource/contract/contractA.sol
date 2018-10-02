contract A{

    event AE(uint128 value);
    event AEA(uint128 value);
    event AEB(uint128 value);

    B b = B(0x0000000000000000000000000000000000000000000000000000000000001234);

    function AA(){
        AE(1);
        AEA(2);
        b.BB(address(0x0000000000000000000000000000000000000000000000000000000000001234), 1, 256, 128);
        AEB(3);
    }
}

contract B{
   function BB(address _recipient, uint128 _amount, bytes32 _foreignNetworkId, bytes32 _foreignData) public;
}

