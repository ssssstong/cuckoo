package cuckoo;

import cuckoo.util.CommonUtils;

/*
* 排列编码(4bit to 3bit)
* */
public class PermEncoding {

    short permSize = 3876;
    short[] decTable;
    short[] encTable;

    public PermEncoding() {
        /*
        * 为什么只转换指纹的4bit？
        * 答：
        * 参数定义：
        * f:用于转换的指纹bit数
        * M1=n(n+1)(n+2)(n+3)/24=n^4+6n^3+(6+2+3)n^2+6n,n=2^f
        * M2=2^x,x是最终优化的空间大小
        *
        * 由于指纹数为4，并且考虑到编码的通用性，因此排列算法压缩的空间需要平均到每个指纹上（每个指纹压缩同等的bit数）；
        * 假设可以优化到8bit（每个指纹压缩2bit），则x<=4f-8，且M2>=M1
        * 但是M2=2^(4f-8)<2^4f<M1，假设不成立，因此优化空间的bit数x<4f-8，最终压缩的空间bit数小于8bit；
        * 又因为M2=2^(4f-4)>M1，所以最终选择压缩4bit
        *
        * 为什么是3876？
        * 答：重复组合：每个指纹有16种组合，从16种组合取4个（可重复）
        *
        * 为什么指纹数是4个？
        * 答：空间效率最优
        * */
        int oriSize = 1 << 16;
        //init table
        decTable = new short[permSize];
        encTable = new short[oriSize];

        generateTable((byte)0, (byte)0, new byte[4], (short)0);
    }

    public short encode(byte[] lowbits) {
        return encTable[pack(lowbits) & 0x0000ffff];
    }

    public void decode(short codeword, byte[] lowbits) {
        unpack(decTable[codeword], lowbits);
    }

    private int generateTable(byte base, byte k, byte[] dst, int idx) {
        for (byte i = base; i < 16; i++) {
            dst[k] = i;
            if (k + 1 < 4) {
                idx = generateTable(i, (byte) (k + 1), dst, idx);
            } else {
                decTable[idx] = pack(dst);
                encTable[pack(dst) & 0x0000ffff] = (short)idx;
                idx++;
            }
        }
        return idx;
    }

    private short pack(byte[] tags) {
        short u1 = CommonUtils.unsignedIntToShort((CommonUtils.unsignedLeftShift(tags[0], 0) | CommonUtils.unsignedLeftShift(tags[1], 8)) & 0x0f0f);//0000111100001111
        short u2 = CommonUtils.unsignedIntToShort(CommonUtils.unsignedLeftShift(tags[2], 0) | CommonUtils.unsignedLeftShift(tags[3], 8));//0000111100001111

        return CommonUtils.unsignedIntToShort(CommonUtils.unsignedLeftShift(u1, 0) | CommonUtils.unsignedLeftShift(u2,4));
    }

    private void unpack(short codeword, byte[] tags) {
        tags[0] = CommonUtils.unsignedIntToByte(codeword & 0x000f);
        tags[1] = CommonUtils.unsignedIntToByte((codeword >>> 8) & 0x000f);
        tags[2] = CommonUtils.unsignedIntToByte((codeword >>> 4) & 0x000f);
        tags[3] = CommonUtils.unsignedIntToByte((codeword >>> 12) & 0x000f);
    }

    public static void main(String[] args) {
        long i = 2249603839077156L;
        System.out.println(((i & 0x0fffffff)));
    }
}
