### 数据库系统架构论文阅读笔记

我看的是中文版，英文原版一百多页有毒啊，不过一些翻译不好的地方以及专有名词还是建议看原版

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







#### 准入控制







