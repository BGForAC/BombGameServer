package com.example.holder



/**
 * AutoGrow特质，用于自动生成唯一ID
 * 提供了一个自动增长的ID生成功能
 */
trait AutoGrow { // 定义一个名为AutoGrow的特质
  var id: Int = 0 // 声明一个可变的Int类型变量id，初始值为0



  /**
   * 生成唯一ID的方法
   * 每次调用时，id值自动加1并返回新值
   * @return 返回当前id值（自增后的值）
   */
  def generateId() = { // 定义一个无参数的方法generateId
    id += 1 // id值自增1
    id // 返回自增后的id值
  }
}
