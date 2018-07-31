package org.spongycastle.jcajce.provider.asymmetric.ec;

import java.io.ByteArrayOutputStream;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.KeyEncoder;
import org.spongycastle.crypto.agreement.ECDHBasicAgreement;
import org.spongycastle.crypto.digests.SHA1Digest;
import org.spongycastle.crypto.engines.AESEngine;
import org.spongycastle.crypto.engines.DESedeEngine;
import org.spongycastle.crypto.engines.IESEngine;
import org.spongycastle.crypto.engines.OldIESEngine;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.generators.EphemeralKeyPairGenerator;
import org.spongycastle.crypto.generators.KDF2BytesGenerator;
import org.spongycastle.crypto.macs.HMac;
import org.spongycastle.crypto.modes.CBCBlockCipher;
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.spongycastle.crypto.params.AsymmetricKeyParameter;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.IESWithCipherParameters;
import org.spongycastle.crypto.params.ParametersWithIV;
import org.spongycastle.crypto.parsers.ECIESPublicKeyParser;
import org.spongycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.spongycastle.jcajce.provider.asymmetric.util.IESUtil;
import org.spongycastle.jcajce.util.BCJcaJceHelper;
import org.spongycastle.jcajce.util.JcaJceHelper;
import org.spongycastle.jce.interfaces.ECKey;
import org.spongycastle.jce.interfaces.IESKey;
import org.spongycastle.jce.spec.IESParameterSpec;
import org.spongycastle.util.Strings;


public class IESCipher
    extends CipherSpi
{
    private final JcaJceHelper helper = new BCJcaJceHelper();

    private int ivLength;
    private IESEngine engine;
    private int state = -1;
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private AlgorithmParameters engineParam = null;
    private IESParameterSpec engineSpec = null;
    private AsymmetricKeyParameter key;
    private SecureRandom random;
    private boolean dhaesMode = false;
    private AsymmetricKeyParameter otherKeyParameter = null;

    public IESCipher(IESEngine engine)
    {
        this.engine = engine;
        this.ivLength = 0;
    }

    public IESCipher(IESEngine engine, int ivLength)
    {
        this.engine = engine;
        this.ivLength = ivLength;
    }

    public int engineGetBlockSize()
    {
        if (engine.getCipher() != null)
        {
            return engine.getCipher().getBlockSize();
        }
        else
        {
            return 0;
        }
    }


    public int engineGetKeySize(Key key)
    {
        if (key instanceof ECKey)
        {
            return ((ECKey)key).getParameters().getCurve().getFieldSize();
        }
        else
        {
            throw new IllegalArgumentException("not an EC key");
        }
    }


    public byte[] engineGetIV()
    {
        return null;
    }

    public AlgorithmParameters engineGetParameters()
    {
        if (engineParam == null && engineSpec != null)
        {
            try
            {
                engineParam = helper.createAlgorithmParameters("IES");
                engineParam.init(engineSpec);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e.toString());
            }
        }

        return engineParam;
    }


    public void engineSetMode(String mode)
        throws NoSuchAlgorithmException
    {
        String modeName = Strings.toUpperCase(mode);

        if (modeName.equals("NONE"))
        {
            dhaesMode = false;
        }
        else if (modeName.equals("DHAES"))
        {
            dhaesMode = true;
        }
        else
        {
            throw new IllegalArgumentException("can't support mode " + mode);
        }
    }


    public int engineGetOutputSize(int inputLen)
    {
        int len1, len2, len3;

        if (key == null)
        {
            throw new IllegalStateException("cipher not initialised");
        }

        len1 = engine.getMac().getMacSize();

        if (otherKeyParameter == null)
        {
            len2 = 1 + 2 * (((ECKeyParameters)key).getParameters().getCurve().getFieldSize() + 7) / 8;
        }
        else
        {
            len2 = 0;
        }

        if (engine.getCipher() == null)
        {
            len3 = inputLen;
        }
        else if (state == Cipher.ENCRYPT_MODE || state == Cipher.WRAP_MODE)
        {
            len3 = engine.getCipher().getOutputSize(inputLen);
        }
        else if (state == Cipher.DECRYPT_MODE || state == Cipher.UNWRAP_MODE)
        {
            len3 = engine.getCipher().getOutputSize(inputLen - len1 - len2);
        }
        else
        {
            throw new IllegalStateException("cipher not initialised");
        }

        if (state == Cipher.ENCRYPT_MODE || state == Cipher.WRAP_MODE)
        {
            return buffer.size() + len1 + len2 + len3;
        }
        else if (state == Cipher.DECRYPT_MODE || state == Cipher.UNWRAP_MODE)
        {
            return buffer.size() - len1 - len2 + len3;
        }
        else
        {
            throw new IllegalStateException("cipher not initialised");
        }

    }

    public void engineSetPadding(String padding)
        throws NoSuchPaddingException
    {
        String paddingName = Strings.toUpperCase(padding);

        // TDOD: make this meaningful...
        if (paddingName.equals("NOPADDING"))
        {

        }
        else if (paddingName.equals("PKCS5PADDING") || paddingName.equals("PKCS7PADDING"))
        {

        }
        else
        {
            throw new NoSuchPaddingException("padding not available with IESCipher");
        }
    }


    // Initialisation methods

    public void engineInit(
        int opmode,
        Key key,
        AlgorithmParameters params,
        SecureRandom random)
        throws InvalidKeyException, InvalidAlgorithmParameterException
    {
        AlgorithmParameterSpec paramSpec = null;

        if (params != null)
        {
            try
            {
                paramSpec = params.getParameterSpec(IESParameterSpec.class);
            }
            catch (Exception e)
            {
                throw new InvalidAlgorithmParameterException("cannot recognise parameters: " + e.toString());
            }
        }

        engineParam = params;
        engineInit(opmode, key, paramSpec, random);

    }


    public void engineInit(
        int opmode,
        Key key,
        AlgorithmParameterSpec engineSpec,
        SecureRandom random)
        throws InvalidAlgorithmParameterException, InvalidKeyException
    {
        otherKeyParameter = null;

        // Use default parameters (including cipher key size) if none are specified
        if (engineSpec == null)
        {
            this.engineSpec = IESUtil.guessParameterSpec(engine.getCipher());
        }
        else if (engineSpec instanceof IESParameterSpec)
        {
            this.engineSpec = (IESParameterSpec)engineSpec;
        }
        else
        {
            throw new InvalidAlgorithmParameterException("must be passed IES parameters");
        }

        byte[] nonce = this.engineSpec.getNonce();

        if (nonce != null)
        {
            if (ivLength == 0)
            {
                throw new InvalidAlgorithmParameterException("NONCE present in IES Parameters when none required");
            }
            else if (nonce.length != ivLength)
            {
                throw new InvalidAlgorithmParameterException("NONCE in IES Parameters needs to be " + ivLength + " bytes long");
            }
        }

        // Parse the recipient's key
        if (opmode == Cipher.ENCRYPT_MODE || opmode == Cipher.WRAP_MODE)
        {
            if (key instanceof PublicKey)
            {
                this.key = ECUtil.generatePublicKeyParameter((PublicKey)key);
            }
            else if (key instanceof IESKey)
            {
                IESKey ieKey = (IESKey)key;

                this.key = ECUtil.generatePublicKeyParameter(ieKey.getPublic());
                this.otherKeyParameter = ECUtil.generatePrivateKeyParameter(ieKey.getPrivate());
            }
            else
            {
                throw new InvalidKeyException("must be passed recipient's public EC key for encryption");
            }
        }
        else if (opmode == Cipher.DECRYPT_MODE || opmode == Cipher.UNWRAP_MODE)
        {
            if (key instanceof PrivateKey)
            {
                this.key = ECUtil.generatePrivateKeyParameter((PrivateKey)key);
            }
            else if (key instanceof IESKey)
            {
                IESKey ieKey = (IESKey)key;

                this.otherKeyParameter = ECUtil.generatePublicKeyParameter(ieKey.getPublic());
                this.key = ECUtil.generatePrivateKeyParameter(ieKey.getPrivate());
            }
            else
            {
                throw new InvalidKeyException("must be passed recipient's private EC key for decryption");
            }
        }
        else
        {
            throw new InvalidKeyException("must be passed EC key");
        }


        this.random = random;
        this.state = opmode;
        buffer.reset();

    }


    public void engineInit(
        int opmode,
        Key key,
        SecureRandom random)
        throws InvalidKeyException
    {
        try
        {
            engineInit(opmode, key, (AlgorithmParameterSpec)null, random);
        }
        catch (InvalidAlgorithmParameterException e)
        {
            throw new IllegalArgumentException("can't handle supplied parameter spec");
        }

    }


    // Update methods - buffer the input

    public byte[] engineUpdate(
        byte[] input,
        int inputOffset,
        int inputLen)
    {
        buffer.write(input, inputOffset, inputLen);
        return null;
    }


    public int engineUpdate(
        byte[] input,
        int inputOffset,
        int inputLen,
        byte[] output,
        int outputOffset)
    {
        buffer.write(input, inputOffset, inputLen);
        return 0;
    }


    // Finalisation methods

    public byte[] engineDoFinal(
        byte[] input,
        int inputOffset,
        int inputLen)
        throws IllegalBlockSizeException, BadPaddingException
    {
        if (inputLen != 0)
        {
            buffer.write(input, inputOffset, inputLen);
        }

        final byte[] in = buffer.toByteArray();
        buffer.reset();

        // Convert parameters for use in IESEngine
        CipherParameters params = new IESWithCipherParameters(engineSpec.getDerivationV(),
            engineSpec.getEncodingV(),
            engineSpec.getMacKeySize(),
            engineSpec.getCipherKeySize());

        if (engineSpec.getNonce() != null)
        {
            params = new ParametersWithIV(params, engineSpec.getNonce());
        }

        final ECDomainParameters ecParams = ((ECKeyParameters)key).getParameters();

        final byte[] V;

        if (otherKeyParameter != null)
        {
            try
            {
                if (state == Cipher.ENCRYPT_MODE || state == Cipher.WRAP_MODE)
                {
                    engine.init(true, otherKeyParameter, key, params);
                }
                else
                {
                    engine.init(false, key, otherKeyParameter, params);
                }
                return engine.processBlock(in, 0, in.length);
            }
            catch (Exception e)
            {
                throw new BadPaddingException(e.getMessage());
            }
        }

        if (state == Cipher.ENCRYPT_MODE || state == Cipher.WRAP_MODE)
        {
            // Generate the ephemeral key pair
            ECKeyPairGenerator gen = new ECKeyPairGenerator();
            gen.init(new ECKeyGenerationParameters(ecParams, random));

            final boolean usePointCompression = engineSpec.getPointCompression();
            EphemeralKeyPairGenerator kGen = new EphemeralKeyPairGenerator(gen, new KeyEncoder()
            {
                public byte[] getEncoded(AsymmetricKeyParameter keyParameter)
                {
                    return ((ECPublicKeyParameters)keyParameter).getQ().getEncoded(usePointCompression);
                }
            });

            // Encrypt the buffer
            try
            {
                engine.init(key, params, kGen);

                return engine.processBlock(in, 0, in.length);
            }
            catch (Exception e)
            {
                throw new BadPaddingException(e.getMessage());
            }

        }
        else if (state == Cipher.DECRYPT_MODE || state == Cipher.UNWRAP_MODE)
        {
            // Decrypt the buffer
            try
            {
                engine.init(key, params, new ECIESPublicKeyParser(ecParams));

                return engine.processBlock(in, 0, in.length);
            }
            catch (InvalidCipherTextException e)
            {
                throw new BadPaddingException(e.getMessage());
            }
        }
        else
        {
            throw new IllegalStateException("cipher not initialised");
        }

    }

    public int engineDoFinal(
        byte[] input,
        int inputOffset,
        int inputLength,
        byte[] output,
        int outputOffset)
        throws ShortBufferException, IllegalBlockSizeException, BadPaddingException
    {

        byte[] buf = engineDoFinal(input, inputOffset, inputLength);
        System.arraycopy(buf, 0, output, outputOffset, buf.length);
        return buf.length;
    }

    /**
     * Classes that inherit from us
     */

    static public class ECIES
        extends IESCipher
    {
        public ECIES()
        {
            super(new IESEngine(new ECDHBasicAgreement(),
                new KDF2BytesGenerator(new SHA1Digest()),
                new HMac(new SHA1Digest())));
        }
    }

    static public class ECIESwithCipher
        extends IESCipher
    {
        public ECIESwithCipher(BlockCipher cipher)
        {
            super(new IESEngine(new ECDHBasicAgreement(),
                            new KDF2BytesGenerator(new SHA1Digest()),
                            new HMac(new SHA1Digest()),
                            new PaddedBufferedBlockCipher(cipher)));
        }

        public ECIESwithCipher(BlockCipher cipher, int ivLength)
        {
            super(new IESEngine(new ECDHBasicAgreement(),
                            new KDF2BytesGenerator(new SHA1Digest()),
                            new HMac(new SHA1Digest()),
                            new PaddedBufferedBlockCipher(cipher)), ivLength);
        }
    }

    static public class ECIESwithDESede
        extends ECIESwithCipher
    {
        public ECIESwithDESede()
        {
            super(new DESedeEngine());
        }
    }

    static public class ECIESwithAES
        extends ECIESwithCipher
    {
        public ECIESwithAES()
        {
            super(new AESEngine());
        }
    }

    static public class ECIESwithDESedeCBC
        extends ECIESwithCipher
    {
        public ECIESwithDESedeCBC()
        {
            super(new CBCBlockCipher(new DESedeEngine()), 8);
        }
    }

    static public class ECIESwithAESCBC
        extends ECIESwithCipher
    {
        public ECIESwithAESCBC()
        {
            super(new CBCBlockCipher(new AESEngine()), 16);
        }
    }

    /**
     * Backwards compatibility
     */
    static public class OldECIES
        extends IESCipher
    {
        public OldECIES()
        {
            super(new OldIESEngine(new ECDHBasicAgreement(),
                new KDF2BytesGenerator(new SHA1Digest()),
                new HMac(new SHA1Digest())));
        }
    }

    static public class OldECIESwithCipher
        extends IESCipher
    {
        public OldECIESwithCipher(BlockCipher baseCipher)
        {
            super(new OldIESEngine(new ECDHBasicAgreement(),
                new KDF2BytesGenerator(new SHA1Digest()),
                new HMac(new SHA1Digest()),
                new PaddedBufferedBlockCipher(baseCipher)));
        }

        public OldECIESwithCipher(BlockCipher baseCipher, int ivLength)
        {
            super(new OldIESEngine(new ECDHBasicAgreement(),
                new KDF2BytesGenerator(new SHA1Digest()),
                new HMac(new SHA1Digest()),
                new PaddedBufferedBlockCipher(baseCipher)), ivLength);
        }
    }

    static public class OldECIESwithDESede
        extends OldECIESwithCipher
    {
        public OldECIESwithDESede()
        {
            super(new DESedeEngine());
        }
    }

    static public class OldECIESwithAES
        extends OldECIESwithCipher
    {
        public OldECIESwithAES()
        {
            super(new AESEngine());
        }
    }

    static public class OldECIESwithDESedeCBC
        extends OldECIESwithCipher
    {
        public OldECIESwithDESedeCBC()
        {
            super(new CBCBlockCipher(new DESedeEngine()), 8);
        }
    }

    static public class OldECIESwithAESCBC
        extends OldECIESwithCipher
    {
        public OldECIESwithAESCBC()
        {
            super(new CBCBlockCipher(new AESEngine()), 16);
        }
    }
}
