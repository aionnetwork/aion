package org.aion.base.type;

import java.math.BigInteger;

/** @author jay */
public interface IPowBlockHeader extends IBlockHeader {

    byte[] getDifficulty();

    BigInteger getDifficultyBI();

    void setDifficulty(byte[] _diff);

    byte[] getPowBoundary();
}
