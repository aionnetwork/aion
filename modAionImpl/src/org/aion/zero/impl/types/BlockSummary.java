package org.aion.zero.impl.types;

import java.util.List;

/** @author jay */
public interface BlockSummary {
    List<?> getReceipts();

    Block getBlock();
}
