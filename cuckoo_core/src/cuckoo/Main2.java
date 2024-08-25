package cuckoo;

/**
 * @ClassName Main
 * @Description
 * @Date 2023/7/12 20:23
 **/
public class Main2 {

    public static void main(String[] args) throws CloneNotSupportedException {
        for (int j = 5; j < 6; j++) {
            System.out.println("当前指纹数量：" + j);
            CuckooFilter cuckooFilter = new CuckooFilter(j, 5000);
            byte[] data = new byte[4];
            cuckooFilter.add("hello".getBytes());
            for (int i = 0; i < 10500; i++) {
                data[0] = (byte) (i & 0x000000ff);
                data[1] = (byte) ((i >> 8) & 0x000000ff);
                data[2] = (byte) ((i >> 16) & 0x000000ff);
                data[3] = (byte) ((i >> 24) & 0x000000ff);
                cuckooFilter.add(data);
            }
            System.out.println("add count:" + cuckooFilter.size());
            CuckooFilter copy = cuckooFilter.clone();
            System.out.println("copy add count:" + copy.size());
        }

    }

}
