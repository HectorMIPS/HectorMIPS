# 简单通用cache
* 预留替换策略
* 预留组相连个数（路数）
* 固定4KB每路
* ram接口->类sram/axi
## 细节
### 地址如何划分
* VIPT  
31-12: TAG   
11-4:  Index  
3-0 :  Offset
  
### CACHE表项
* TAG 20b
* V   1b
* D   1b
* Data 可选
### 行为
* Lookup 查询 
* Hit Write 命中写
* Replace 替换需要抛弃的行，并从内存读一行
* Refill  把替换行安上去


|              | Lookup         | Hit Write        |Replace         |Refill|
|  ----        | ----           |----              | ----           |----|
| {TAG,V}(21)  | 读n路           |-                 |读替换路         | 写替换路    |
| D(1)         | 读n路           |修改写标记          |读替换路         | 写替换路    |
| DATA(不定)    | 读命中路（局部）  |写命中行（局部）     |读替换路（全部）   | 读替换路（全部）    |


### 时序
下面是阻塞的
#### IDLE
* to lookup   
有请求，进入LOOKUP  
用Addr的index 取出本组n路cache，地址送TLB转换为虚拟地址

#### LOOKUP
取cache TAG 组相连比较
* to IDLE  
    成功，返回数据，且没有新访存
* to LOOKUP  
成功，返回数据，且有新访存

##### to Miss
失败，向内存请求，

#### Miss
* to MISS  
    总线忙，不允许写
* to REPLACE
    允许写，发起写请求
#### REPLACE
* to REPLACE
    
