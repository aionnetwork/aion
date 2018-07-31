package org.spongycastle.jce.provider;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateParsingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.spongycastle.asn1.ASN1InputStream;
import org.spongycastle.asn1.ASN1Sequence;
import org.spongycastle.asn1.x509.CertificatePair;
import org.spongycastle.x509.X509CertificatePair;
import org.spongycastle.x509.X509StreamParserSpi;
import org.spongycastle.x509.util.StreamParsingException;

public class X509CertPairParser
    extends X509StreamParserSpi
{
    private InputStream currentStream = null;

    private X509CertificatePair readDERCrossCertificatePair(
        InputStream in)
        throws IOException, CertificateParsingException
    {
        ASN1InputStream dIn = new ASN1InputStream(in);
        ASN1Sequence seq = (ASN1Sequence)dIn.readObject();
        CertificatePair pair = CertificatePair.getInstance(seq);
        return new X509CertificatePair(pair);
    }

    public void engineInit(InputStream in)
    {
        currentStream = in;

        if (!currentStream.markSupported())
        {
            currentStream = new BufferedInputStream(currentStream);
        }
    }

    public Object engineRead() throws StreamParsingException
    {
        try
        {

            currentStream.mark(10);
            int tag = currentStream.read();

            if (tag == -1)
            {
                return null;
            }

            currentStream.reset();
            return readDERCrossCertificatePair(currentStream);
        }
        catch (Exception e)
        {
            throw new StreamParsingException(e.toString(), e);
        }
    }

    public Collection engineReadAll() throws StreamParsingException
    {
        X509CertificatePair pair;
        List certs = new ArrayList();

        while ((pair = (X509CertificatePair)engineRead()) != null)
        {
            certs.add(pair);
        }

        return certs;
    }
}
