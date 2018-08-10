package org.aion.wallet.util;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class AddressUtilsTest {
    @Test
    public void isValidWhenInputIsValid() {
        String full = "0xa0cafecafe111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(full), is(true));
        String stripped = "a0cafecafe111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(stripped), is(true));
    }

    @Test
    public void isValidWhenInputNotValid() {
        String tooShort = "0xa0cafecafe11111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(tooShort), is(false));
        String tooLong = "0xa0cafecafe11111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(tooLong), is(false));
        String uppercase = "0xa0CAFecafe11111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(uppercase), is(false));
        String badPrefix1 = "xa0cafecafe1111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(badPrefix1), is(false));
        String badPrefix2 = "a1cafecafe111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(badPrefix2), is(false));
        String badPrefix3 = "0xa1cafecafe111111111111111111111111111111111111111111111111111111";
        assertThat(AddressUtils.isValid(badPrefix2), is(false));
    }
}