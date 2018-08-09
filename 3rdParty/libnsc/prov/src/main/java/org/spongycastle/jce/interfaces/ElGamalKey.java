package org.spongycastle.jce.interfaces;

import javax.crypto.interfaces.DHKey;

import org.spongycastle.jce.spec.ElGamalParameterSpec;

public interface ElGamalKey
    extends DHKey
{
    public ElGamalParameterSpec getParameters();
}
