package com.example.serer

/**
 * ServerConfig 是一个服务器配置对象，用于加载和管理服务器相关的配置信息
 * 它从配置文件中读取端口号、线程数等关键参数
 */
object ServerConfig {
  // 定义配置文件的路径，通过系统属性获取
  private val configPath = System.getProperty("serverConfig")

  /**
   * 加载配置文件并解析为Properties对象
   * 使用当前线程的类加载器加载配置文件
   */
  private val properties = {
    val is = Thread.currentThread().getContextClassLoader.getResourceAsStream(configPath)
    val props = new java.util.Properties()
    props.load(is)  // 从输入流中加载配置属性
    props
  }

  val clientHost: String = properties.getProperty("server.host")

  // 获取服务器端口号配置
  val clientPort: Int = properties.getProperty("server.port").toInt

  // 获取服务器Boss线程数量配置
  val bossThreadNum: Int = properties.getProperty("server.boss.thread.num").toInt

  // 获取服务器Worker线程数量配置
  val workerThreadNum: Int = properties.getProperty("server.worker.thread.num").toInt
}
