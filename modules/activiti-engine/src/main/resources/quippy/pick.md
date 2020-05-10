## 流程引擎模块

流程虚拟机规范的定义和实现

核心接口：围绕流程7大服务展开，采用门面模式
核心实现：基于命令拦截器的模式进行实现

核心业务逻辑被封装为一个个的命令接口实现类
命令本身需要运行在命令上下文中
采用责任链模式，通过责任链模式的拦截器层，为命令的执行创造条件

核心实体：
    ActivityImpl        该类抽象了节点实现
    FlowElementBehavior 该类抽象了节点指令动作
    ExecutionImpl       流程执行实体类

命令模式：
Command命令接口，所有的具体命令都需要实现该类，最终业务就是执行该类的 execute 方法
CommandContext命令上下文，为具体命令的执行提供上下文支撑
CommandContextInterceptor命令上下文拦截器，生成命令上下文

命令的执行：需要经过命令拦截器链
自定义前置命令拦截器 -> 默认命令拦截器 —> 自定义后置命令拦截器 -> 命令执行拦截器

默认命令拦截器：事务拦截器、命令上下文拦截器

流程定义解析
Activiti采用的STAX的拉模型进行XML解析
BpmnXMLConverter:   XML文件 -> ProcessDefinitionEntity实体
BpmnParseHandlers:  ProcessDefinitionEntity实体 -> ActivityImp实体

一个 ActivityImpl 在以下两种情况下会被定义为作用域 ActivityImpl。

该 ActivityImpl 是可变范围，则它是一个作用域。可变范围可以理解为该节点的内容定义是可变的。比如流程定义、子流程，其内部内容是可变的。根据 BPMN 定义，可变范围有：流程定义，子流程，多实例，调用活动。
该 ActivityImpl 定义了一个上下文用于接收事件。比如：具备边界事件的 ActivityImpl，具备事件子流程的 ActivityImpl，事件驱动网关，中间事件捕获 ActivityImpl。


Bpmn模型
PVM模型

Activiti采取了领域中的充血模型作为自己的实现方式
大部分业务逻辑都直接关联在ExecutionEntity实体中

Activiti 的持久化机制简单说来就是数据集中提交（自动提交）

PVM 执行树
PVM 将流程定义描述为流程元素的集合。再将流程元素细分为 2 个子类：流程节点和连线。
流程节点：是某一种动作表达的抽象描述。节点本身是可以嵌套的，也就是节点可以拥有子节点。
连线：表达是不同节点之间的转移关系。一个连线只能有一个源头节点和一个目标节点，而节点本身可以有任意多的进入连线和外出连线。

PvmActivity 继承于 PvmScope。这种继承关系表明流程节点本身有其归于的作用域（PvmScope）
节点本身也可能是另外一些节点的作用域
通过流程节点和连线，PVM 完成了对流程定义的表达


定义期：流程定义是一个流程的静态表达
运行期：流程执行则是依照流程定义启动的一个运行期表达，每一个流程执行都具备自己唯一的生命周期

流程执行需要具备以下要素：

1. 流程节点的具体执行动作 ActivityBehavior
2. 当前处于哪一个流程节点 PvmExecution
3. 如何从一个节点运行至下一个节点 InterpretableExecution
4. 如何从当前流程状态转变到下一个状态 AtomicOperation

PVM执行树

ExecutionEntity 的含义是一个流程定义被启动后的执行实例，代表着流程的运行期状态。在 Activiti 的设计中，事件订阅，流程变量等都是与一个具体的 ExecutionEntity 相关的。其本身有几个重要的属性：

isScope：该属性为真时，意味该执行实例在执行一个具备作用域的 ActivityImpl 节点或者执行一个流程定义。更简单一些，意味着该实例正在执行一个作用域活动。
isConcurrent：该属性为真时，意味与该执行实例正在执行的活动节点同属相同作用域的节点可能正并发被其他执行实例执行（比如并行网关后面的 2 个并行任务）。
isActive：该属性为真时，意味该执行实例正在执行一个简单 ActivityImpl（不包含其他 ActivityImpl 的 ActivityImpl）
isEventScope：该属性为真时，意味该执行实例是为了后期补偿而进行的变量保存所创建的执行实例。由于流程执行中的变量都需要与 ExecutionEntity 挂钩，而补偿是需要原始变量的快照。为了满足这个需求，创建出一个专用于此的 ExecutionEntity。
activityId：该 ExecutionEntity 正在执行的 ActivityImpl 的 id。正在执行意味着几种情况：进入该节点，执行该节点动作，离开该节点。如果是等待子流程的完成，则该属性为 null。

ExecutionEntity实现了一些重要接口：

PVM相关的接口，赋予了ExecutionEntity流程驱动的能力，例如single、start方法。
实现VariableScope接口让ExecutionEntity可以持久上下文变量。
ProcessInstance接口暴露了ExecutionEntity关联的ProcessDefinitionEntity的信息。
PersistentObject接口代表ExecutionEntity对象是需要持久化。

驱动流程

Activiti框架的流程运行于PVM模型之上，在流程运行时主要涉及到PVM中几个对象：ActivityImpl、TransitionImpl和ActivityBehavior。

ActivityImpl：ActivityImpl是流程节点的抽象，ActivityImpl维护流程图中节点的连线，包括有哪些进线，有哪些出线。另外还包含节点同步／异步执行等信息。
TransitionImpl：TransitionImpl包含source和target两个属性，连接了两个流程节点。
ActivityBehavior：每一个ActivityImpl对象都拥有一个ActivityBehavior对象，ActivityBehavior代表节点的行为。
ActivityImpl、TransitionImpl和ActivityBehavior只是描述了流程的节点、迁移线和节点行为，真正要让ExecutionEntity流转起来，还需要AtomicOperation的驱动：



代码分析入口：
1. 创建流程引擎      ProcessEngineConfiguration
2. 部署流程图        RepositoryService
3. 发起流程         RuntimeService

PROCESS_START做了几件事：

获取流程定义级别定义的监听start事件的ExecutionListener，调用notify方法。
如果开启了事件功能，发布ActivitiEntityWithVariablesEvent和ActivitiProcessStartedEvent。
调用PROCESS_START_INITIAL。
PROCESS_START_INITIAL也实现了类似的功能：

获取初始节点上定义的监听start事件的ExecutionListener，调用notify方法。
调用ACTIVITY_EXECUTE。
ACTIVITY_EXECUTE：调用当前activity的behavior。
TRANSITION_NOTIFY_LISTENER_END：某个activity节点执行完毕，调用节点上声明的监听end事件的ExecutionListener。
TRANSITION_NOTIFY_LISTENER_TAKE：触发线上的ExecutionListener。
TRANSITION_NOTIFY_LISTENER_START：某个activity节点即将开始执行，调用节点上的监听start事件的ExecutionListener


总体思路：

将所有流程相关的操作抽象为命令，在特定的命令上下文环境中执行，执行过程会遵从约定的执行链依次执行。
流程的推动，可以看作一系列有顺序的原子操作，原子操作会改变流程的状态，不同的节点对应不同的行为。

## activiti-engine 模块
|- engine          -   流程虚拟机定义和服务
    |- cfg/            -   流程引擎、邮件服务
    |- delegate/       -   活动实体关联的事件和事件分发          
    |- event/          -   事件记录格式
    |- form/           -   表单定义
    |- history/        -   历史数据和查询
    |- identity/       -   用户和分组
    |- logging/        -   日志诊断
    |- management/     -   流程管理
    |- parse/          -   bpmn文件解析
    |- query/          -   数据查询
    |- repository/     -   部署和流程定义
    |- runtime/        -   运行时流程实例
    |- task/           -   任务
    |- test/           -   流程引擎测试
    |- impl/           -   流程虚拟机实现
        |- asyncexecutor/       -   异步执行器
        |- bpmn/                -   bpmn解析和转换
        |- calendar/            -   日历和作业计划
        |- cfg/                 -   基础和配置
        |- cmd/                 -   命令执行器
        |- context/             -   上下文
        |- db/                  -   数据库
        |- delegate/            -   委托代理
        |- el/                  -   EL表达式
        |- event/               -   预定义事件处理器
        |- form/                -   表单处理
        |- history/             -   历史查询处理
        |- identity/            -   用户
        |- interceptor/         -   拦截器，命令，日志，事务
        |- javax/               -   EL解析
        |- jobexecutor/         -   作业执行器
        |- json/                -   JSON转换器
        |- juel/                -   juel 支持
        |- persistence/         -   数据持久化
        |- repository/          -   部署
        |- rules/               -   规则 ？
        |- runtime/             -   运行时
        |- scripting/           -   脚本
        |- task/                -   任务
        |- test/                -   测试
        |- transformer/         -   转换器
        |- util/                -   公共工具
        |- variable/            -   流程变量
        |- webservice/          -   WS服务
        |- pvm/                 -   流程虚拟机核心
            |- delegate/            -   行为委托
            |- process/             -   流程活动实体
            |- runtime/             -   运行时原子操作


代码实现：
接口定义方法和常量；
抽象类定义模板方法，子类实现具体行为；
上下文定义统一的数据获取入口；


行为模式的最佳实践：
职责链模式：类似于工厂的生产线，处理完当前工序后进入下一道工序。
命令模式：类似于命令行，解耦命令的下达者和执行者。命令下达者分散于具体的业务逻辑中，执行者专注于自己的命令执行
