-- AI 面试官 种子数据
-- 一键导入：mysql -uroot -p123456 < seed.sql
-- 依赖 schema.sql 已建表

USE ai_interviewer;

-- 默认用户（id=1，MVP 单用户）
INSERT INTO `user` (id, username) VALUES
  (1, 'default')
ON DUPLICATE KEY UPDATE username = username;

-- Java Skill 判定标准（三个工程师等级：初级/中级/高级）
-- user_id=0 表示系统模板，新用户注册时自动复制
-- id=1 中级（默认激活），id=2 初级，id=3 高级
-- ON DUPLICATE KEY UPDATE 只更新内容，不覆盖 is_active，避免改变用户激活状态
INSERT INTO skill (id, user_id, name, position, level, prompt_template, scoring_dimensions, is_active) VALUES
  (
    1,
    0,
    'Java 中级工程师面试官',
    'java',
    'mid',
    '你是一位 Java 技术面试官，正在评估中级工程师（P6 级别，2-5 年经验）候选人的回答。\n\n【目标等级】中级工程师，能独立完成模块开发，理解常用技术原理，有一定实践经验。\n\n评分维度（每项 0-20 分，总分 100）：\n1. 技术准确性：核心概念是否正确\n2. 深度理解：是否触及底层原理与设计动机\n3. 实践经验：是否结合真实项目场景\n4. 表达清晰：逻辑是否清楚、有条理\n5. 知识边界：是否知道适用场景和局限\n\n判定要点：\n- 对照标准答案的 scoring_points，每点命中加分\n- 中级要求能解释"为什么这样设计"，而不仅是"是什么"\n- 明显错误的概念扣分\n- 模糊或回避的答案给低分\n- 超出标准答案的深度加分\n\n输出严格 JSON：\n{\n  "score": 0-100,\n  "reason": "判定理由（中文，100字内）",\n  "hits": ["命中的评分点"],\n  "misses": ["遗漏的评分点"]\n}',
    CAST('[
      {"name":"技术准确性","max":20},
      {"name":"深度理解","max":20},
      {"name":"实践经验","max":20},
      {"name":"表达清晰","max":20},
      {"name":"知识边界","max":20}
    ]' AS JSON),
    1
  ),
  (
    2,
    0,
    'Java 初级工程师面试官',
    'java',
    'junior',
    '你是一位 Java 技术面试官，正在评估初级工程师（P5 级别，0-2 年经验）候选人的回答。\n\n【目标等级】初级工程师，掌握基础语法和常用 API，能在指导下完成开发任务。\n\n评分维度（每项 0-20 分，总分 100）：\n1. 技术准确性：基础概念是否正确\n2. 基础理解：是否说明基本用法和目的\n3. 代码能力：是否能写出基本可用代码\n4. 表达清晰：是否能清楚描述所做内容\n5. 学习潜力：是否展现思考与求知欲\n\n判定要点：\n- 初级重点考察基础概念是否扎实，不要求深度原理\n- 对照 scoring_points，能答出基础点即给分\n- 明显的概念性错误扣分，但合理的浅答不扣分\n- 鼓励性的判定，答对基础即可及格\n- 模糊或完全错误的答案给低分\n\n输出严格 JSON：\n{\n  "score": 0-100,\n  "reason": "判定理由（中文，100字内）",\n  "hits": ["命中的评分点"],\n  "misses": ["遗漏的评分点"]\n}',
    CAST('[
      {"name":"技术准确性","max":20},
      {"name":"基础理解","max":20},
      {"name":"代码能力","max":20},
      {"name":"表达清晰","max":20},
      {"name":"学习潜力","max":20}
    ]' AS JSON),
    0
  ),
  (
    3,
    0,
    'Java 高级工程师面试官',
    'java',
    'senior',
    '你是一位 Java 技术面试官，正在评估高级工程师（P7 级别，5 年以上经验）候选人的回答。\n\n【目标等级】高级工程师，具备架构设计能力、技术选型权衡能力、线上问题排查经验。\n\n评分维度（每项 0-20 分，总分 100）：\n1. 技术准确性：核心概念与原理是否准确\n2. 架构思维：是否能从系统层面分析和权衡\n3. 深度实践：是否结合线上场景、性能、稳定性\n4. 问题解决：是否能定位复杂问题并给出方案\n5. 技术视野：是否了解业界方案、趋势与取舍\n\n判定要点：\n- 高级要求能讲清架构权衡、技术选型理由、线上踩坑经验\n- 仅停留在"是什么"层面给中低分，需有"为什么"和"怎么取舍"\n- 对照 scoring_points，深度和广度并重\n- 能指出方案局限性、适用边界、替代方案加分\n- 模糊或回避深度问题给低分\n\n输出严格 JSON：\n{\n  "score": 0-100,\n  "reason": "判定理由（中文，100字内）",\n  "hits": ["命中的评分点"],\n  "misses": ["遗漏的评分点"]\n}',
    CAST('[
      {"name":"技术准确性","max":20},
      {"name":"架构思维","max":20},
      {"name":"深度实践","max":20},
      {"name":"问题解决","max":20},
      {"name":"技术视野","max":20}
    ]' AS JSON),
    0
  )
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  level = VALUES(level),
  prompt_template = VALUES(prompt_template),
  scoring_dimensions = VALUES(scoring_dimensions);

-- 种子题库（id=1，source=seed）
INSERT INTO question_bank (id, user_id, name, source, description) VALUES
  (1, 1, 'Java 默认题库', 'seed', '覆盖 JVM/集合/并发/Spring/MySQL/Redis 的 Java 高频面试题')
ON DUPLICATE KEY UPDATE name = name;

-- 30 道种子题（bank_id=1）
INSERT INTO question (bank_id, type, difficulty, content, standard_answer, scoring_points) VALUES
-- ========== JVM (5) ==========
(1, 'theory', 2, '请描述 JVM 的主要内存区域划分，以及各自存放的内容。',
 '堆（对象实例）、方法区/元空间（类信息、常量、静态变量）、虚拟机栈（栈帧、局部变量、操作数栈）、本地方法栈（native 方法）、程序计数器（当前指令地址）。堆是 GC 主战场，分为新生代（Eden+S0+S1）和老年代。',
 CAST('["堆/方法区/虚拟机栈/本地方法栈/程序计数器五区域","堆存放对象实例","方法区存类信息常量静态变量","新生代老年代划分","程序计数器线程私有"]' AS JSON)),
(1, 'theory', 2, '常见的垃圾回收算法有哪些？各有什么特点？',
 '标记-清除（产生碎片）、标记-复制（新生代用，浪费空间）、标记-整理（老年代用，无碎片但慢）、分代收集（新生代复制、老年代整理）。G1 用 Region 化分代，ZGC/Shenandoah 用染色指针做并发整理。',
 CAST('["标记清除/复制/整理三种基础算法","分代收集思想","G1的Region化","ZGC/Shenandoah并发整理","优缺点对比"]' AS JSON)),
(1, 'theory', 2, '请解释 JVM 类加载的过程。',
 '加载（找字节流生成 Class）、验证（格式/元数据/字节码/符号引用）、准备（静态变量赋零值）、解析（符号引用转直接引用）、初始化（执行 <clinit> 静态块赋值）。使用和卸载是后续阶段。',
 CAST('["加载/验证/准备/解析/初始化五阶段","准备阶段赋零值非初始值","初始化执行clinit","验证的四个子阶段","解析是符号引用转直接引用"]' AS JSON)),
(1, 'theory', 2, '什么是双亲委派模型？为什么需要它？',
 '类加载请求先委派给父加载器，父加载失败才自己加载。保证核心 API 不被替换（安全）、避免重复加载、保证类型一致性。但可被打破（SPI/JDBC 用线程上下文加载器、Tomcat 应用隔离）。',
 CAST('["委派给父加载器优先","保证核心API不被替换","避免重复加载","SPI/Tomcat打破场景","线程上下文加载器"]' AS JSON)),
(1, 'theory', 3, '对象在 JVM 中的创建过程？什么时候会进入老年代？',
 '创建：类加载检查→分配内存（指针碰撞/空闲列表，TLAB）→内存空间初始化零值→设置对象头→执行 <init>。进入老年代：年龄达阈值（默认15）、大对象直接进、动态年龄判断、Minor GC 后存活对象过多担保失败。',
 CAST('["分配内存的两种方式","TLAB","对象头设置","年龄阈值","大对象/动态年龄/空间担保"]' AS JSON)),
-- ========== 集合 (5) ==========
(1, 'theory', 2, 'HashMap 的底层实现原理？为什么用红黑树？',
 'JDK8 数组+链表+红黑树。put 时 hash 扰动计算桶下标，空桶直接放，否则尾插链表；链表长度≥8 且数组≥64 转红黑树，≤6 退化。扩容因子 0.75，2 倍扩容。红黑树防 hash 冲突退化成 O(n)。',
 CAST('["数组+链表+红黑树","hash扰动计算下标","链表转树阈值8","扩容因子0.75","红黑树防止O(n)退化"]' AS JSON)),
(1, 'theory', 3, 'ConcurrentHashMap 在 JDK7 和 JDK8 的实现差异？',
 'JDK7 分段锁 Segment（默认16段），每段独立加锁。JDK8 改为 Node 数组+链表+红黑树，put 用 CAS+synchronized 锁桶头节点，粒度更细；size 用 CounterCell 计数避免竞争。并发度更高。',
 CAST('["JDK7 Segment分段锁","JDK8 CAS+synchronized锁桶头","锁粒度从段到节点","CounterCell计数","转红黑树"]' AS JSON)),
(1, 'theory', 1, 'ArrayList 和 LinkedList 的区别？分别适合什么场景？',
 'ArrayList 基于数组，随机访问 O(1)，尾部增删 O(1) 均摊，中间增删 O(n) 需移动；LinkedList 基于双向链表，随机访问 O(n)，头尾增删 O(1)。ArrayList 适合读多写少、LinkedList 适合频繁头尾增删。实际很少用 LinkedList。',
 CAST('["ArrayList基于数组","LinkedList基于双向链表","随机访问复杂度对比","增删复杂度对比","适用场景"]' AS JSON)),
(1, 'theory', 3, 'TreeMap 的实现原理？什么时候用它？',
 '基于红黑树，key 必须实现 Comparable 或传 Comparator。增删查 O(log n)，key 有序。适合需要按 key 排序遍历或范围查询的场景。非线程安全，并发用 ConcurrentSkipListMap。',
 CAST('["红黑树实现","key必须Comparable或Comparator","O(logn)复杂度","key有序","ConcurrentSkipListMap并发替代"]' AS JSON)),
(1, 'theory', 2, '什么是 fail-fast？modCount 的作用？',
 '遍历时若结构被修改（modCount 变化），迭代器抛 ConcurrentModificationException，提示并发修改。modCount 记录结构修改次数，迭代器初始化时记录 expectedModCount，每次 next 比较不一致就抛异常。这是错误提示非保护机制。',
 CAST('["fail-fast抛ConcurrentModificationException","modCount记录修改次数","迭代器比较expectedModCount","是提示非保护","并发应用Collections.synchronizedList"]' AS JSON)),
-- ========== 并发 (5) ==========
(1, 'theory', 2, 'synchronized 和 ReentrantLock 的区别？',
 'synchronized 是 JVM 关键字（monitorenter/exit），自动释放，非公平，不可中断；ReentrantLock 是 API 类，需手动 unlock（finally），支持公平/非公平、可中断、可超时、多条件变量 Condition。功能更丰富但易错。',
 CAST('["synchronized是JVM关键字","ReentrantLock是API","公平/非公平","可中断可超时","Condition多条件变量"]' AS JSON)),
(1, 'theory', 3, 'volatile 的作用和原理？为什么不能保证原子性？',
 '作用：可见性（刷主存）、有序性（禁止指令重排，内存屏障）。不保证原子性：如 i++ 是读-改-写三步，volatile 只保证读到的值最新，但写回时可能被覆盖。原子性需 synchronized 或 AtomicInteger。',
 CAST('["可见性刷主存","有序性内存屏障","不保证原子性","i++读改写举例","AtomicInteger保证原子"]' AS JSON)),
(1, 'theory', 2, '线程池有哪几个核心参数？拒绝策略有哪些？',
 '7 参数：核心线程数、最大线程数、空闲存活时间、时间单位、工作队列、线程工厂、拒绝策略。4 种拒绝策略：Abort（抛异常，默认）、CallerRuns（调用者执行）、Discard（丢弃）、DiscardOldest（丢最老）。任务流转：核心→队列→非核心→拒绝。',
 CAST('["7个核心参数","4种拒绝策略","任务流转顺序","Abort默认","CallerRuns回压"]' AS JSON)),
(1, 'theory', 3, 'AQS 的原理？它如何实现独占和共享？',
 'AQS 用 volatile int state 表示同步状态，CLH 双向队列管理等待线程。独占：tryAcquire/tryRelease，state 0→1，头节点唤醒后继；共享：tryAcquireShared 返回值判断是否传播唤醒后续。ReentrantLock/Semaphore/CountDownLatch 都基于 AQS。',
 CAST('["volatile state同步状态","CLH双向队列","独占tryAcquire/Release","共享tryAcquireShared传播","ReentrantLock/Semaphore应用"]' AS JSON)),
(1, 'theory', 3, 'ThreadLocal 的原理？内存泄漏怎么发生？怎么避免？',
 '每个线程有 ThreadLocalMap，key 是 ThreadLocal 弱引用，value 是强引用。ThreadLocal 被回收后 key 变 null 但 value 仍在，线程长生命周期（如线程池）导致 value 泄漏。避免：用完 remove()，或用 InheritableThreadLocal/TransmittableThreadLocal。',
 CAST('["ThreadLocalMap结构","key弱引用value强引用","线程池场景泄漏","remove()清理","父子线程传递"]' AS JSON)),
-- ========== Spring (5) ==========
(1, 'theory', 2, '什么是 IOC？Spring 如何实现？',
 'IOC（控制反转）是把对象创建和依赖管理的控制权交给容器。通过 DI（依赖注入）实现：构造器、setter、字段注入。Spring 用 BeanDefinition 描述 bean，BeanFactory 创建，ApplicationContext 增强（事件、国际化、注解）。降低耦合。',
 CAST('["IOC控制反转概念","DI三种注入方式","BeanDefinition描述","BeanFactory/ApplicationContext","降低耦合"]' AS JSON)),
(1, 'theory', 2, 'Spring AOP 的实现原理？JDK 动态代理和 CGLIB 的区别？',
 'AOP 通过动态代理在运行时织入切面。接口用 JDK Proxy（基于 InvocationHandler 反射），类用 CGLIB（生成子类重写方法，FastClass 机制）。Spring 默认有接口用 JDK、无接口用 CGLIB。AspectJ 是编译时织入更强大。',
 CAST('["运行时动态代理","JDK Proxy需接口","CGLIB生成子类","Spring默认选择策略","AspectJ编译时织入"]' AS JSON)),
(1, 'theory', 3, 'Spring Bean 的生命周期？',
 '实例化→属性注入→Aware 回调（BeanName/BeanFactory）→BeanPostProcessor.before→初始化（@PostConstruct/InitializingBean/init-method）→BeanPostProcessor.after（AOP 代理在此生成）→使用→销毁（@PreDestroy/DisposableBean/destroy-method）。单例走完整流程，原型只前半段。',
 CAST('["实例化属性注入","Aware回调","BeanPostProcessor前后置","初始化三方式","AOP代理在after生成"]' AS JSON)),
(1, 'theory', 3, 'SpringBoot 自动配置的原理？',
 '@SpringBootApplication 含 @EnableAutoConfiguration，通过 AutoConfigurationImportSelector 加载 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports（2.7+，旧版 spring.factories）里的配置类。每个配置类用 @Conditional 按条件（Class/Bean/MissingBean）生效。starter 即提供配置类+依赖。',
 CAST('["@EnableAutoConfiguration","AutoConfigurationImportSelector","imports文件加载","@Conditional条件装配","starter机制"]' AS JSON)),
(1, 'theory', 3, 'Spring 事务的传播行为有哪些？默认是哪个？',
 '7 种：REQUIRED（默认，有则加入无则新建）、REQUIRES_NEW（总是新建挂起当前）、SUPPORTS（有则加入无则非事务）、NOT_SUPPORTED（非事务挂起当前）、MANDATORY（必须有否则异常）、NEVER（必须无否则异常）、NESTED（嵌套保存点）。自调用不走代理会失效。',
 CAST('["7种传播行为","REQUIRED默认","REQUIRES_NEW独立事务","NESTED嵌套保存点","自调用失效问题"]' AS JSON)),
-- ========== MySQL (5) ==========
(1, 'theory', 2, 'MySQL InnoDB 索引为什么用 B+ 树？',
 'B+ 树非叶子节点只存索引不存数据，单节点能存更多 key，树更矮磁盘 IO 少；叶子节点有序链表，范围查询高效；所有数据在叶子，查询稳定 O(log n)。对比 B 树范围查询需回溯，Hash 不支持范围，二叉树退化。',
 CAST('["非叶子只存key树更矮","磁盘IO少","叶子有序链表范围查询快","查询稳定O(logn)","对比B树/Hash"]' AS JSON)),
(1, 'theory', 2, 'MySQL 的四种事务隔离级别？分别解决什么问题？',
 '读未提交（脏读）、读已提交（不可重复读）、可重复读（幻读，InnoDB 用 MVCC+间隙锁解决）、串行化（全锁）。InnoDB 默认 RR。MVCC 在 RC 每次读生成新 read view，RR 复用事务开始时的 read view。',
 CAST('["四种隔离级别","脏读/不可重复读/幻读","RR默认","MVCC read view时机","间隙锁解决幻读"]' AS JSON)),
(1, 'theory', 3, 'MVCC 的原理？undo log 怎么用？',
 '每行有隐藏 trx_id（最近修改事务 ID）和 roll_pointer（指向 undo log 旧版本）。读时根据 read view 可见性判断：trx_id < min_trx 可见、> max_trx 不可见、在活跃列表不可见。不可见则沿 roll_pointer 找旧版本。实现非锁定读。',
 CAST('["隐藏trx_id和roll_pointer","undo log版本链","read view可见性判断","三步判断规则","非锁定读"]' AS JSON)),
(1, 'theory', 3, 'MySQL 有哪些锁？行锁是怎么实现的？',
 '表锁（MyISAM、DDL）、行锁（InnoDB）。行锁分记录锁（锁单行）、间隙锁（锁区间防插入）、临键锁（记录+前间隙，RR 默认）。InnoDB 行锁基于索引，无索引会退化为表锁。还有意向锁（IS/IX 表级）、MDL（元数据锁）。',
 CAST('["表锁行锁分类","记录锁/间隙锁/临键锁","基于索引实现","无索引退化表锁","意向锁/MDL"]' AS JSON)),
(1, 'scenario', 2, '如何用 EXPLAIN 分析 SQL？重点关注哪些字段？',
 '重点关注：type（访问类型，从 system/const 到 ALL，最好 ref/range 以上）、key（实际用索引）、rows（扫描行数）、Extra（Using index 覆盖索引、Using filesort 额外排序、Using temporary 临时表）。避免 ALL 全表扫描和 filesort。',
 CAST('["type访问类型","key实际索引","rows扫描行数","Extra关键信息","避免ALL/filesort"]' AS JSON)),
-- ========== Redis (5) ==========
(1, 'theory', 1, 'Redis 有哪些常用数据类型？分别适合什么场景？',
 'String（计数器、缓存对象）、List（消息队列、最新列表）、Hash（对象存储）、Set（标签、去重、交集）、ZSet（排行榜、延时队列）。还有 BitMap（签到）、HyperLogLog（UV 去重估算）、Geo（附近）、Stream（消息流）。',
 CAST('["五种基础类型","String/List/Hash/Set/ZSet场景","BitMap签到","HyperLogLog去重估算","Stream消息流"]' AS JSON)),
(1, 'theory', 2, 'Redis 的 RDB 和 AOF 持久化各有什么特点？',
 'RDB：二进制快照，bgsave fork 子进程，恢复快、文件小，但可能丢最后一段数据。AOF：追加命令，可 everysec 平衡性能安全，恢复慢文件大。AOF 重写压缩。Redis 4.0+ 混合持久化（RDB 全量+AOF 增量）。生产推荐 AOF+everysec 或混合。',
 CAST('["RDB快照fork子进程","AOF追加命令","everysec策略","AOF重写","4.0混合持久化"]' AS JSON)),
(1, 'scenario', 2, '缓存穿透、击穿、雪崩分别是什么？怎么解决？',
 '穿透（查不存在的 key 打 DB）：布隆过滤器、空值缓存。击穿（热点 key 过期瞬间高并发）：互斥锁重建、热点 key 永不过期。雪崩（大量 key 同时过期）：过期时间加随机、多级缓存、限流降级。',
 CAST('["穿透=查不存在","布隆过滤器/空值缓存","击穿=热点过期","互斥锁/永不过期","雪崩=随机过期+多级"]' AS JSON)),
(1, 'theory', 3, '如何用 Redis 实现分布式锁？有什么问题？',
 '基础：SET key value NX PX 过期。问题：业务超时导致锁误释放（value 用唯一 ID，释放用 Lua 比对删除）、主从故障锁丢失。Redlock 多节点多数派提升可靠性但争议。生产建议 Redisson（看门狗续期、可重入、Lua 释放）。极端强一致用 ZK/etcd。',
 CAST('["SET NX PX基础","value唯一ID+Lua释放","业务超时误释放","Redlock多节点","Redisson看门狗"]' AS JSON)),
(1, 'theory', 2, 'Redis 的过期删除和内存淘汰策略？',
 '过期删除：惰性（访问时检查）+ 定期（随机抽样删除）。8 种淘汰：noeviction（默认拒写）、allkeys-lru、allkeys-lfu、allkeys-random、volatile-lru、volatile-lfu、volatile-random、volatile-ttl。生产推荐 allkeys-lru 或 volatile-lru。',
 CAST('["惰性+定期删除","8种淘汰策略","noeviction默认拒写","allkeys-lru推荐","volatile只淘汰带过期"]' AS JSON));
