package com.example.serer

import com.example.commands.{IPlayerCommand, ISystemCommand}
import com.example.exception.BusinessException
import com.example.holder.PlayerHolder
import com.example.message.Message
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.util.AttributeKey

import scala.collection.mutable

/**
 * MasterHandler是一个可共享的Netty处理器，用于处理网络消息
 * 它继承自SimpleChannelInboundHandler，专门处理Message类型的消息
 */
@Sharable
object MasterHandler extends SimpleChannelInboundHandler[Message] {
  // 在管道中定义玩家ID的属性键
  final val ATTR_PLAYER_ID = AttributeKey.valueOf[String]("playerId")

  // 缓存类和方法的映射，用于提高性能
  private val clsCache = mutable.Map[String, Object]()
  private val methodCache = mutable.Map[(String, String), java.lang.reflect.Method]()

  // 需要记录日志的命令集合
//  private val logFilterCommands: Set[String] = Set(CmdType.MOVE, CmdType.HEARTBEAT).map(toHexString)

  /**
   * 将整数命令转换为4位十六进制字符串
   * @param cmd 整数命令
   * @return 4位十六进制字符串
   */
  def toHexString(cmd: Int): String = f"$cmd%04X"

  /**
   * 读取并处理消息的核心方法
   * @param ctx 通道处理上下文
   * @param message 收到的消息
   */
  override def channelRead0(ctx: ChannelHandlerContext, message: Message): Unit = {
    // 将命令转换为十六进制字符串
    val cmd = toHexString(message.getCommand)
    // 获取通道
//    val channel = ctx.channel()
    // 如果命令不在过滤列表中，则打印接收到的消息
//    if (!logFilterCommands.contains(cmd)) println(s"Received message with command: 0x$cmd from channel: $channel")
    // 根据命令构建类名和方法名
    val clsName = s"com.example.commands.Command${cmd.take(2)}$$"
    val methodName = s"handler${cmd.drop(2)}"

    // 从缓存中获取类对象
    val cls = getObjectFromCache(clsName)

    try {
      // 根据命令类型进行不同的处理
      cls match {
        case _: IPlayerCommand =>
          // 玩家命令：获取玩家ID并调用相应方法
          val playerId = ctx.channel().attr(ATTR_PLAYER_ID).get()
          invoke(clsName, methodName, playerId, message)
        case _: ISystemCommand =>
          // 系统命令：直接调用相应方法
          invoke(clsName, methodName, ctx, message)
        case _ =>
          // 未知命令类型
          throw new RuntimeException(s"Unknown command handler type for command: 0x$cmd")
      }
    } catch {
      // 处理业务异常
      case t: Throwable =>
        t.getCause match {
          case e: BusinessException =>
            ctx.writeAndFlush(e.toMessage)
          case other =>
            other.printStackTrace()
        }
      // 处理其他异常
      case e: Exception =>
        e.printStackTrace()
    }
  }

  /**
   * 通道不活跃时的处理方法（客户端断开连接时调用）
   * @param ctx 通道处理上下文
   */
  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)
    val ch = ctx.channel()
    // 通过反射调用 onDisConnect 方法
    val playerId = ch.attr(ATTR_PLAYER_ID).get()
    if (playerId != null) {
      val player = PlayerHolder.getPlayer(playerId)
      if (player != null) {
        println(s"Player ${player.id} has disconnected.")
        player.onDisConnect()
      }
    }
  }

  /**
   * 通过反射调用方法
   * @param clsName 类名
   * @param methodName 方法名
   * @param args 参数列表
   * @return 方法调用结果
   */
  private def invoke(clsName: String, methodName: String, args: Object*): Any = {
    val method = getMethodFromCache(clsName, methodName, args.map(parseClass): _*)
    val obj = getObjectFromCache(clsName)
    method.invoke(obj, args: _*)
  }

  /**
   * 解析参数的类型
   * @param obj 参数对象
   * @return 参数的Class对象
   */
  private def parseClass(obj: Object): Class[_] = {
    obj match {
      case _: ChannelHandlerContext => classOf[ChannelHandlerContext]
      case _ => obj.getClass
    }
  }

  /**
   * 从缓存获取类对象，如果不存在则创建并缓存
   * @param className 类名
   * @return 类对象
   */
  private def getObjectFromCache(className: String): Object = {
    if (clsCache.contains(className)) {
      return clsCache(className)
    }
    val cls = Class.forName(className)
    val obj: Object = cls.getField("MODULE$").get(null)
    clsCache.getOrElseUpdate(className, obj)
  }

  /**
   * 从缓存获取方法对象，如果不存在则创建并缓存
   * @param className 类名
   * @param methodName 方法名
   * @param clsList 参数类型列表
   * @return 方法对象
   */
  private def getMethodFromCache(className: String, methodName: String, clsList: Class[_]*): java.lang.reflect.Method = {
    methodCache.getOrElseUpdate((className, methodName), {
      val obj = getObjectFromCache(className)
      obj.getClass.getMethod(methodName, clsList: _*)
    })
  }
}