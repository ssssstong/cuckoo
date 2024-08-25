package cuckoo;

import cuckoo.exception.CuckooException;
import cuckoo.util.CommonUtils;
import cuckoo.util.HashUtils;

public class CuckooFilter implements Cloneable {

    private int count;
    private Tree tree;
    //作用：缓存最近被踢掉的鸟，方便查询，缺点：该处只能记录最近一个被踢出的，无法查询短时间内有多个被踢出的鸟的情况
    private Victim victim;

    private static final int MAX_KICK_OUT_COUNT = 500;

    class Victim {
        int index;
        int bird;
        boolean used;
    }

    private CuckooFilter() {

    }

    public CuckooFilter(int bitsPerBird, int maxNestNum) {
        if (bitsPerBird > 32) {
            throw new CuckooException("bitsPerBird max 32bit");
        }
        int sizePerNest = 4;
        int nestNum = CommonUtils.getNextPow2(maxNestNum / sizePerNest);
        if (((float)maxNestNum / (float)(nestNum * sizePerNest)) > CommonUtils.maxLoadFactor(sizePerNest)) {
            nestNum <<= 1;
        }
        if (nestNum == 0) {
            nestNum = 1;
        }
        this.tree = new Tree();
        this.tree.init(sizePerNest, bitsPerBird, nestNum, null);
        this.count = 0;
        this.victim = new Victim();
        this.victim.used = false;
        this.victim.index = -1;
        this.victim.bird = -1;
    }

    private int indexHash(int hv) {
        // table.NumBuckets is always a power of two, so modulo can be replaced with bitwise-and:
        return hv & (this.tree.nestsNum() - 1);
    }

    private int birdHash(int hv) {
        return CommonUtils.unsignedLongToInt((hv & 0xffffffffL) % (CommonUtils.unsignedLeftShift(1, this.tree.getBitsPerBird()) - 1) + 1);
    }

    public void add(byte[] data) {
//        if (this.victim.used) {//有被踢出的bird就不能添加？
//            return;
//        }
        int[] idxAndBird = generateIndexAndBird(data);
        int idx = idxAndBird[0];
        int bird = idxAndBird[1];
        doAdd(idx, bird);
    }

    private void doAdd(int idx, int bird) {
        int curIdx = idx;
        int curBird = bird;
        int kickedBird;
        boolean kickOut;

        for (int count = 0; count < MAX_KICK_OUT_COUNT; count++) {
            kickOut = count > 0;
            kickedBird = this.tree.insertBird(curIdx, curBird, kickOut);
            if (kickedBird == 0) {//insert success
                this.count++;
                return;
            } else if (kickOut) {//kick out a bird
                curBird = kickedBird;
            }
            curIdx = altIndex(curIdx, curBird);
        }
        this.victim.index = curIdx;
        this.victim.bird = curBird;
        this.victim.used = true;
    }

    public boolean contain(byte[] data) {
        int[] idxAndBird = generateIndexAndBird(data);
        int idx = idxAndBird[0];
        int bird = idxAndBird[1];
        int idx2 = altIndex(idx, bird);
        boolean hit = victim.index == idx && bird == victim.bird;
        return hit || tree.containBird(idx, idx2, bird);
    }

    public void del(byte[] data) throws CuckooException {
        int[] idxAndBird = generateIndexAndBird(data);
        int idx = idxAndBird[0];
        int bird = idxAndBird[1];
        doDel(idx, bird);
    }

    private void doDel(int idx, int bird) throws CuckooException {
        //删除成功，将被踢出的bird重新添加
        int idx2 = altIndex(idx, bird);
        if (this.tree.removeBird(idx, bird) || this.tree.removeBird(idx2, bird)) {
            if (this.victim.used) {
                doAdd(this.victim.index, this.victim.bird);
                this.victim.index = 0;
                this.victim.bird = 0;
                this.victim.used = false;
            }
            this.count--;
            return;
        } else if (this.victim.used && this.victim.bird == bird && (this.victim.index == idx || this.victim.index == idx2)) {
            this.victim.index = 0;
            this.victim.bird = 0;
            this.victim.used = false;
            return;
        }
        throw new CuckooException("del failed,idx=" + idx + ",bird=" + bird);
    }

    public void reset() {
        this.tree.reset();
        this.count = 0;
        this.victim.index = 0;
        this.victim.bird = 0;
        this.victim.used = false;
    }

    // FalsePositiveRate return the False Positive Rate of filter
    // Notice that this will reset filter
    public float falsePositiveRate() {
        byte[] n1 = new byte[4];
        this.reset();
        int n = this.tree.birdsNum();
        for (int i = 0; i < n; i++) {
            n1[0] = (byte) (i & 0x000000ff);
            n1[1] = (byte) ((i >> 8) & 0x000000ff);
            n1[2] = (byte) ((i >> 16) & 0x000000ff);
            n1[3] = (byte) ((i >> 24) & 0x000000ff);
            this.add(n1);
        }
        int rounds = 100000;
        int fp = 0;
        for (int i = 0; i < rounds; i++) {
            n1[0] = (byte) ((i + n + 1) & 0x000000ff);
            n1[1] = (byte) (((i + n + 1) >> 8) & 0x000000ff);
            n1[2] = (byte) (((i + n + 1) >> 16) & 0x000000ff);
            n1[3] = (byte) (((i + n + 1) >> 24) & 0x000000ff);
            if (this.contain(n1)) {
                fp++;
            }
        }
        this.reset();
        return (float)fp / (float)rounds;
    }

    public int size() {
        return this.count;
    }

    @Override
    public CuckooFilter clone() throws CloneNotSupportedException {
        CuckooFilter copyFilter = (CuckooFilter) super.clone();
        if (copyFilter != null) {
            byte[] data = this.tree.read();
            copyFilter.tree = this.tree.decode(data);
            copyFilter.count = this.count;
            Victim victim = new Victim();
            victim.used = this.victim.used;
            victim.index = this.victim.index;
            victim.bird = this.victim.bird;
            copyFilter.victim = victim;
        }

        return copyFilter;
    }

    private int[] generateIndexAndBird(byte[] data) {
        long hash = HashUtils.hash64ToLong(data, 1996);
        int idx = indexHash(CommonUtils.unsignedLongToInt(hash));
        int bird = birdHash(CommonUtils.unsignedLongToInt(hash >>> 32));
        return new int[]{idx, bird};
    }

    private int altIndex(int index, int bird) {
        // 0x5bd1e995 is the hash constant from MurmurHash2
        return indexHash(index ^ (bird * 0x5bd1e995));
    }
}
