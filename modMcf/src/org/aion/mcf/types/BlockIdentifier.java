package org.aion.mcf.types;

/** @author jay */
public interface BlockIdentifier {

    byte[] getHash();

    long getNumber();
}
