package com.example.serer

import com.example.commands.CmdType
import com.example.exception.ExceptionType
import com.example.message.{ErrorMessage, Message, MessageBody}
import com.example.tick.ITick
import io.netty.channel.ChannelHandlerContext

/**
 * PlayerChannels 对象用于管理所有玩家网络连接的通道
 * 它提供了添加、移除通道，以及向指定玩家或所有玩家发送消息的功能
 */
object PlayerChannels extends ITick {
  // 使用可变Map存储玩家ID和对应的ChannelHandlerContext
  private val channels = scala.collection.mutable.Map[String, ChannelHandlerContext]()

  private var lastHeartbeatTime = 0L
  /**
   * 添加玩家通道
   * @param playerId 玩家唯一标识符
   * @param ctx Netty的ChannelHandlerContext对象，代表一个网络连接通道
   */
  def addChannel(playerId: String, ctx: ChannelHandlerContext): Unit = {
    channels += (playerId -> ctx)
  }

  /**
   * 移除玩家通道
   * @param playerId 要移除的玩家ID
   */
  def removeChannel(playerId: String): Unit = {
    channels -= playerId
  }

  /**
   * 向指定玩家发送消息
   * @param playerId 目标玩家ID
   * @param msg 要发送的消息对象
   */
  def send(playerId: String, msg: Message): Unit = {
    channels.get(playerId) match {
      case Some(ctx) => ctx.writeAndFlush(msg)  // 如果找到通道，则发送消息
      case None => println(s"玩家 $playerId 未找到.")  // 如果玩家不存在，打印提示信息
      case _ => println("未知的message类型")  // 其他未知情况处理
    }
  }

  /**
   * 向指定玩家发送错误消息
   * @param playerId 目标玩家ID
   * @param msg 错误消息内容
   * @param errTyp 错误类型，默认为DEFAULT
   */
  def sendError(playerId: String, msg: String, errTyp: Int = ExceptionType.DEFAULT): Unit = {
    send(playerId, ErrorMessage(msg, errTyp))
  }

  /**
   * 向所有玩家广播消息
   * @param msg 要广播的消息对象
   */
  def sendToAll(msg: Message): Unit = {
    channels.keys.foreach(send(_, msg))  // 遍历所有玩家ID并发送消息
  }

  /**
   * 向指定玩家发送警告消息
   * @param playerId 目标玩家ID
   * @param msg 警告消息内容
   */
  def alert(playerId: String, msg: String): Unit = {
    send(playerId, Message(CmdType.ALERT, MessageBody("msg" -> msg)))
  }

  /**
   * 向所有玩家广播警告消息
   * @param msg 警告消息内容
   */
  def alertAll(msg: String): Unit = {
    sendToAll(Message(CmdType.ALERT, MessageBody("msg" -> msg)))
  }

  /**
   * 向指定玩家发送信息消息
   * @param playerId 目标玩家ID
   * @param msg 信息消息内容
   */
  def info(playerId: String, msg: String): Unit = {
    send(playerId, Message(CmdType.INFO, MessageBody("msg" -> msg)))
  }

  /**
   * 向所有玩家广播信息消息
   * @param msg 信息消息内容
   */
  def infoAll(msg: String): Unit = {
    sendToAll(Message(CmdType.INFO, MessageBody("msg" -> msg)))
  }

  /**
   * 定时检查玩家心跳状态
   * 遍历所有玩家通道，检查其最后心跳时间是否超时
   * 如果超过10秒未收到心跳，则关闭该玩家的连接通道
   */
  def tick(tickIndex: Long): Unit = {
    // TODO: 检查心跳
    val currentTime = System.currentTimeMillis()
    if(currentTime - lastHeartbeatTime > 10000){
      channels.foreach({case (playerId, ctx) =>
        // 获取该玩家最后心跳时间，如果超过10秒未收到心跳，则执行超时处理(三次心跳时间)
        if (currentTime - ctx.channel().attr(MasterHandler.ATTR_HEARTBEAT).get() > 15000) {
          // TODO: 超时处理
          println(s"[Heartbeat]玩家 $playerId 超时，断开连接")
/*          // 调用玩家的断开连接处理逻辑
          PlayerHolder.getPlayer(playerId).onDisConnect()
          // 关闭该玩家的网络连接
          ctx.close()*/
        }
      })
      lastHeartbeatTime = currentTime
    }

  }

  /**
   * 获取当前通道数量，用于JMX监控
   * @return 当前在线玩家数量
   */
  def getChannelCount: Int = {
    channels.size
  }

  /**
   * 获取心跳超时的玩家数量，用于JMX监控
   * @return 超时玩家数量
   */
  def getTimeoutPlayerCount: Int = {
    val currentTime = System.currentTimeMillis()
    channels.count { case (_, ctx) =>
      currentTime - ctx.channel().attr(MasterHandler.ATTR_HEARTBEAT).get() > 15000
    }
  }
}