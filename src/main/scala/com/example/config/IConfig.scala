package com.example.config

/**
 * IConfig 是一个配置加载的特质接口
 * 定义了配置加载的基本功能规范
 */
trait IConfig {
  /**
   * 加载配置的方法
   * 子类需要实现此方法来完成具体的配置加载逻辑
   */
  def loadConfigs(): Unit
}
