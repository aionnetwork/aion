package org.aion.avm.stub;

public enum AvmVersion {
    VERSION_1(1),
    VERSION_2(2);

    public final int number;

    AvmVersion(int number) {
        this.number = number;
    }

    public static AvmVersion fromNumber(int number) {
        switch (number) {
            case 1: return VERSION_1;
            case 2: return VERSION_2;
            default: return null;
        }
    }

    public static int highestSupportedVersion() {
        return 2;
    }
}
