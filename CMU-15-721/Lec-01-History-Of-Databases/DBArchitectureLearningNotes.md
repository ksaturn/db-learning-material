### 数据库系统架构论文阅读笔记

我看的是中文版，英文原版一百多页有毒啊，不过一些翻译不好的地方以及专有名词还是建议看原版

我是边看[论文导读](<https://www.bilibili.com/video/av74618606>)边做笔记的，感谢PingCAP哈哈

### 导论

![1588079636901](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588079636901.png)



关系型数据库五个部分

* Client Communication Manager

负责管理调用者与数据库服务器之间的连接

* Process Manager

在收到客户端第一个请求之后，DBMS会为之分配一个计算线程。主要工作是确保线程数据以及控制输出是通过通信管理器与客户端连接的。

* Relational Query Processor

1.检查用户是否有权进行该查询

2.把用户sql查询语句编译为中间查询计划

3.编译完成后结果查询计划会交给查询执行器

4.查询执行器会包含一系列处理查询的操作，典型处理查询任务包括:连接，选择，投影，聚集，排序等

* transactional storage manager

存储管理器：负责所有的数据接口和操作调用

存储系统主要是包括了管理**磁盘的基本算法和数据结构**，比如基本的表和索引

它还会包括一个buffer manager，用来控制内存缓冲区和磁盘的数据传输

还有就是lock manager,保证ACID

log manager用于实现undolog(撤销操作的完整性,实现原子性关键)和redolog(事务操作的持久性实现)

* 查询结束后，把数据库的数据组织成结果元组，放入client communication manager的buffer中，由其负责发送给调用者。
* 而上图的右侧的组件会独立于任何查询，使得数据库保持稳定性和整体性。例如CatalogManager，在数据的传输，分解与查询优化过程中会用到目录。MemoryManager也广泛应用于整个DBMS运行中动态分配和释放内存的场合。

我们以这幅图来简单总结下Query执行的生命周期

![1588085893049](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588085893049.png)



接下来对比下TiDB的SQL层的执行流程

1.Client发送调用，具体而言是一个网络包

2.协议层解析这个网络包，拆包，把SQL解析出来

3.SQL经过词法语法语义分析转换成AST抽象语法树

4.语法树传入Logical Optimizer生成逻辑计划

5.逻辑计划经过Physical Optimizer生成物理计划

6.物理计划分发给本地或者分布式的执行器

7.执行器负责远程从TIKV调数据

![1588089621125](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588089621125.png)



### 进程模型

* 每个DBMS worker拥有一个进程

* 每个DBMS worker拥有一个线程

* 进程池

#### 共享数据和进程空间

两种主要的缓冲区：

* 磁盘IO缓冲区

主要考虑不同两种IO中断:数据库请求与日志请求

1. 数据库IO中断请求：缓冲池

当一个线程需要从数据库读取一个数据页的时候，会产生一个IO请求并且在缓冲池中分配内存空间来存放从磁盘读取的数据。

当要把缓冲区的页存入磁盘时候，线程也会产生一个IO请求把缓冲池中的页存入磁盘中的目的地址中。

   2.日志IO请求: 日志尾部

每一个事务都会产生一条日志entry，它们会暂时存储在内存队列中。然后会有一个独立的进程或者线程负责将其以FIFO顺序刷新到磁盘中。

通常采用的日志刷新方法是WAL，即一个事务在日志记录刷新到日志存储器之前不能被成功提交。就是先写日志再提交事务，而日志刷新可能会被推迟一段时间以实现一个IO请求批量提交记录。



* 客户端通信缓冲区

SQL通常被用于"pull"拉模型，即客户端可以通过重复发送SQL FETCH请求不断获取结果元组，而DBMS会尽可能再FETCH流到来之前做数据pre-fetch工作。

客户端可以用游标机制，在客户端存储近期即将被访问的结果，而不再依赖**操作系统通信缓冲区**做数据的pre-fetch



* 锁表

Lock Manager模块负责管理，锁表会由所有的DBMS worker共享



#### 准入控制（Admission Control）

首先我们来讨论一般什么情况下会发生"抖动"呢？

* 可能是由内存问题造成的

可能缓冲池满了，放不下磁盘调来的的页，需要频繁在缓冲池和磁盘中调页淘汰页

可能是一些内存消耗比较大的操作如排序，哈希连接等导致

* 可能是因为锁竞争导致的

可能发生死锁，事务需要回滚以及重启

因此一个多用户的系统通常都要设计**准入控制**模块，实现满载情况下的"graceful degradation": 具体指的是事务的延迟会随着事务到达率增加，但是吞吐量会一直维持在峰值。

那具体如何实现呢？可以从两个层面实现：

1.**在用户请求到达的时候进行准入控制**可以通过确保客户端链接数处在一个临界值中，避免对**网络链接数，本质是socket数量**的过度消耗，一般可以在应用层，事务处理层和网络服务层实现

2.在**DBMS内核的Query Processor模块**实现，即在**执行查询计划的时候进行准入控制**

Query Processor在把query进行转换和优化生成执行计划之后,会继续决定是否推迟执行一个查询或者是否使用更少的资源来执行查询或者是否需要额外的限制条件来执行查询。

具体依赖的是**Query Optimizer**提供的信息（查询所需资源）以及系统当前的资源

具体来说Query Optimizer可以提供的信息有：

1.确定要查询的磁盘设备以及顺序和随机磁盘IO次数的估计

2.根据计划的算子估计CPU负载与所要查询的元组个数

3.评估查询数据结构的内存使用情况，包括连接，排序，哈希连接等操作消耗的内存

其中第三点最为重要，因为我们也说了“抖动thrashing”的主要原因还是因为内存压力，准入控制主要还是为了解决系统的内存压力问题的。



### 并行架构：进程和内存的协调

本部分侧重讨论进程模型与**Memory Coordination**问题

#### Shared-Memory



![1588165410259](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588165410259.png)

所有处理器共享相同的内存地址空间

* UMA（均衡访问模型）

![1588165586729](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588165586729.png)

* NUMA（非均衡访问模型）

![1588165698955](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588165698955.png)

避免了对内存共享系统总线的占用

主要做法

* 多个CPU与部分内存构成一个Node，Node内部的CPU通过IMC Bus进行通信
* 不同Node之间通过QPI通信
* QPI的延迟远比IMC要大，因此访问远程内存的时间远比访问近程内存要慢

TiDB这种架构不适合在NUMA上跑，原因是假如是Node1接受客户端请求，但是数据在Node0上，会导致请求走QPI总线，导致延迟过高。



解决方法: 提前绑核，把所有CPU绑定在同一个Node



#### Shared-Disk

![1588169398747](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588169398747.png)

依赖分布式锁：不同处理器通过内部网络相互通信，多个处理器同时访问磁盘必定会带来并发安全问题，因此需要引入分布式锁管理磁盘的读取和写入。

高效缓存一致性协议: 每个CPU与内存不是直接打交道的，是通过多级缓存来缓冲CPU和内存处理速度能力的差异。CPU处理速度远远高于内存处理速度。每个CPU绑定的缓存不一样，可能缓存写入内存的时候会出现不一致情况。

通常有两种解决方法:

1.总线锁，某个核心独占总线，其他CPU不能通过总线与内存通信，这样做效率无疑会很低

2.MESI缓存一致性协议

若干个CPU核心通过ringbus连到一起。每个核心都维护自己的Cache的状态。如果对于同一份内存数据在多个核里都有cache，则状态都为S（shared）。一旦有一核心改了这个数据（状态变成了M），其他核心就能瞬间通过ringbus感知到这个修改，从而把自己的cache状态变成I（Invalid），并且从标记为M的cache中读过来。同时，这个数据会被原子的写回到主存。最终，cache的状态又会变为S



#### Shared-Nothing

分布式架构

![1588174351805](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588174351805.png)



1.分区可以怎么分？hash,partition,round-robin等

2.分布式架构通常会出现网络分区问题，分布式事务的解决方案也有一个演变过程

2PC -> 3PC -> Percolator(Google提出，可以了解下)

分布式事务是日后需要深入了解的领域

3.避免单个节点成为热点，这会导致该节点成为系统的瓶颈，同时要把热点节点的数据打散到别的节点上去



### 关系查询处理器

![1588175184389](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588175184389.png)

这部分实践可以以**Apache Calcite**来作为学习。

#### 查询处理与鉴权

Parser主要任务：

1.检查这个查询语法，是否被正确定义

2.解决名字与引用

 	a. 把表名规范化为<数据库，模式，表名>，并且检查表是否被注册

3.把查询转换为优化器使用的内部形式

4.核实这个用户是否被授予权限执行该查询



#### 查询重写

Logical Optimizer 负责简化和标准化查询，无需改变查询语义

* 视图展开
* 简化常量运算表达式

eg: a > 1 + 1 => a > 2

* 谓词逻辑重写

eg:  R.x < 10 and R.x = S.y => R.x < 10 AND S.y < 10 AND R.x = S.y

左部分要全表扫描y

而有部分如果在y上建立了索引，可以利用索引来找y

* 语义的优化

如冗余连接的消除



#### Query Optimizer

也就是**Physics Optimizer**,把内部查询表达转换为一个高效的查询计划，指导数据库如何去取表，如何排序，如何join。

* 计划空间
* 选择代价估计(Cost Model)
* 搜索算法
* 并行
* 自动调优

比较详细的请看原文。



![1588177540944](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1588177540944.png)



