contract B{
   event BE(address indexed _recipient, uint128 indexed _amount, bytes32 indexed _foreignNetworkId, bytes32 _foreignData);

   function BB(address _recipient, uint128 _amount, bytes32 _foreignNetworkId, bytes32 _foreignData){
        BE(_recipient, _amount, _foreignNetworkId, _foreignData);
    }
}
