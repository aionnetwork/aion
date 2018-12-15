package org.aion.zero.impl.vm.contracts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.aion.base.util.Hex;
import org.aion.solidity.CompilationResult;
import org.aion.solidity.Compiler;
import org.aion.solidity.Compiler.Options;

public class ContractUtils {
    /**
     * Reads the given contract.
     *
     * @param fileName
     * @return
     */
    public static byte[] readContract(String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InputStream in = ContractUtils.class.getResourceAsStream(fileName);
        for (int c; (c = in.read()) != -1; ) {
            out.write(c);
        }
        in.close();

        return out.toByteArray();
    }

    /**
     * Compiles the given solidity source file and returns the deployer code for the given contract.
     *
     * @param fileName
     * @param contractName
     * @return
     * @throws IOException
     */
    public static byte[] getContractDeployer(String fileName, String contractName)
        throws IOException {
        Compiler.Result r = Compiler.getInstance().compile(readContract(fileName), Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get(contractName).bin;
        return Hex.decode(deployer);
    }

    /**
     * Compiles the given solidity source file and returns the contract code for the given contract.
     *
     * <p>NOTE: This method assumes the constructor is empty.
     *
     * @param fileName
     * @param contractName
     * @return
     * @throws IOException
     */
    public static byte[] getContractBody(String fileName, String contractName) throws IOException {
        Compiler.Result r = Compiler.getInstance().compile(readContract(fileName), Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get(contractName).bin;
        String contract = deployer.substring(deployer.indexOf("60506040", 1));
        return Hex.decode(contract);
    }
}
