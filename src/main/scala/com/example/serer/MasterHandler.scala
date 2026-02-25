package com.example.serer

import com.example.commands.{CmdType, IPlayerCommand, ISystemCommand}
import com.example.exception.BusinessException
import com.example.holder.PlayerHolder
import com.example.message.Message
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.util.AttributeKey

import scala.collection.mutable

@Sharable
object MasterHandler extends SimpleChannelInboundHandler[Message] {
  final val ATTR_PLAYER_ID = AttributeKey.valueOf[String]("playerId")

  private val clsCache = mutable.Map[String, Object]()
  private val methodCache = mutable.Map[(String, String), java.lang.reflect.Method]()

//  private val logFilterCommands: Set[String] = Set(CmdType.MOVE, CmdType.HEARTBEAT).map(toHexString)

  def toHexString(cmd: Int): String = f"$cmd%04X"

  override def channelRead0(ctx: ChannelHandlerContext, message: Message): Unit = {
    val cmd = toHexString(message.getCommand)
//    val channel = ctx.channel()
//    if (!logFilterCommands.contains(cmd)) println(s"Received message with command: 0x$cmd from channel: $channel")
    val clsName = s"com.example.commands.Command${cmd.take(2)}$$"
    val methodName = s"handler${cmd.drop(2)}"

    val cls = getObjectFromCache(clsName)

    try {
      cls match {
        case _: IPlayerCommand =>
          val playerId = ctx.channel().attr(ATTR_PLAYER_ID).get()
          invoke(clsName, methodName, playerId, message)
        case _: ISystemCommand =>
          invoke(clsName, methodName, ctx, message)
        case _ =>
          throw new RuntimeException(s"Unknown command handler type for command: 0x$cmd")
      }
    } catch {
      case t: Throwable =>
        t.getCause match {
          case e: BusinessException =>
            ctx.writeAndFlush(e.toMessage)
          case other =>
            other.printStackTrace()
        }
      case e: Exception =>
        e.printStackTrace()
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)
    val ch = ctx.channel()
    val playerId = ch.attr(ATTR_PLAYER_ID).get()
    if (playerId != null) {
      val player = PlayerHolder.getPlayer(playerId)
      if (player != null) {
        println(s"Player ${player.id} has disconnected.")
        player.onDisConnect()
      }
    }
  }

  private def invoke(clsName: String, methodName: String, args: Object*): Any = {
    val method = getMethodFromCache(clsName, methodName, args.map(parseClass): _*)
    val obj = getObjectFromCache(clsName)
    method.invoke(obj, args: _*)
  }

  private def parseClass(obj: Object): Class[_] = {
    obj match {
      case _: ChannelHandlerContext => classOf[ChannelHandlerContext]
      case _ => obj.getClass
    }
  }

  private def getObjectFromCache(className: String): Object = {
    if (clsCache.contains(className)) {
      return clsCache(className)
    }
    val cls = Class.forName(className)
    val obj: Object = cls.getField("MODULE$").get(null)
    clsCache.getOrElseUpdate(className, obj)
  }

  private def getMethodFromCache(className: String, methodName: String, clsList: Class[_]*): java.lang.reflect.Method = {
    methodCache.getOrElseUpdate((className, methodName), {
      val obj = getObjectFromCache(className)
      obj.getClass.getMethod(methodName, clsList: _*)
    })
  }
}