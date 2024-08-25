package cuckoo;

import cuckoo.exception.CuckooException;
import cuckoo.util.CommonUtils;

import java.util.Arrays;
import java.util.Random;

public class Tree {

    /*
    * 数据组成结构：元数据+指纹
    * 指纹=目录位+12位码字（4bit to 3bit）
    * */

    private static int birdsPNest = 4;
    private static int codewordSize = 12;
    private static int cBirdSize = 4;

    private static int bitsPerByte = 8;
    private static int bytesPerUint64 = 8;
    private static int bytesPerUint32 = 4;

    //每只鸟的大小(指纹大小bit)
    private int bitsPerBird;
    //指纹目录位大小
    private int kDirBitsPerBird;
    //巢的大小bit
    private int kBitsPerNest;
    //巢的大小byte
    private int kBytesPerNest;
    //目录位标记
    private int kDirPerBirdMask;


    //树的长度
    private int len;
    //巢的数量
    private int nestNum;
    //巢,存放顺序：低位-高位，从左往右
    private byte[] nests;
    //映射工具
    private PermEncoding permEncoding;

    protected Tree init(int sizePerNest, int bitsPerBird, int nestNum, byte[] initNests) {
        this.nestNum = nestNum;
        this.bitsPerBird = bitsPerBird;

        this.kDirBitsPerBird = this.bitsPerBird - cBirdSize;
        this.kBitsPerNest = (this.bitsPerBird - 1) * birdsPNest;
        this.kBytesPerNest = (this.kBitsPerNest + 7) >> 3;//这里的7用于进1位
        this.kDirPerBirdMask = CommonUtils.unsignedLongToInt(CommonUtils.unsignedLeftShift((1 << this.kDirBitsPerBird) - 1, cBirdSize));
        // NOTE: use 7 extra bytes to avoid overrun as we always read a uint64
        this.len = ((this.kBitsPerNest * this.nestNum + 7) >> 3) + 7;
        //init nests
        if (initNests == null) {
            initNests = new byte[this.len];
        } else if (initNests.length != this.len) {
            throw new CuckooException("tree length not expected");
        }

        this.nests = initNests;
        this.permEncoding = new PermEncoding();
        return this;
    }

    private void sortNest(int[] nest) {
        int t;
        if ((nest[0] & 0x0f) > (nest[2] & 0x0f)) {
            t = nest[2];
            nest[2] = nest[0];
            nest[0] = t;
        }
        if ((nest[1] & 0x0f) > (nest[3] & 0x0f)) {
            t = nest[3];
            nest[3] = nest[1];
            nest[1] = t;
        }
        if ((nest[0] & 0x0f) > (nest[1] & 0x0f)) {
            t = nest[1];
            nest[1] = nest[0];
            nest[0] = t;
        }
        if ((nest[2] & 0x0f) > (nest[3] & 0x0f)) {
            t = nest[3];
            nest[3] = nest[2];
            nest[2] = t;
        }
        if ((nest[1] & 0x0f) > (nest[2] & 0x0f)) {
            t = nest[2];
            nest[2] = nest[1];
            nest[1] = t;
        }
    }


    private void readBird(int idx, int[] nest) {
        //根据idx找出pos
        //将nest中的4只bird的后三位转换成解码数据（3 to 4），并存放至变量nest中返回
        int pos = idx * kBitsPerNest >>> 3;
        short nestData16;
        int nestData32;
        long nestData64;
        short codeword;
        switch (this.bitsPerBird) {//获取目录位
            case 5:
                //1dirBit,16bitPerNest
                //读取pos~pos+1
                //idx整除直接取，不能整除偏移4bit取
                nestData16 = CommonUtils.unsignedIntToShort(CommonUtils.unsignedLeftShift(nests[pos], 0)
                        | CommonUtils.unsignedLeftShift(nests[pos + 1], 8));
                codeword = CommonUtils.unsignedIntToShort(nestData16 & 0x0fff);
                nest[0] = (nestData16 >>> 8) & this.kDirPerBirdMask;
                nest[1] = (nestData16 >>> 9) & this.kDirPerBirdMask;
                nest[2] = (nestData16 >>> 10) & this.kDirPerBirdMask;
                nest[3] = (nestData16 >>> 11) & this.kDirPerBirdMask;
                break;
            case 6:
                //2dirBit,20bitPerNest
                nestData32 = CommonUtils.unsignedLeftShift(nests[pos], 0)
                        | CommonUtils.unsignedLeftShift(nests[pos + 1], 8)
                        | CommonUtils.unsignedLeftShift(nests[pos + 2], 16)
                        | CommonUtils.unsignedLeftShift(nests[pos + 3], 24);
                codeword = (short) (nestData32 >>> ((idx & 1) << 2) & 0x0fff);
                nest[0] = (nestData32 >>> (8 + ((idx & 1) << 2))) & this.kDirPerBirdMask;
                nest[1] = (nestData32 >>> (10 + ((idx & 1) << 2))) & this.kDirPerBirdMask;
                nest[2] = (nestData32 >>> (12 + ((idx & 1) << 2))) & this.kDirPerBirdMask;
                nest[3] = (nestData32 >>> (14 + ((idx & 1) << 2))) & this.kDirPerBirdMask;
                break;
            case 7:
                //3dirBit,24bitPerNest
                nestData32 = CommonUtils.unsignedLeftShift(nests[pos], 0)
                        | CommonUtils.unsignedLeftShift(nests[pos + 1], 8)
                        | CommonUtils.unsignedLeftShift(nests[pos + 2], 16)
                        | CommonUtils.unsignedLeftShift(nests[pos + 3], 24);
                codeword = (short) (nestData32 & 0x0fff);
                nest[0] = (nestData32 >>> 8) & this.kDirPerBirdMask;
                nest[1] = (nestData32 >>> 11) & this.kDirPerBirdMask;
                nest[2] = (nestData32 >>> 14) & this.kDirPerBirdMask;
                nest[3] = (nestData32 >>> 17) & this.kDirPerBirdMask;
                break;
            case 8:
                //4dirBit,28bitPerNest
                nestData32 = CommonUtils.unsignedLeftShift(nests[pos], 0)
                        | CommonUtils.unsignedLeftShift(nests[pos + 1], 8)
                        | CommonUtils.unsignedLeftShift(nests[pos + 2], 16)
                        | CommonUtils.unsignedLeftShift(nests[pos + 3], 24);
                codeword = (short) ((nestData32 >>> ((idx & 1) << 2)) & 0x0fff);
                nest[0] = (nestData32 >>> (8 + ((idx & 1) << 2))) & this.kDirPerBirdMask;
                nest[1] = (nestData32 >>> (12 + ((idx & 1) << 2))) & this.kDirPerBirdMask;
                nest[2] = (nestData32 >>> (16 + ((idx & 1) << 2))) & this.kDirPerBirdMask;
                nest[3] = (nestData32 >>> (20 + ((idx & 1) << 2))) & this.kDirPerBirdMask;
                break;
            case 9:
                //5dirBit,32bitPerNest
                nestData32 = CommonUtils.unsignedLeftShift(nests[pos], 0)
                        | CommonUtils.unsignedLeftShift(nests[pos + 1], 8)
                        | CommonUtils.unsignedLeftShift(nests[pos + 2], 16)
                        | CommonUtils.unsignedLeftShift(nests[pos + 3], 24);
                codeword = (short) (nestData32 & 0x0fff);
                nest[0] = (nestData32 >>> 8) & this.kDirPerBirdMask;
                nest[1] = (nestData32 >>> 13) & this.kDirPerBirdMask;
                nest[2] = (nestData32 >>> 18) & this.kDirPerBirdMask;
                nest[3] = (nestData32 >>> 23) & this.kDirPerBirdMask;
                break;
            case 13:
                //9dirBit,48bitPerNest
                nestData64 = CommonUtils.unsignedLeftShift(nests[pos] & 0xff, 0)
                        | CommonUtils.unsignedLeftShift(nests[pos + 1] & 0xff, 8)
                        | CommonUtils.unsignedLeftShift(nests[pos + 2] & 0xff, 16)
                        | CommonUtils.unsignedLeftShift(nests[pos + 3] & 0xff, 24)
                        | CommonUtils.unsignedLeftShift(nests[pos + 4] & 0xff, 32)
                        | CommonUtils.unsignedLeftShift(nests[pos + 5] & 0xff, 40)
                        | CommonUtils.unsignedLeftShift(nests[pos + 6] & 0xff, 48)
                        | CommonUtils.unsignedLeftShift(nests[pos + 7] & 0xff, 56);
                codeword = (short) (nestData64 & 0x0fffL);
                nest[0] = CommonUtils.unsignedLongToInt((nestData64 >>> 8) & this.kDirPerBirdMask);
                nest[1] = CommonUtils.unsignedLongToInt((nestData64 >>> 17) & this.kDirPerBirdMask);
                nest[2] = CommonUtils.unsignedLongToInt((nestData64 >>> 26) & this.kDirPerBirdMask);
                nest[3] = CommonUtils.unsignedLongToInt((nestData64 >>> 35) & this.kDirPerBirdMask);
                break;
            case 17:
                //13dirBit,64bitPerNest
                nestData64 = CommonUtils.unsignedLeftShift(nests[pos] & 0xff, 0)
                        | CommonUtils.unsignedLeftShift(nests[pos + 1] & 0xff, 8)
                        | CommonUtils.unsignedLeftShift(nests[pos + 2] & 0xff, 16)
                        | CommonUtils.unsignedLeftShift(nests[pos + 3] & 0xff, 24)
                        | CommonUtils.unsignedLeftShift(nests[pos + 4] & 0xff, 32)
                        | CommonUtils.unsignedLeftShift(nests[pos + 5] & 0xff, 40)
                        | CommonUtils.unsignedLeftShift(nests[pos + 6] & 0xff, 48)
                        | CommonUtils.unsignedLeftShift(nests[pos + 7] & 0xff, 56);
                codeword = (short) (nestData64 & 0x0fffL);
                nest[0] = CommonUtils.unsignedLongToInt((nestData64 >>> 8) & this.kDirPerBirdMask);
                nest[1] = CommonUtils.unsignedLongToInt((nestData64 >>> 21) & this.kDirPerBirdMask);
                nest[2] = CommonUtils.unsignedLongToInt((nestData64 >>> 34) & this.kDirPerBirdMask);
                nest[3] = CommonUtils.unsignedLongToInt((nestData64 >>> 47) & this.kDirPerBirdMask);
                break;
            default:
                int rShift = (this.kBitsPerNest * idx) & (bitsPerByte - 1);//对应(i & 1) << 2，取后三位
                // tag is max 32bit, store 31bit per tag, so max occupies 16 bytes
                int kBytes = (rShift + this.kBitsPerNest + 7) >> 3;//计算一个nest的最大索引
                long u1 = 0L;//低8字节
                long u2 = 0L;//高8字节
                for (int k = 0; k < kBytes; k++) {//由于存储单元大小为64bit，所以如果传入的数据超过64bit，则需要将超出部分存入另一64bit存储单元
                    if (k < bytesPerUint64) {
                        u1 |= CommonUtils.unsignedLeftShift(nests[pos + k] & 0xff, k * bitsPerByte);
                    } else {
                        u2 |= CommonUtils.unsignedLeftShift(nests[pos + k] & 0xff, (k - bytesPerUint64) * bitsPerByte);
                    }
                }

                codeword = (short) ((u1 >>> rShift) & 0x0fff);
                for (int k = 0; k < birdsPNest; k++) {
                    //shift：u2偏移位数
                    int shift = codewordSize - cBirdSize + k * this.kDirBitsPerBird - 64 + rShift;
                    if (( codewordSize + this.kDirBitsPerBird * (k + 1) + rShift ) <= 64) {
                        nest[k] = CommonUtils.unsignedLongToInt(u1 >>> (codewordSize - cBirdSize + k * this.kDirBitsPerBird) >>> rShift);
                    } else if (( codewordSize + this.kDirBitsPerBird * k + rShift ) >= 64) {
                        if (shift < 0) {
                            nest[k] = CommonUtils.unsignedLongToInt(CommonUtils.unsignedLeftShift((int)(u2 >>> (shift + cBirdSize)), cBirdSize));//先右移，再左移四位，预留后面的指纹位
                        } else {
                            nest[k] = CommonUtils.unsignedLongToInt(u2 >>> shift);
                        }
                    } else {
                        nest[k] = CommonUtils.unsignedLongToInt(u1 >>> (codewordSize - cBirdSize + k * this.kDirBitsPerBird) >>> rShift);
                        nest[k] |= CommonUtils.unsignedLongToInt(CommonUtils.unsignedLeftShift(CommonUtils.unsignedLongToInt(u2), -shift));
                    }

                    nest[k] &= this.kDirPerBirdMask;
                }

        }
        byte[] lowBits = new byte[4];
        this.permEncoding.decode(codeword, lowBits);
        nest[0] |= (lowBits[0] & 0x0f);
        nest[1] |= (lowBits[1] & 0x0f);
        nest[2] |= (lowBits[2] & 0x0f);
        nest[3] |= (lowBits[3] & 0x0f);
    }

    private void writeBird(int idx, int[] nest) {
        sortNest(nest);
        //根据idx找出pos
        //将nest中的4只bird转换成编码数据（4 to 3），并存放至this.nests中
        int pos = idx * this.kBitsPerNest >>> 3;
        byte[] lowBits = new byte[4];
        int[] highBits = new int[4];

        lowBits[0] = (byte) (nest[0] & 0x0f);
        lowBits[1] = (byte) (nest[1] & 0x0f);
        lowBits[2] = (byte) (nest[2] & 0x0f);
        lowBits[3] = (byte) (nest[3] & 0x0f);

        highBits[0] = nest[0] & 0xfffffff0;
        highBits[1] = nest[1] & 0xfffffff0;
        highBits[2] = nest[2] & 0xfffffff0;
        highBits[3] = nest[3] & 0xfffffff0;

        short codeword = this.permEncoding.encode(lowBits);
        short nestData;
        int nestData32;
        long nestData64;
        switch (this.kBitsPerNest) {
            case 16:
                //1dir,16/4-3
                nestData = CommonUtils.unsignedLongToShort(CommonUtils.unsignedLeftShift(codeword, 0)
                        | CommonUtils.unsignedLeftShift(highBits[0], 8)
                        | CommonUtils.unsignedLeftShift(highBits[1], 9)
                        | CommonUtils.unsignedLeftShift(highBits[2], 10)
                        | CommonUtils.unsignedLeftShift(highBits[3], 11));
                nests[pos] = CommonUtils.unsignedShortToByte(nestData);
                nests[pos + 1] = CommonUtils.unsignedIntToByte(nestData >>> 8);
                break;
            case 20:
                //2dir
                nestData32 = CommonUtils.unsignedLeftShift(nests[pos], 0)//需要去除符号位
                        | CommonUtils.unsignedLeftShift(nests[pos + 1], 8)
                        | CommonUtils.unsignedLeftShift(nests[pos + 2], 16)
                        | CommonUtils.unsignedLeftShift(nests[pos + 3], 24);
                if ((idx & 1) > 0) {
                    nestData32 &= 0xff00000f;
                    nestData32 |= (CommonUtils.unsignedLeftShift(codeword, 4)
                            | CommonUtils.unsignedLeftShift(highBits[0], 12)
                            | CommonUtils.unsignedLeftShift(highBits[1], 14)
                            | CommonUtils.unsignedLeftShift(highBits[2], 16)
                            | CommonUtils.unsignedLeftShift(highBits[3], 18));
                } else {
                    nestData32 &= 0xfff00000;
                    nestData32 |= (CommonUtils.unsignedLeftShift(codeword, 0)
                            | CommonUtils.unsignedLeftShift(highBits[0], 8)
                            | CommonUtils.unsignedLeftShift(highBits[1], 10)
                            | CommonUtils.unsignedLeftShift(highBits[2], 12)
                            | CommonUtils.unsignedLeftShift(highBits[3], 14));
                }
                nests[pos] = CommonUtils.unsignedIntToByte(nestData32);
                nests[pos + 1] = CommonUtils.unsignedIntToByte(nestData32 >>> 8);
                nests[pos + 2] = CommonUtils.unsignedIntToByte(nestData32 >>> 16);
                nests[pos + 3] = CommonUtils.unsignedIntToByte(nestData32 >>> 24);
                break;
            case 24:
                //3dir
                nestData32 = CommonUtils.unsignedLeftShift(nests[pos], 0)//需要去除符号位
                        | CommonUtils.unsignedLeftShift(nests[pos + 1], 8)
                        | CommonUtils.unsignedLeftShift(nests[pos + 2], 16)
                        | CommonUtils.unsignedLeftShift(nests[pos + 3], 24);
                nestData32 &= 0xff000000;
                nestData32 |= CommonUtils.unsignedLongToInt(CommonUtils.unsignedLeftShift(codeword, 0)
                        | CommonUtils.unsignedLeftShift(highBits[0], 8)
                        | CommonUtils.unsignedLeftShift(highBits[1], 11)
                        | CommonUtils.unsignedLeftShift(highBits[2], 14)
                        | CommonUtils.unsignedLeftShift(highBits[3], 17));
                nests[pos] = CommonUtils.unsignedIntToByte(nestData32);
                nests[pos + 1] = CommonUtils.unsignedIntToByte(nestData32 >>> 8);
                nests[pos + 2] = CommonUtils.unsignedIntToByte(nestData32 >>> 16);
                nests[pos + 3] = CommonUtils.unsignedIntToByte(nestData32 >>> 24);
                break;
            case 32:
                //5dir
                nestData32 = CommonUtils.unsignedLongToInt(CommonUtils.unsignedLeftShift(codeword, 0)
                        | CommonUtils.unsignedLeftShift(highBits[0], 8)
                        | CommonUtils.unsignedLeftShift(highBits[1], 13)
                        | CommonUtils.unsignedLeftShift(highBits[2], 18)
                        | CommonUtils.unsignedLeftShift(highBits[3], 23));
                nests[pos] = CommonUtils.unsignedIntToByte(nestData32);
                nests[pos + 1] = CommonUtils.unsignedIntToByte(nestData32 >>> 8);
                nests[pos + 2] = CommonUtils.unsignedIntToByte(nestData32 >>> 16);
                nests[pos + 3] = CommonUtils.unsignedIntToByte(nestData32 >>> 24);
                break;
            case 64:
                //13dir
                nestData64 = CommonUtils.unsignedLeftShift(codeword, 0)
                        | CommonUtils.unsignedLeftShift(highBits[0], 8)
                        | CommonUtils.unsignedLeftShift(highBits[1], 21)
                        | CommonUtils.unsignedLeftShift(highBits[2], 34)
                        | CommonUtils.unsignedLeftShift(highBits[3], 47);
                nests[pos] = CommonUtils.unsignedLongToByte(nestData64);
                nests[pos + 1] = CommonUtils.unsignedLongToByte(nestData64 >>> 8);
                nests[pos + 2] = CommonUtils.unsignedLongToByte(nestData64 >>> 16);
                nests[pos + 3] = CommonUtils.unsignedLongToByte(nestData64 >>> 24);
                nests[pos + 4] = CommonUtils.unsignedLongToByte(nestData64 >>> 32);
                nests[pos + 5] = CommonUtils.unsignedLongToByte(nestData64 >>> 40);
                nests[pos + 6] = CommonUtils.unsignedLongToByte(nestData64 >>> 48);
                nests[pos + 7] = CommonUtils.unsignedLongToByte(nestData64 >>> 56);
                break;
            default:
                writeInNests(idx, pos, highBits, codeword);
        }
    }

    private void writeInNests(int idx, int pos, int[] highBits, short codeword) {
        int rShift = (idx * this.kBitsPerNest) & (bitsPerByte - 1);//0|4
        int lShift = (rShift + this.kBitsPerNest) & (bitsPerByte - 1);//0|4
        int kBytes = (rShift + this.kBitsPerNest + 7) >>> 3;
        int end = kBytes - 1;

        byte rMask = (byte) (0xff >>> (8 - rShift));
        byte lMask = CommonUtils.unsignedIntToByte(CommonUtils.unsignedLeftShift((byte)0xff, lShift));
        if (lShift == 0) {
            lMask = 0;
        }
        long u1;
        long u2 = 0L;
        u1 = nests[pos] & rMask;
        if (kBytes <= bytesPerUint64) {
            u1 |= CommonUtils.unsignedLeftShift(nests[pos + end] & lMask, end * bitsPerByte);
        } else {
            u2 = CommonUtils.unsignedLeftShift(nests[pos + end] & lMask, (end - bytesPerUint64) * bitsPerByte);
        }
        u1 |= CommonUtils.unsignedLeftShift(codeword, rShift);

        for (int k = 0; k < birdsPNest; k++) {
            //shift[-56,28],特殊情况shift=0
            int shift = codewordSize + this.kDirBitsPerBird * k - cBirdSize - 64 + rShift;
            if (( codewordSize + this.kDirBitsPerBird * (k + 1) + rShift ) <= 64) {
                u1 |= CommonUtils.unsignedLeftShift(highBits[k], (codewordSize + this.kDirBitsPerBird * k - cBirdSize), rShift);
            } else if (( codewordSize + this.kDirBitsPerBird * k + rShift ) >= 64) {
                if (shift < 0) {//u2末尾已经存在若干bit数据了，具体原因：减去指纹位导致shift为负数
                    u2 |= highBits[k] >>> -shift;
                } else {
                    u2 |= CommonUtils.unsignedLeftShift(highBits[k], shift);
                }
            } else {
                u1 |= CommonUtils.unsignedLeftShift(highBits[k], (codewordSize + this.kDirBitsPerBird * k - cBirdSize), rShift);
                u2 |= highBits[k] >>> -shift;
            }
        }

        for (int k = 0; k < kBytes; k++) {
            if (k < bytesPerUint64) {
                nests[pos + k] = CommonUtils.unsignedLongToByte(u1 >>> (bitsPerByte * k));
            } else {
                nests[pos + k] = CommonUtils.unsignedLongToByte(u2 >>> (bitsPerByte * (k - bytesPerUint64)));
            }
        }
    }

    /**
     *
     * @param idx 索引
     * @param bird 待放入的鸟
     * @param kickOut 是否踢出
     * @return 被踢出的鸟
     */
    protected int insertBird(int idx, int bird, boolean kickOut) {
        int kickedBird = -1;
        int[] nest = new int[birdsPNest];
        readBird(idx, nest);
        for (int i = 0; i < nest.length; i++) {
            if (nest[i] == 0) {
                nest[i] = bird;
                writeBird(idx, nest);
                return 0;
            }
        }

        if (kickOut) {
            int nextInt = new Random().nextInt(birdsPNest);
            kickedBird = nest[nextInt];
            nest[nextInt] = bird;
            writeBird(idx, nest);
        }

        return kickedBird;
    }

    protected boolean containBird(int idx1, int idx2, int bird) {
        int[] nest = new int[birdsPNest];
        readBird(idx1, nest);
        for (int i = 0; i < nest.length; i++) {
            if (nest[i] == bird) {
                return true;
            }
            nest[i] = 0;
        }

        readBird(idx2, nest);
        for (int value : nest) {
            if (value == bird) {
                return true;
            }
        }

        return false;
    }

    protected boolean removeBird(int idx, int bird) {
        int[] nest = new int[birdsPNest];
        readBird(idx, nest);
        for (int i = 0; i < nest.length; i++) {
            if (nest[i] == bird) {
                nest[i] = 0;
                writeBird(idx, nest);
                return true;
            }
        }

        return false;
    }

    // Reset reset table
    protected void reset() {
        Arrays.fill(this.nests, (byte) 0);
    }

    protected int nestsNum() {
        return this.nestNum;
    }

    protected int birdsNum() {
        return this.nestNum * birdsPNest;
    }

    protected int getBitsPerBird() {
        return this.bitsPerBird;
    }

    private static final int TREE_META_DATA_SIZE = 1 + bytesPerUint32;

    //将元数据和数据一起打包返回
    protected byte[] read() {
        //元数据组成：treeType(2b)+bitsPerBird(6b)+nestNum(32b)
        byte[] result = new byte[TREE_META_DATA_SIZE + this.len];
        System.arraycopy(this.nests, 0, result, TREE_META_DATA_SIZE, this.len);
        result[0] = (byte) (1 + (getBitsPerBird() << 2));
        result[1] = (byte) nestsNum();
        result[2] = (byte) (nestsNum() >>> 8);
        result[3] = (byte) (nestsNum() >>> 16);
        result[4] = (byte) (nestsNum() >>> 24);
        return result;
    }

    //解析数据并写入当前对象
    protected Tree decode(byte[] data) {
        int nestNum = data[1]
                | CommonUtils.unsignedLeftShift(data[2], 8)
                | CommonUtils.unsignedLeftShift(data[3], 16)
                | CommonUtils.unsignedLeftShift(data[4], 24);
        byte[] init = new byte[data.length - TREE_META_DATA_SIZE];
        System.arraycopy(data, TREE_META_DATA_SIZE, init, 0, data.length - TREE_META_DATA_SIZE);
        return init(birdsPNest, data[0] >>> 2, nestNum, init);
    }

    // Info return tree's info
    protected String info() {
        return String.format("Tree with bird size: %s bits \n"+
                        "\t\t4 packed bits(3 bits after compression) and %s direct bits\n"+
                        "\t\tAssociativity: 4 \n"+
                        "\t\tTotal # of rows: %s\n"+
                        "\t\tTotal # slots: %s\n",
                this.bitsPerBird, this.kDirBitsPerBird, this.nestNum, this.birdsNum());
    }

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            System.out.println(new Random().nextInt(4));
        }
    }

}
