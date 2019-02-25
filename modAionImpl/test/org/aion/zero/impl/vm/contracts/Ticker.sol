pragma solidity ^0.4.0;

contract Ticker {

   uint128 ticks;

   function ticking(){
        ticks++;
    }

   function getTicker() constant returns (uint128){
        return ticks;
    }
}