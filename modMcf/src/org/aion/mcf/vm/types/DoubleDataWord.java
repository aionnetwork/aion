package org.aion.mcf.vm.types;

import org.aion.base.vm.IDataWord;

public class DoubleDataWord implements IDataWord {
    private static final WordType wType = WordType.DOUBLE_DATA_WORD;

    public static final DoubleDataWord ZERO = null;

    @Override
    public WordType getType() { return wType; }

    @Override
    public byte[] getData() {
        //TODO
        return null;
    }

    @Override
    public byte[] getNoLeadZeroesData() {
        //TODO
        return null;
    }

    @Override
    public IDataWord copy() {
        //TODO
        return null;
    }

    @Override
    public boolean isZero() {
        //TODO
        return false;
    }

}
