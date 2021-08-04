# HectorMIPS

“龙芯杯”北京邮电大学2021年参赛作品，一个高性能的双发射静态调度MIPS处理器。

## 概述

本项目是在“龙芯杯”大赛方提供的 FPGA 实验平台（FPGA 为 Artix-7 XC7A200T）上实现一个片上系统（SOC）。其中，CPU基于MIPS 32 Rev 1指令集架构，包含指令缓存和数据缓存；功能方面能够通过大赛方提供的功能测试、性能测试、系统测试；性能方面频率达 #TODO MHz，每时钟周期指令数为龙芯 GS132 的 #TODO 倍。

### 设计语言

HectorMIPS完全使用Chisel3编写，再通过Chisel3编译到Verilog导入到vivado中。得益于使用高级语言开发，我们可以有更高的开发速度和更低的bug率

### 设计框架

HectorMIPS中的CPU采用顺序双发射伪六级流水线架构，实现了包括除4条非对齐指令外的所有MIPS I指令、MIPS32中的ERET指令以及MUL指令，共58条指令，5个CP0 寄存器，3个中断，7种例外。CPU对外的访存通信通过四个接口，分别是Uncached属性指令接口、Cached属性指令接口、Uncached属性数据接口、Cached属性数据接口。四个接口通过AXI3协议，经过AXI Crossbar整合成为一个接口与外设交互。

HectorMIPS实现了指令缓存(I-Cache)与数据缓存(D-Cache)，响应CPU的取指与访存请求。I-Cache与D-Cache大小均为16K（#TODO确认大小），在连续命中时，能够实现不间断地流水返回数据。I-Cache与D-Cache分别引出一个AXI接口。D-Cache能够缓冲 CPU的写请求，并且实现了一个写回缓存 (Victim Cache)，兼具了缓存与写回队列的功能。

## CPU

### 流水线结构

#TODO

### 指令集(＃TODO确认指令)

CPU 在大赛要求的 57 条指令基础之上，增加了部分指令以启动操作系统。实现的所 有指令如下： 

* **算术运算指令** ADD, ADDU, SUB, SUBU, ADDI, ADDIU, SLT, SLTU, SLTI, SLTIU, MUL, MULT, MULTU, DIV, DIVU, MADD, MADDU, MSUB, MSUBU, CLO, CLZ
* **逻辑运算指令** AND, OR, XOR, ANDI, ORI, XORI, NOR, LUI
* **移位指令** SLL, SRL, SRA, SLLV, SRLV, SRAV
* **访存指令** SB, SH, SW, SWL, SWR, LB, LBU, LH, LHU, LW, LWL, LWR
* **分支跳转指令** BEQ, BNE, BGEZ, BGTZ, BLEZ, BLTZ, BGEZAL, BLTZAL, J, JAL, JR, JALR
* **数据移动指令** MFHI, MFLO, MTHI, MTLO, MOVZ, MOVN
* **特权指令** CACHE（实现为空）, SYSCALL, BREAK, TLBR, TLBWR, TLBWI, TLBP, ERET, MTC0, MFC0, PREF（实现为空）, SYNC（实现为空）, WAIT（实现为空）

### 协处理器 0(＃TODO确认寄存器)

CPU实现了MIPS 32 Rev 1规范中协处理器0中的大部分寄存器，同时为了启动操作系统，实现了MIPS 32 Rev 2规范中的EBase寄存器。所有寄存器如下，名称摘录自参考资料3：

* Index Register (CP0 Register 0, Select 0)
* Random Register (CP0 Register 1, Select 0)
* EntryLo0, EntryLo1 (CP0 Registers 2 and 3, Select 0)
* Context Register (CP0 Register 4, Select 0)
* PageMask Register (CP0 Register 5, Select 0)
* Wired Register (CP0 Register 6, Select 0)
* BadVAddr Register (CP0 Register 8, Select 0)
* Count Register (CP0 Register 9, Select 0)
* EntryHi Register (CP0 Register 10, Select 0)
* Compare Register (CP0 Register 11, Select 0)
* Status Register (CP Register 12, Select 0)
* Cause Register (CP0 Register 13, Select 0)
* Exception Program Counter (CP0 Register 14, Select 0)
* Processor Identication (CP0 Register 15, Select 0)
* EBase Register (CP0 Register 15, Select 1) 
* Conguration Register (CP0 Register 16, Select 0)
* Conguration Register 1 (CP0 Register 16, Select 1)

### 内存管理

＃TODO

### 中断和异常

＃TODO

### 分支预测

CPU实现了两级自适应分支预测，分支指令缓存使用64块（＃TODO确认数量）全相连映射，每一块使用4个（＃TODO确认数量）分支历史寄存器，用于记录同一条pc在不同情况下的跳转历史并作出预测，可以预测在2周期（＃TODO确认数量，为log2(bht_size)）以内出现的所有重复序列的模式。

每一次成功/失败的分支预测都会更新其对应pc的Branch history，再更新当前Branch history对应的2位饱和计数器。在预测分支时，即可通过branch history找到对应的饱和计数器，从而得到分支预测的结果

![420px-Two-level_branch_prediction.svg](./README.assets/420px-Two-level_branch_prediction.svg.png)

### 缓存设计

＃TODO

### CPU 外部接口

＃TODO

## 思考与展望

