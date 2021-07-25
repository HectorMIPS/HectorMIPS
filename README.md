# cache优化目标
## icache
* 预取器prefetching
* MSHR(如果流水线做动态调度再加)
## dcache
* store buffer
* 预取器prefetching
* MSHR(如果流水线做动态调度再加)
* 接入sram
## uncache
* store buffer


# 现在在做的工作
## 7.24
* 关键字优先（dcache有问题）
* 统计miss率

## 7.25
* 尝试扩大缓存（时序不合格）
* 修改victim,使其从1个周期变为多个周期，降低时序要求
* 目前规格：16K


