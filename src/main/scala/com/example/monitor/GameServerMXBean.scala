
package com.example.monitor

/**
 * GameServerMXBean 定义了游戏服务器的监控接口
 * 通过JMX可以监控服务器的各种运行状态
 */
trait GameServerMXBean {
  /**
   * 获取当前在线玩家数量
   * @return 在线玩家数量
   */
  def getOnlinePlayerCount: Int

  /**
   * 获取服务器启动时间
   * @return 服务器启动时间戳
   */
  def getServerStartTime: Long

  /**
   * 获取服务器运行时长(毫秒)
   * @return 服务器运行时长
   */
  def getServerUptime: Long

  /**
   * 获取处理的消息总数
   * @return 消息总数
   */
  def getTotalMessages: Long

  /**
   * 获取每秒处理的消息数
   * @return 每秒消息数
   */
  def getMessagesPerSecond: Double

  /**
   * 获取Boss线程数
   * @return Boss线程数
   */
  def getBossThreadCount: Int

  /**
   * 获取Worker线程数
   * @return Worker线程数
   */
  def getWorkerThreadCount: Int

  /**
   * 获取服务器端口
   * @return 服务器端口
   */
  def getServerPort: Int

  /**
   * 获取服务器主机
   * @return 服务器主机
   */
  def getServerHost: String

  /**
   * 获取心跳超时的玩家数量
   * @return 超时玩家数量
   */
  def getTimeoutPlayerCount: Int

  /**
   * 重置消息计数器
   */
  def resetMessageCount(): Unit
}
