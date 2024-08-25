package cuckoo.util;

public class CommonUtils {

    public static int getNextPow2(long n) {
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        n |= n >> 32;
        n++;
        return (int) n;
    }

    public static double maxLoadFactor(int tagsPerBucket) {
        switch(tagsPerBucket) {
            case 2:
                return 0.85;
            case 4:
                return 0.96;
            default:
                return 0.99;
        }
    }

    public static long unsignedLeftShift(int val, int shift) {
        return (val & 0xffffffffL) << shift;
    }

    public static long unsignedLeftShift(int val, int... shifts) {
        long result = val & 0xffffffffL;
        for (int shift : shifts) {
            result <<= shift;
        }
        return result;
    }

    public static int unsignedLeftShift(short val, int shift) {
        return (val & 0xffff) << shift;
    }

    public static int unsignedLeftShift(byte val, int shift) {
        return (val & 0xff) << shift;
    }

    public static short unsignedIntToShort(int val) {
        return (short) (val & 0x0000ffff);
    }

    public static byte unsignedIntToByte(int val) {
        return (byte) (val & 0x000000ff);
    }

    public static byte unsignedShortToByte(short val) {
        return (byte) (val & 0x00ff);
    }

    public static int unsignedLongToInt(long val) {
        return (int) (val & 0xffffffffL);
    }

    public static short unsignedLongToShort(long val) {
        return (short) (val & 0x0000ffffL);
    }

    public static byte unsignedLongToByte(long val) {
        return (byte) (val & 0x000000ffL);
    }

}
