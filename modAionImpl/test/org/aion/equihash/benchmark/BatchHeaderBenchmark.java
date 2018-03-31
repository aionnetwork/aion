//package org.aion.equihash.benchmark;
//
//import org.aion.base.type.Address;
//import org.aion.base.util.ByteUtil;
//import org.aion.equihash.EquiUtils;
//import org.aion.equihash.OptimizedEquiValidator;
//import org.aion.mcf.valid.BlockHeaderValidator;
//import org.aion.zero.impl.AionBlockchainImpl;
//import org.aion.zero.impl.blockchain.ChainConfiguration;
//import org.aion.zero.impl.types.AionBlock;
//import org.aion.zero.types.A0BlockHeader;
//import org.junit.Test;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.FileReader;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Scanner;
//
//public class BatchHeaderBenchmark {
//
//
//    @Test
//    public void benchTime() {
//        try {
//
//            List<A0BlockHeader> hdrs = new ArrayList<>();
//            OptimizedEquiValidator ov = new OptimizedEquiValidator(210,9);
//            ChainConfiguration chainConfig = new ChainConfiguration();
//            AionBlockchainImpl chain = AionBlockchainImpl.inst();
//            BlockHeaderValidator blockHeaderValidator = chainConfig.createBlockHeaderValidator();
//
//            long seqExecutionTotal = 0;
//            long parallelExecutionTotal = 0;
//            long numIter = 0;
//            int numInvalid = 0;
//
//            boolean isValidSeq = true;
//            boolean isValidPar = true;
//
//            Thread.sleep(1000 * 20L);
//
//
//            for(int k = 0; k < 10; k++) {
//                File file = new File("aionTestnetChain.csv");
//                FileReader fileReader = new FileReader(file);
//                BufferedReader bufferedReader = new BufferedReader(fileReader);
//                String line;
//                while ((line = bufferedReader.readLine()) != null) {
//                    String[] contents = line.split(",");
//
//                    byte[] parentHash = ByteUtil.hexStringToBytes(contents[0]);
//                    Address coinBase = new Address(ByteUtil.hexStringToBytes(contents[1]));
//                    byte[] stateRoot = ByteUtil.hexStringToBytes(contents[2]);
//                    byte[] txTrieRoot = ByteUtil.hexStringToBytes(contents[3]);
//                    byte[] receiptTrieRoot = ByteUtil.hexStringToBytes(contents[4]);
//                    byte[] logsBloom = ByteUtil.hexStringToBytes(contents[5]);
//                    byte[] diff = new byte[16];
//                    System.arraycopy(ByteUtil.hexStringToBytes(contents[6]), 0, diff, 16 - ByteUtil.hexStringToBytes(contents[6]).length, ByteUtil.hexStringToBytes(contents[6]).length);
//
//                    long number = Long.decode(contents[7]);
//                    long timestamp = Long.decode(contents[8]);
//                    byte[] extraData = ByteUtil.hexStringToBytes(contents[9]);
//                    byte[] nonce = ByteUtil.hexStringToBytes(contents[10]);
//                    byte[] solution = ByteUtil.hexStringToBytes(contents[11]);
//                    long energyConsumed = Long.decode(contents[12]);
//                    long energyLimit = Long.decode(contents[13]);
//
//
//                    A0BlockHeader newHdr = new A0BlockHeader(parentHash, coinBase, logsBloom,
//                            diff, number, timestamp, extraData, nonce, solution, energyConsumed,
//                            energyLimit);
//
//                    newHdr.setStateRoot(stateRoot);
//                    newHdr.setTxTrieRoot(txTrieRoot);
//                    newHdr.setReceiptsRoot(receiptTrieRoot);
//
//                    hdrs.add(newHdr);
//
//                    if (hdrs.size() % 192 == 0) {
//
//                        long start;
//                        long stop;
//
//                        //Sequential processing
//                        start = System.nanoTime();
//                        for (A0BlockHeader hdr : hdrs) {
//
//                            isValidSeq &= blockHeaderValidator.validate(hdr);
//                        }
//                        stop = System.nanoTime();
//                        seqExecutionTotal += (stop - start);
//
//                        numIter++;
//
//                        hdrs.clear();
//                    }
//                }
//                fileReader.close();
//            }
//
//            System.out.println(hdrs.size());
//            System.out.println("NumInvalid: " + numInvalid++);
//            System.out.println("Num Iter: " + numIter);
//            System.out.println("Average sequential: "  + seqExecutionTotal/numIter);
//            System.out.println("Average parallel:   "  + parallelExecutionTotal/numIter);
//            System.out.println("isValidSeq: " + isValidSeq);
//            System.out.println("isValidPara: " + isValidPar);
//
//            Scanner input = new Scanner(System.in);
//            input.nextLine();
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//
//    }
//
//}
