package org.aion.util.others;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static final Object dummy = new Object();

    private static final Pattern matchPattern = Pattern.compile("^([0-9]+)([a-zA-Z]+)$");
    public static final long KILO_BYTE = 1024;
    public static final long MEGA_BYTE = 1048576;
    public static final long GIGA_BYTE = 1073741824;
    /**
     * Matches file sizes based on fileSize string, in the format: [numericalValue][sizeDescriptor]
     *
     * <p>Examples of acceptable formats:
     *   10b,10B,10K,10KB,10kB,10M,10mB,10MB,10G,10gB,10GB
     *
     * <p>Commas are <b>not</b> accepted by the parser, and are considered invalid.
     *
     * <p>Note: Anything beyond {@code gigaByte (GB, G, gB)} is not considered valid, and will be
     * treated as a parse exception.
     *
     * <p>Note: this function assumes the binary representation of magnitudes, therefore 1kB
     * (kiloByte) is not {@code 1000 bytes} but rather {@code 1024 bytes}.
     *
     * @param fileSize file size string
     * @return {@code Optional.of(fileSizeInt)} if we were able to successfully decode the filesize
     *     string, otherwise outputs {@code Optional.empty()} indicating that we were unable to
     *     decode the file size string, this usually refers to some sort of syntactic error made by
     *     the user.
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
