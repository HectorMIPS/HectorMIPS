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
* 关键字优先
* 统计miss率
* 优化时序
* 尝试扩大缓存
* 修改victim为bram


