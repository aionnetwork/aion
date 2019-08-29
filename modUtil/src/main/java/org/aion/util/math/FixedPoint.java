package org.aion.util.math;

import com.google.common.annotations.VisibleForTesting;

import java.math.BigDecimal;
import java.math.BigInteger;

// This class can NOT be used for negative values

public final class FixedPoint {

    // 70 bits of precision
    public static final int PRECISION = 70;
    public static final BigInteger MAX_PRECISION = BigInteger.ONE.shiftLeft(PRECISION);

    public static final FixedPoint ZERO = new FixedPoint(BigInteger.ZERO);
    public static final FixedPoint ONE = new FixedPoint(BigInteger.ONE.shiftLeft(PRECISION));

    private final BigInteger value;
    
    // This constructor should be used with care
    public FixedPoint(BigInteger value) {
        
        if (null == value || value.signum() < 0) {
            throw new IllegalArgumentException("Cannot create a FixedPoint with negative value");
        }
        
        this.value = value;
    }
    
    public static FixedPoint fromString(String inputString) {
        
        if (null == inputString) {
            throw new IllegalArgumentException("Null input to FixedPoint method");
        }
        
        BigDecimal inputBD = new BigDecimal(inputString);
        FixedPoint returnValue;

        if (inputBD.signum() < 0) {
            throw new IllegalArgumentException("FixedPoint can only represent positive rational numbers");
        } else {
            try {
                BigInteger value = inputBD.multiply(new BigDecimal(MAX_PRECISION)).toBigInteger();
                returnValue = new FixedPoint(value);
            } catch (ArithmeticException arithmeticException) {
                throw new IllegalArgumentException("Precision would be lost when creating this FixedPoint. " +
                        "Please try again with at most " + PRECISION + " decimal places.");
            }
        }
        
        return returnValue;
    }

    public FixedPoint add(FixedPoint addend) {
        
        if (null == addend) {
            throw new IllegalArgumentException("Null input to FixedPoint method");
        }
        
        return new FixedPoint(value.add(addend.value));
    }

    // do not create a negative value through this!
    public FixedPoint subtract(FixedPoint subtrahend) {

        if (null == subtrahend) {
            throw new IllegalArgumentException("Null input to FixedPoint method");
        }
        
        if (this.value.compareTo(subtrahend.value) < 0) {
            throw new IllegalArgumentException("FixedPoint can only represent positive rational numbers");
        } else {
            return new FixedPoint(value.subtract(subtrahend.value));
        }
    }

    public FixedPoint multiplyInteger(BigInteger multiplicand) {

        if (null == multiplicand) {
            throw new IllegalArgumentException("Null input to FixedPoint method");
        }
        
        return new FixedPoint(value.multiply(multiplicand));
    }

    public FixedPoint multiplyInteger(int multiplicand) {
        return multiplyInteger(BigInteger.valueOf(multiplicand));
    }
    
    public FixedPoint divideByPowerOfTwo(int shift) {
        
        if (shift <= 0) {
            throw new IllegalArgumentException("Can only divide by a positive power of two");
        }

        return new FixedPoint(value.shiftRight(shift));
    }
    
    public int compareTo(FixedPoint comparand) {
        
        if (null == comparand) {
            throw new IllegalArgumentException("Null input to FixedPoint method");
        }
        
        return value.compareTo(comparand.value);
    }
    
    @VisibleForTesting
    BigDecimal toBigDecimal() {

        BigDecimal maxPrecision = new BigDecimal(MAX_PRECISION);
        
        return new BigDecimal(value).divide(maxPrecision).stripTrailingZeros();
    }
    
    public BigInteger toBigInteger() {
        return value.shiftRight(PRECISION);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FixedPoint that = (FixedPoint) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return toBigDecimal().toPlainString();
    }
}
