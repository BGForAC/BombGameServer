
package com.example.monitor

import com.example.serer.{GameServer, PlayerChannels, ServerConfig}
import io.netty.channel.ChannelHandlerContext

import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import javax.management.{InstanceAlreadyExistsException, MBeanServer, ObjectName}

/**
 * GameServerMonitor 实现了GameServerMXBean接口
 * 负责收集和提供游戏服务器的监控数据
 */
object GameServerMonitor extends GameServerMXBean {
  // 服务器启动时间
  private val serverStartTime: Long = System.currentTimeMillis()

  // 消息计数器
  private val messageCount = new AtomicLong(0)

  // 上次统计时间
  private var lastStatsTime = System.currentTimeMillis()

  // 上次统计时的消息数
  private var lastMessageCount = 0L

  // MBean服务器
  private var mBeanServer: MBeanServer = _

  /**
   * 初始化JMX监控
   */
  def initJMX(): Unit = {
    try {
      // 获取MBean服务器
      mBeanServer = ManagementFactory.getPlatformMBeanServer

      // 创建MBean名称
      val mBeanName = new ObjectName("com.example.monitor:type=GameServerMonitor")

      // 注册MBean
      if (!mBeanServer.isRegistered(mBeanName)) {
        mBeanServer.registerMBean(this, mBeanName)
        println("JMX监控已启动: com.example.monitor:type=GameServerMonitor")
      }
    } catch {
      case _: InstanceAlreadyExistsException =>
        println("JMX MBean已存在，跳过注册")
      case e: Exception =>
        println(s"JMX监控初始化失败: ${e.getMessage}")
        e.printStackTrace()
    }
  }

  /**
   * 增加消息计数
   */
  def incrementMessageCount(): Unit = {
    messageCount.incrementAndGet()
  }

  /**
   * 获取当前在线玩家数量
   * @return 在线玩家数量
   */
  override def getOnlinePlayerCount: Int = {
    PlayerChannels.getChannelCount
  }

  /**
   * 获取服务器启动时间
   * @return 服务器启动时间戳
   */
  override def getServerStartTime: Long = {
    serverStartTime
  }

  /**
   * 获取服务器运行时长(毫秒)
   * @return 服务器运行时长
   */
  override def getServerUptime: Long = {
    System.currentTimeMillis() - serverStartTime
  }

  /**
   * 获取处理的消息总数
   * @return 消息总数
   */
  override def getTotalMessages: Long = {
    messageCount.get()
  }

  /**
   * 获取每秒处理的消息数
   * @return 每秒消息数
   */
  override def getMessagesPerSecond: Double = {
    val currentTime = System.currentTimeMillis()
    val timeDiff = currentTime - lastStatsTime

    if (timeDiff > 0) {
      val currentMessageCount = messageCount.get()
      val messageDiff = currentMessageCount - lastMessageCount

      // 更新统计时间
      lastStatsTime = currentTime
      lastMessageCount = currentMessageCount

      // 计算每秒消息数
      (messageDiff.toDouble / timeDiff) * 1000.0
    } else {
      0.0
    }
  }

  /**
   * 获取Boss线程数
   * @return Boss线程数
   */
  override def getBossThreadCount: Int = {
    ServerConfig.bossThreadNum
  }

  /**
   * 获取Worker线程数
   * @return Worker线程数
   */
  override def getWorkerThreadCount: Int = {
    ServerConfig.workerThreadNum
  }

  /**
   * 获取服务器端口
   * @return 服务器端口
   */
  override def getServerPort: Int = {
    ServerConfig.clientPort
  }

  /**
   * 获取服务器主机
   * @return 服务器主机
   */
  override def getServerHost: String = {
    ServerConfig.clientHost
  }

  /**
   * 获取心跳超时的玩家数量
   * @return 超时玩家数量
   */
  override def getTimeoutPlayerCount: Int = {
    PlayerChannels.getTimeoutPlayerCount
  }

  /**
   * 重置消息计数器
   */
  override def resetMessageCount(): Unit = {
    messageCount.set(0)
    lastMessageCount = 0
    lastStatsTime = System.currentTimeMillis()
  }
}
