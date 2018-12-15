pragma solidity ^0.4.0;

contract SolidityType {

   function testBool(bool x) returns (bool) {
        return x;
   }

   function testUnit(uint96 x) returns (uint96) {
       return x;
   }

   function testAddress(address x) returns (address) {
       return x;
   }

   function testFixedBytes1(bytes5 x) returns (bytes5) {
       return x;
   }

   function testFixedBytes2(bytes20 x) returns (bytes20) {
       return x;
   }

   /**
    * Note: be sure to test both long and short string
    */
   function testString(string x) returns (string) {
       return x;
   }

   /**
    * Note: be sure to test both long and short bytes
    */
   function testBytes(bytes x) returns (bytes) {
       return x;
   }

   function testStaticArray1(uint16[3] x) returns (uint16[3]) {
       return x;
   }

   function testStaticArray2(bytes20[3] x) returns (bytes20[3]) {
       return x;
   }

   function testDynamicArray1(uint16[] x) returns (uint16[]) {
       return x;
   }

   function testDynamicArray2(bytes20[] x) returns (bytes20[]) {
       return x;
   }
}
