package cuckoo.util;

import java.nio.ByteBuffer;

/**
 * @ClassName HashUtils
 * @Description
 * @Date 2023/7/11 11:14
 **/
public class HashUtils {

    public static String hash64(byte[] data, long seed) {
        MetroHash64 metroHash64 = new MetroHash64(seed).apply(ByteBuffer.wrap(data));
        return hex(metroHash64.get());
    }

    public static long hash64ToLong(byte[] data, long seed) {
        MetroHash64 metroHash64 = new MetroHash64(seed).apply(ByteBuffer.wrap(data));
        return metroHash64.get();
    }

    public static String hash128(byte[] data, long seed) {
        MetroHash128 metroHash128 = new MetroHash128(seed).apply(ByteBuffer.wrap(data));
        return hex(metroHash128.getHigh()) + hex(metroHash128.getLow());
    }

    static String hex(long hash) {
        return String.format("%016X", hash);
    }

    static String hex(byte[] bytes) {
        String hex = "";
        for (byte b : bytes) {
            hex += String.format("%02X", b);
        }
        return hex;
    }

    public static void main(String[] args) {
        System.out.println(HashUtils.hash64(new Object().toString().getBytes(), 6));
    }

}
