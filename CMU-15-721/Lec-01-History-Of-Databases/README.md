
### Study Notes on Course1

[Video-bilibili](https://www.bilibili.com/video/BV1Wz411b7sD?from=search&seid=1785395184520069316)

#### Course Topics

Focus on internals of single nodes in-memory database system.

Not distributed Systems.


### 论文阅读笔记

《What's really new with NewSQL》

##### NoSQL

* 传统关系模型与强事务保证制约了其性能和可扩展性，高可用性

* NoSQL的的设计思想是一方面抛弃强事务保证，引入柔情事务，追求的是最终一致性。
另一方面，抛弃传统关系模型，采用如键值对，图或者文档数据模型来组织数据。

* 如Google的BigTable,以及其衍生出来的Hbase,Cassandra，文档型数据库MongoDB,
键值型数据库Redis

##### NewSQL

* NoSQL的缺点：开发者需要花大量精力写code来处理数据不一致的情况,在生产中发现
还是保证事务性会比较好

* NewSQL的定义是：

1.保持关系模式，但是对OLTP提供如NoSQL相同的可扩展性能

2.保证事务的ACID特性

说白了就是既要保持传统关系模式，同时追求与NoSQL一样的性能与可扩展性，开发者再也不需要写额外的code来保证最终一致性。

那具体NewSQL DBMS需要满足什么特性？

1.读写事务是short-lived的

2.每次只处理subset数据,一般是先用索引找出来，即避免全表扫描或者大规模的分布式join

3.可能相同的query要执行多次，并且每次的input都不同

更为narrow的definition包括

提供无锁的并发控制策略

需要是shared nothing architecture


什么是**shared nothing architecture**？


每个节点都是独立的。典型的SNA系统会集中存储状态的信息，如：数据库中，内存cache中；不在节点上保存状态的信息。 
对于server集群，若将session等状态保存在各个节点上，那么各个节点的session复制会极大的影响性能；若采用SNA，保持每个节点的无状态性，不再使用session来保持全局的状态，而是将session直接放在数据库中，在数据库前再加一层分布式Cache（推荐使用memcached）
，这样将可极大的提高性能，当改变session中的对象时，同步到cache和数据库。

shared nothing需要确立一种分片策略，使得依据不同的分片策略，减少资源竞争。
三种基本的分片策略结构：

1) 功能分片
根据多个功能互相不重叠的特点进行分片，这种方式在ebay取得巨大成功。缺点是需要深入理解应用领域，才能更好地分片。

2) 键值分片
在数据中找到一个可以均匀分布到各个分片中的键值。

3) 查表
在集群中有一个节点充当目录角色，用于查询哪个节点拥有用户要访问的数据。缺点在于这个表可能成为整个系统的瓶颈及单点失效点。


然后我们详细讨论下NewSQL几个架构新特点

#### New Architecture

All of the DBMSs in this category are based
on distributed architectures that operate on shared-nothing resources and contain components to support multi-node concurrency control, fault tolerance through replication, flow control, and distributed query processing.

* 分布式SNA架构

* 提供分布式事务（多节点并发控制）

* 复制机制来保证容错

* 流量控制(flow control)

* 分布式SQL executor


This means that the DBMS
is responsible for distributing the database across its resources
with a custom engine instead of relying on an off-the-shelf distributed filesystem (e.g., HDFS) or storage fabric (e.g., Apache
Ignite). 

* newSQL 负责管理自己的storage，而不用以来外部存储(HDFS)

* DBMS负责“send the query to the data”,而不是"send the data to the query"

* 这么做的好处是可以大大减少网络传输量(传一个序列化后的query总比要传一个数据或者索引或者物化视图要强)

#### Transparent Sharding Middleware

NewSQL之前通常sharding是由中间件来完成，但是每个节点还是要使用传统的DBMS，

这些DBMS是基于**disk-oriented**的架构，因此它们不能够应用一些专门给**memory-oriented**

的storage manager或者并发控制策略。

另一方面，应用传统关系DBMS会使得扩展性和高可用性很差，因为要保持一致性，根据CAP理论的话就要放弃A

个人理解而NoSQL放弃了一致性C，因此可扩展性就会较强

而分布式SQL执行器是该类系统的特点，各个数据可以直接交换数据而不是使用中间件处理。

#### Database-as-a-service

* 使用者无需在云端VM配置数据库，也不需要在自己私有的硬件上maintan DBMS

* 服务提供方需要维护数据库的配置(如缓冲池的大小，复制与备份)

* 客户使用方只需要一个URL就可以连接DBMS，一个dashboard应用进行实时监控和API来操控数据库。


接下来我们再讨论下NewSQL DBMS的一些新特性

#### Main Memory Storage 基于内存的存储引擎

* 传统的DBMS都是disk-oriented即面向磁盘

特点是：

1.主要存储在以块为寻址基本单位的HDD或者SSD

2.使用内存来把从磁盘读取的块缓存起来,把事务的更新做buffer

* NewSQL的main memory storage architecture

基于内存的优化可以不用再考虑对数据的等待，以及不用存在buffer pool的管理和非常繁重的并发控制模式。

特点是:

1.数据都存储在内存中

内存不够怎么办？

2.支持把内存中的某些不再用数据淘汰到持久化层(例如硬盘)

The general approach is to use an internal tracking mechanism inside of the system to identify which tuples
are not being accessed anymore and then chose them for eviction

这个过程也叫"anti-caching"，其实就是把部分内存swap到磁盘，但是需要维护一个内存追踪系统

3.另一种策略是，如MemSQL采用列式存储，使用LSM存储来减少update的开销
？这里不太懂

#### Partitioning/Sharding 基于分区分片的水平可扩展性

NewSQL通常是通过分区和分片来实现其高可扩展性的。

一般水平分区分片的工作原理：

* The database’s tables are horizontally divided into multiple
  fragments whose boundaries are based on the values of one (or
  more) of the table’s columns 
  
* The DBMS assigns each tuple to a fragment based on the values of these attributes using either 
**range or hash partitioning**
  
* Related fragments from multiple tables are combined together
  to form a partition that is managed by a single node
  
* That node is responsible for executing any query that needs to access data
  stored in its partition
  
相关联的数据尽可能放在一个shard中，那么就可以尽量把query的执行下放到一个节点中,不需要
2PC来维护分布式事务的原子性

这里简单介绍下NuoDB和MemSQL的架构

* NuoDB

1.把一个或多个节点分为storage managers(SM),每一个SM
存储这一个分区

2.SM把数据库划分出一个个block,叫做atom

3.其他节点称为transaction engines(TE),是atom的内存镜像

4.提供load balance机制来保证数据固定分布在某些节点上

* MemSQL

聚集节点负责执行

叶子节点负责存储

* NewSQL的分区还支持在线迁移，包括数据迁移和rebalance


#### 并发控制

* 提供原子性和隔离性的保证

* 分为中心化和去中心化事务控制两种机制

* 中心化的事务控制

所有事务操作都需要通过中心协调器来进行

由协调器来决定事务是否执行

* 去中心化的事务控制

每个节点都会维护一个事务状态并且通过与其他节点通信来发现事务是否冲突








### ref

https://www.dazhuanlan.com/2019/10/16/5da61be779020/