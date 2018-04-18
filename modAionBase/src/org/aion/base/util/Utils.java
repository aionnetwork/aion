/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 ******************************************************************************/
package org.aion.base.util;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static SecureRandom random = new SecureRandom();

    public static final Object dummy = new Object();

    /**
     * @param number
     *            should be in form '0x34fabd34....'
     * @return String
     */
    public static BigInteger unifiedNumericToBigInteger(String number) {

        boolean match = Pattern.matches("0[xX][0-9a-fA-F]+", number);
        if (!match) {
            return (new BigInteger(number));
        } else {
            number = number.substring(2);
            number = number.length() % 2 != 0 ? "0".concat(number) : number;
            byte[] numberBytes = Hex.decode(number);
            return (new BigInteger(1, numberBytes));
        }
    }

    /**
     * Return formatted Date String: yyyy.MM.dd HH:mm:ss Based on Unix's time()
     * input in seconds
     *
     * @param timestamp
     *            seconds since start of Unix-time
     * @return String formatted as - yyyy.MM.dd HH:mm:ss
     */
    public static String longToDateTime(long timestamp) {
        Date date = new Date(timestamp * 1000);
        DateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        return formatter.format(date);
    }

    // public static ImageIcon getImageIcon(String resource) {
    // URL imageURL = ClassLoader.getSystemResource(resource);
    // ImageIcon image = new ImageIcon(imageURL);
    // return image;
    // }
    static BigInteger _1000_ = new BigInteger("1000");

    public static String getValueShortString(BigInteger number) {
        BigInteger result = number;
        int pow = 0;
        while (result.compareTo(_1000_) == 1 || result.compareTo(_1000_) == 0) {
            result = result.divide(_1000_);
            pow += 3;
        }
        return result.toString() + "\u00b7(" + "10^" + pow + ")";
    }

    /**
     * Decodes a hex string to address bytes and checks validity
     *
     * @param hex
     *            - a hex string of the address, e.g.,
     *            6c386a4b26f73c802f34673f7248bb118f97424a
     * @return - decode and validated address byte[]
     */
    public static byte[] addressStringToBytes(String hex) {
        final byte[] addr;
        try {
            addr = Hex.decode(hex);
        } catch (Exception addressIsNotValid) {
            return null;
        }

        if (isValidAddress(addr)) {
            return addr;
        }
        return null;
    }

    public static boolean isValidAddress(byte[] addr) {
        return addr != null && addr.length == 20;
    }

    /**
     * @param addr
     *            length should be 20
     * @return short string represent 1f21c...
     */
    public static String getAddressShortString(byte[] addr) {

        if (!isValidAddress(addr)) {
            throw new Error("not an address");
        }

        String addrShort = Hex.toHexString(addr, 0, 3);

        StringBuffer sb = new StringBuffer();
        sb.append(addrShort);
        sb.append("...");

        return sb.toString();
    }

    public static SecureRandom getRandom() {
        return random;
    }

    public static double JAVA_VERSION = getJavaVersion();

    static double getJavaVersion() {
        String version = System.getProperty("java.version");

        // on android this property equals to 0
        if (version.equals("0")) {
            return 0;
        }

        int dpos = 0;
        for (; dpos < version.length(); dpos++) {
            if (version.charAt(dpos) == '-') {
                version = version.substring(0, dpos);
                break;
            }
        }

        if (version.length() == 1) {
            return Double.parseDouble(version);
        }

        int pos = 0, count = 0;
        for (; pos < version.length() && count < 2; pos++) {
            if (version.charAt(pos) == '.') {
                count++;
            }
        }
        return Double.parseDouble(version.substring(0, pos - 1));
    }

    public static String getHashListShort(List<byte[]> blockHashes) {
        if (blockHashes.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        String firstHash = Hex.toHexString(blockHashes.get(0));
        String lastHash = Hex.toHexString(blockHashes.get(blockHashes.size() - 1));
        return sb.append(" ").append(firstHash).append("...").append(lastHash).toString();
    }

    public static String getNodeIdShort(String nodeId) {
        return nodeId == null ? "<null>" : nodeId.substring(0, 8);
    }

    public static long toUnixTime(long javaTime) {
        return javaTime / 1000;
    }

    public static long fromUnixTime(long unixTime) {
        return unixTime * 1000;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] mergeArrays(T[]... arr) {
        int size = 0;
        for (T[] ts : arr) {
            size += ts.length;
        }
        T[] ret = (T[]) Array.newInstance(arr[0].getClass().getComponentType(), size);
        int off = 0;
        for (T[] ts : arr) {
            System.arraycopy(ts, 0, ret, off, ts.length);
            off += ts.length;
        }
        return ret;
    }

    public static String align(String s, char fillChar, int targetLen, boolean alignRight) {
        if (targetLen <= s.length()) {
            return s;
        }
        String alignString = repeat("" + fillChar, targetLen - s.length());
        return alignRight ? alignString + s : s + alignString;

    }

    public static String repeat(String s, int n) {
        if (s.length() == 1) {
            byte[] bb = new byte[n];
            Arrays.fill(bb, s.getBytes()[0]);
            return new String(bb);
        } else {
            StringBuilder ret = new StringBuilder();
            for (int i = 0; i < n; i++) {
                ret.append(s);
            }
            return ret.toString();
        }
    }


    private static final Pattern matchPattern = Pattern.compile("^([0-9]+)([a-zA-Z]+)$");
    public static final long KILO_BYTE = 1024;
    public static final long MEGA_BYTE = 1048576;
    public static final long GIGA_BYTE = 1073741824;
    /**
     * <p>
     * Matches file sizes based on fileSize string, in the format:
     * [numericalValue][sizeDescriptor]
     * </p>
     *
     * <p>
     * Examples of acceptable formats:
     *
     * <li>
     *   <ul>10b</ul>
     *   <ul>10B</ul>
     *   <ul>10K</ul>
     *   <ul>10KB</ul>
     *   <ul>10kB</ul>
     *   <ul>10M</ul>
     *   <ul>10mB</ul>
     *   <ul>10MB</ul>
     *   <ul>10G</ul>
     *   <ul>10gB</ul>
     *   <ul>10GB</ul>
     * </li>
     * </p>
     *
     * <p>
     * Commas are <b>not</b> accepted by the parser, and are considered invalid.
     *
     * Note: Anything beyond {@code gigaByte (GB, G, gB)} is not considered valid, and will
     * be treated as a parse exception.
     *
     * Note: this function assumes the binary representation of magnitudes,
     * therefore 1kB (kiloByte) is not {@code 1000 bytes} but rather {@code 1024 bytes}.
     * </p>
     *
     * @param fileSize file size string
     * @return {@code Optional.of(fileSizeInt)} if we were able to successfully decode
     * the filesize string, otherwise outputs {@code Optional.empty()} indicating that
     * we were unable to decode the file size string, this usually refers to some
     * sort of syntactic error made by the user.
     */
    public static Optional<Long> parseSize(String fileSize) {
        Matcher m = matchPattern.matcher(fileSize);
        // if anything does not match
        if (!m.find()) {
            return Optional.empty();
        }

        String numerical = m.group(1);
        String sizeSuffix = m.group(2);

        long size = Integer.parseInt(numerical);
        switch (sizeSuffix) {
            case "B":
                break;
            case "K":
            case "kB":
            case "KB":
                // process kiloByte (1024 * byte) here
                size = size * KILO_BYTE;
                break;
            case "M":
            case "mB":
            case "MB":
                size = size * MEGA_BYTE;
                break;
            case "G":
            case "gB":
            case "GB":
                size = size * GIGA_BYTE;
                break;
            default:
                return Optional.empty();
        }
        return Optional.of(size);
    }
}
