package org.aion.rlp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.Arrays;
import org.junit.Test;

public class ValueTest {

    @Test
    public void testCmp() {
        Value val1 = new Value("hello");
        Value val2 = new Value("world");

        assertFalse("Expected values not to be equal", val1.cmp(val2));

        Value val3 = new Value("hello");
        Value val4 = new Value("hello");

        assertTrue("Expected values to be equal", val3.cmp(val4));

        Value inter = new Value(new Object[] {1});
        Object[] interExp = new Object[] {1};

        assertTrue(new Value(inter.asObj()).cmp(new Value(interExp)));

        String dog = "dog";
        Value val5 = new Value(dog);
        Value val6 = Value.fromRlpEncoded(Hex.decode("83646f67"));
        assertThat(val5.cmp(val6)).isTrue();

        val5 = Value.fromRlpEncoded(Hex.decode("83646f67"));
        assertThat(val5.cmp(val6)).isTrue();

        val5 = Value.fromRlpEncoded(Hex.decode("83636174"));
        assertThat(val5.cmp(val6)).isFalse();

        assertThat(val6.cmp(null)).isFalse();
    }

    @Test
    public void testTypes() {
        String expectedStr = "str";
        Value str = new Value(expectedStr);
        assertThat(str.asString()).isEqualTo(expectedStr);
        assertThat(str.asBytes()).isEqualTo(expectedStr.getBytes());
        assertThat(str.isHashCode()).isFalse();

        Value num = new Value(1);
        assertEquals(num.asInt(), 1);
        assertThat(num.length()).isEqualTo(0);
        assertThat(num.isHashCode()).isFalse();

        Value byt = new Value(new byte[] {1, 2, 3, 4});
        byte[] bytExp = new byte[] {1, 2, 3, 4};
        assertTrue(Arrays.equals(byt.asBytes(), bytExp));
        assertThat(byt.isHashCode()).isFalse();

        Value bigInt = new Value(BigInteger.valueOf(10));
        BigInteger bigExp = BigInteger.valueOf(10);
        assertEquals(bigInt.asBigInt(), bigExp);
        assertThat(bigInt.isHashCode()).isFalse();

        Value byteArray = new Value(new byte[32]);
        assertThat(byteArray.isHashCode()).isTrue();
    }

    @Test
    public void longListRLPBug_1() {
        String testRlp =
                "f7808080d387206f72726563748a626574656c676575736580d387207870726573738a70726564696361626c658080808080808080808080";

        Value val = Value.fromRlpEncoded(Hex.decode(testRlp));

        assertEquals(testRlp, Hex.toHexString(val.encode()));
    }

    @Test
    public void testFromRlpEncoded_wNull() {
        assertThat(Value.fromRlpEncoded(null)).isNull();
    }

    @Test
    public void testNewValue_wNull() {
        assertThat(new Value(null).asObj()).isNull();
        assertThat(new Value(null).isNull()).isTrue();
        assertThat(new Value(null).toString()).isEqualTo("");
    }

    @Test(expected = RuntimeException.class)
    public void testGet_wException() {
        Value.fromRlpEncoded(Hex.decode("c0")).get(-1);
    }

    @Test
    public void testToString_wString() {
        String dog = "dog";
        Value val = new Value(dog);
        assertThat(val.toString()).isEqualTo("dog");

        byte[] enc = Hex.decode("83646f67");
        val = Value.fromRlpEncoded(enc);
        assertThat(val.toString()).isEqualTo("'dog'");

        val = new Value(dog.getBytes());
        assertThat(val.toString()).isEqualTo("'dog'");

        val = new Value(new byte[32]);
        assertThat(val.toString())
                .isEqualTo("0000000000000000000000000000000000000000000000000000000000000000");

        val = new Value(new byte[] {9});
        assertThat(val.toString()).isEqualTo("09");

        val = new Value(new byte[] {1, 2, 3});
        assertThat(val.toString()).isEqualTo("010203");

        val = Value.fromRlpEncoded(Hex.decode("c6827a77c10401"));
        assertThat(val.toString()).isEqualTo(" ['zw',  [04] , 01] ");

        val = Value.fromRlpEncoded(Hex.decode("cc83646f6783676f6483636174"));
        assertThat(val.toString()).isEqualTo(" ['dog', 'god', 'cat'] ");
    }
}
