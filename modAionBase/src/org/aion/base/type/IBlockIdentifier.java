package org.aion.base.type;

/** @author jay */
public interface IBlockIdentifier {

    byte[] getHash();

    long getNumber();
}
