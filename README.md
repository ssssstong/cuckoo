# cuckoo

#### 介绍
布谷鸟过滤器
移植于 [efficient/cuckoofilter](https://github.com/efficient/cuckoofilter)和[linvon/cuckoo-filter](https://github.com/linvon/cuckoo-filter) 
#### 概述
布谷鸟过滤器是一种在近似集合隶属查询时替代布隆过滤器的数据结构。布隆过滤器是众所周知的一种用于查询类似于“x是否在集合中？”这类问题，且非常节省空间的数据结构，但不支持删除。其支持删除的相关变种（如计数布隆过滤器）通常需要更多的空间。

布谷鸟过滤器可以灵活地动态添加和删除项。布谷鸟过滤器是基于布谷鸟哈希的（这也是为什么称为布谷鸟过滤器）。 它本质上是一个存储每个键的指纹的布谷鸟哈希表。布谷鸟哈希表可以非常紧凑，因此对于需要更低假阳性率（<3%）的应用程序，布谷鸟过滤器可以比传统的布隆过滤器节省更多空间。

有关算法和引用的详细信息，请参阅：

["Cuckoo Filter: Practically Better Than Bloom"](http://www.cs.cmu.edu/~binfan/papers/conext14_cuckoofilter.pdf) in proceedings of ACM CoNEXT 2014 by Bin Fan, Dave Andersen and Michael Kaminsky


#### 核心要点

1.  指纹(鸟)
2.  4路桶(巢)
3.  半排序桶（重复组合k:每路的指纹组合数，r:路数）

####为什么指纹数是4个，不选择2或8？
选2则节省空间不明显；
选8则静态空间开销会很大，成指数级递增（2^32）

####为什么只转换指纹（鸟）的4bit？

假设f=4（用于转换的指纹bit数）；
M1=n(n+1)(n+2)(n+3)/24=(n^4+6n^3+11n^2+6n)/24,n=2^f；
M2=2^x,x是最终优化的空间大小

由于指纹数为4，并且考虑到编码的通用性，因此排列算法压缩的空间需要平均到每个指纹上（每个指纹压缩同等的bit数）；
假设可以优化到8bit（每个指纹压缩2bit），则x<=4f-8，且M2>=M1
但是M2=2^(4f-8)<2^(4f-5)<M1，假设不成立，因此优化空间的bit数x<4f-8，最终压缩的空间bit数小于8bit；
又因为M2=2^(4f-4)>M1，所以最终选择压缩4bit


#### 使用说明
~~~
//create filter
CuckooFilter cuckooFilter = new CuckooFilter(j, 5000);
byte[] data = "hello world".getBytes();
//add data
cuckooFilter.add(data);
System.out.println("add count:" + cuckooFilter.size());
//data is contained
System.out.println("data is contained:" + cuckooFilter.contain(data));
//del data
try {
    if (cuckooFilter.contain(data)) {
       cuckooFilter.del(data);
    }
} catch (CuckooException e) {
    e.printStackTrace();
}
System.out.println("after del, count:" + cuckooFilter.size());
~~~
