package org.aion.base.type;

public interface IExecutionResult {

    void setCodeAndNrgLeft(int resultCode, long txNrgLimit);

    int getCode();

    byte[] getOutput();

    byte[] toBytes();

    long getNrgLeft();

    void setCode(int i);

    void setOutput(byte[] output);

    String toString();
}
