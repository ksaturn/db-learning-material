### Lec-02 In-memory Databases

[video](https://www.bilibili.com/video/BV1Y7411o7GN?p=2)

### Disk-oriented DBMS(面向磁盘的数据库)

#### Buffer Pool Issue

- 数据主要是在非易失性存储中(HDD,SSD),以Page or Frame为单位做存储
- 使用在内存中的缓存池(Buffer Pool)，缓存来自磁盘的page
- 完整查询过程：

1.从数据库索引中查找对应记录的Page Id和slot (如果索引的节点没有加载到内存中，需要从磁盘中加载)

2.去页表中查询该Page是否已经加载到内存

3.如果没有的话，从磁盘中找到该页，并且复制到缓存池的一帧中(frame)

4.如果缓存池没有空的frame，则按一定替换原则evict某页(LRU,FIFO,CLOCK)，并且还要更新page table

5.如果page是dirty的即被修改过，就需要把对应修改后的内容写回磁盘中

- 这种架构的坏处：

Every tuple access has to go through the buffer pool manager regardless of whether that data will
always be in memory.

无论查询元组是否在内存中都需要去buffer pool中查找

#### Concurrency Control Issue

面向磁盘的数据库假定在尝试访问没有加载到内存的数据的时候，事务会“stall”

当然系统为了提高性能会允许一个事务stall的时候同时执行其他事务，靠上锁来实现ACID。

锁存储在in-memory的一个hash-table，叫lock manager,避免锁数据被swap到磁盘上

**All the info about lock is in memory!**

#### Logging & Recovery Issue

- 事务提交前，把修改写入Write-ahead-log(WAL)，WAL包含undo log 和 redo log
- 每一个log entry包含事务修改前后数据的镜像
- The DBMS flushes WAL pages to disk separately from corresponding modified database pages, so it takes extra
  work to keep track of what log record is responsible for what page

在事务提交之前，WAL需要先flush到磁盘中

需要维护log record是负责哪一个page的信息（利用LSM log sequence number）

#### 开销比较

如果不考虑disk flushing,面向磁盘数据库开销主要花费在：

BUFFER POOL 34%

LATCHING 14% (隔离的是线程,保证并发线程操作临界资源的正确性)

LOCKING 16% (隔离的是事务，一般锁住的是数据库的表，行)

LOGGING 12% 

B-TREE KEYS 16% 索引查找时间

CPU 7% 

### In-memory DBMS

背景：DRAM的发展，价格和容量足以把整个数据库的数据都存储到内存中

这时候磁盘IO不再是数据库性能的瓶颈,而需要考虑以下可以优化的性能瓶颈

- Locking/Latching
- Cache-line misses
- Pointer chasing
- Predicate evaluation
- Data movement and copying
- Networking 

因为所有数据都在memory，因此不会存在脏页，也不需要维护undo log信息，也不需要LSN机制



下面聊一下与面向磁盘数据库的一些不同

- Data Organization

1.从Index中查找数据指针所在的Block Id与Offset

2.根据block id与offset找到数据指针的内存地址,指针存储在Fixed-Length Data Blocks

3.根据这个64-bits指针去Variable-Length Data Blocks去寻找真正的数据

![1587904937780](C:\Users\AlexanderChiu\AppData\Roaming\Typora\typora-user-images\1587904937780.png)



- Indexes

In-memory DBMS并不会记录索引的更新，再重启的时候，当所有数据加载到内存之后直接在内存中重建这个索引。

- Query Processing

由于数据都在内存中，随机访问的速度并不比顺序访问要差

- Logging & Revocery

1.仍然需要维护WAL，即修改数据库之前,把修改记录写入WAL然后同步到内存中

2.使用组提交来提交WAL，分摊fsync系统调用的开销

3.也可以使用更轻量级的logging策略，只存储redo信息

- Concurrency control——提供原子性与隔离性

在面向磁盘的数据库中，锁是存放在另外一个内存中的hash表，与数据记录本身是分开来存储的。因为记录是可能被swap到外存。

而在IMDB中，由于数据记录一直在内存中，不会swap到外存，因此可以把记录有关的锁的信息与记录一起保存。

所以尝试取锁的效率=尝试获得数据的效率

瓶颈在于：多个事务同时尝试访问某个数据，只有一个事务能抢到锁

如果用mutex实现的话性能太慢，建议用CAS来保证这里的同步

#### CAS

```
__sync_bool_compare_and_swap(&M, 20, 30);
```

当且仅当M地址代表的值等于20的时候，把M地址所在的值设为30.

否则失败，因为不等于的话说明这个值肯定被别的线程修改了



接下来的会详细讨论并发控制的一些策略：

- 悲观策略——2 PHASE LOCKING

操作数据之前一定先抢锁。

2PL解决死锁两种策略：

1.死锁探测

维护一个队列，队列存放着拿着锁的事务，一个后台线程周期性扫这个队列的事务，看哪些事务

在运行，哪些stall，这就可以找到发生死锁的事务。

2.死锁预防

在分配锁之前看有没有其他事务已经拿到这把锁，如果这个事务拿不了锁

1）等待

2）自杀

3）杀死另外一个拿锁的事务

- 乐观策略——时间戳机制

操作数据不抢锁，事务提交的时候比较时间戳

#### Basic T/O Protocol

每一个事务都会分配一个时间戳，每一个记录的头部维护上一次事务操作的时间戳。然后事务对时间戳进行操作的时候，比较现在事务的时间戳与记录头部当前的时间戳。

#### 乐观并发控制

```
1. Read Phase: Transaction’s copy tuples accessed to private work space to ensure repeatable reads, and
keep track of read/write sets.

2. Validation Phase: When the transaction invokes COMMIT, the DBMS checks if it conflicts with other
transactions. Parallel validation means that each transaction must check the read/write set of other
transactions that are trying to validate at the same time. Each transaction has to acquire locks for its
write set records in some global order. Original OCC uses serial validation.
The DBMS can proceed with the validation in two directions:
? Backward Validation: Check whether the committing transaction intersects its read/write sets
with those of any transactions that have already committed.
? Forward Validation: Check whether the committing transaction intersects its read/write sets with
any active transactions that have not yet committed.

3. Write Phase: The DBMS propagates the changes in the transactions write set to the database and
makes them visible to other transactions’ items. As each record is updated, the transaction releases
the lock acquired during the Validation Phase
```

具体可以参考slides的过程

#### 时间戳的分配策略

- 基于互斥量：并发性能低
- 原子加操作:   CAS操作去维护一个全局计数器
- 批量原子加操作
- 硬件CLOCK:  Intel CPU only
- 硬件计数器: 尚未有硬件实现



#### 并发控制的性能瓶颈

- Lock Thrashing

锁抖动

Each transaction waits longer to acquire locks, causing other transaction to wait longer to acquire
locks.

Can measure this phenomenon by removing deadlock detection/prevention overhead. 

solution： force txns to acquire locks in primary key order

- Memory Allocation

内存分配

Copying data on every read/write access slows down the DBMS because of contention on the memory
controller.

Default libc malloc is slow. Never use it 

- Timestamp Allocation

时间戳分配

