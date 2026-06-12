package com.example.serer

import com.example.commands.CmdType
import com.example.exception.ExceptionType
import com.example.message.{ErrorMessage, Message, MessageBody}
import com.example.tick.ITick
import io.netty.buffer.Unpooled
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
   * 检查指定玩家是否拥有活跃通道（用于防止顶号登录时误删 PlayerHolder）
   * @param playerId 玩家ID
   * @return 如果玩家拥有活跃通道返回 true
   */
  def hasChannel(playerId: String): Boolean = {
    channels.contains(playerId)
  }

  /**
   * 关闭并移除指定玩家的旧通道（用于玩家重新登录时注销原连接）
   * @param playerId 玩家ID
   */
  def closeAndRemoveChannel(playerId: String): Unit = {
    channels.get(playerId) match {
      case Some(ctx) =>
        // 先发送 LOGOUT 消息通知客户端
        ctx.writeAndFlush(Message(CmdType.LOGOUT, MessageBody()))
        ctx.close()
        channels -= playerId
      case None => // 无旧通道，无需处理
    }
  }

  /**
   * 向指定玩家发送消息
   * @param playerId 目标玩家ID
   * @param msg 要发送的消息对象
   */
  def send(playerId: String, msg: Message): Unit = {
    channels.get(playerId) match {
      case Some(ctx) => ctx.writeAndFlush(msg)  // 如果找到通道，则发送消息
      case _ => // 玩家通道不存在，忽略
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
   * 向所有玩家广播消息（预序列化优化：消息只编码一次，复用给所有通道）
   * @param msg 要广播的消息对象
   */
  def sendToAll(msg: Message): Unit = {
    if (channels.isEmpty) return

    // 预编码消息到 wire format：[4B长度前缀][4B cmdType][body bytes]
    val bodyBytes = msg.getBody.toBytes
    val totalLen = 4 + bodyBytes.length
    val encoded = Unpooled.buffer(4 + totalLen)
    try {
      encoded.writeInt(totalLen)      // 长度前缀（匹配 MessageEncoder 格式）
      encoded.writeInt(msg.getCommand) // cmdType
      encoded.writeBytes(bodyBytes)    // body

      // 写入所有通道（只 write 不 flush，批量冲刷）
      channels.values.foreach { ctx =>
        ctx.write(encoded.retainedSlice())
      }
      // 批量 flush 所有通道
      channels.values.foreach(_.flush())
    } finally {
      encoded.release()  // 释放原始 buffer（各通道的 retainedSlice 依然有效）
    }
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