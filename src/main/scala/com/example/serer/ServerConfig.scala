package com.example.serer

object ServerConfig {
  private val configPath = System.getProperty("serverConfig")

  private val properties = {
    val is = Thread.currentThread().getContextClassLoader.getResourceAsStream(configPath)
    val props = new java.util.Properties()
    props.load(is)
    props
  }

  val clientPort: Int = properties.getProperty("server.port").toInt

  val bossThreadNum: Int = properties.getProperty("server.boss.thread.num").toInt

  val workerThreadNum: Int = properties.getProperty("server.worker.thread.num").toInt
}
