package org.aion.base.type;

import java.util.List;

/** @author jay */
public interface IBlockSummary {
    List<?> getReceipts();

    IBlock getBlock();
}
