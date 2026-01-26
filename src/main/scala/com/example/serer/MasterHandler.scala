package com.example.serer

import com.example.commands.{IPlayerCommand, ISystemCommand}
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

  override def channelRead0(ctx: ChannelHandlerContext, message: Message): Unit = {
    val channel = ctx.channel()
    val cmdHex = message.getCommand.toHexString
    val cmd = cmdHex.reverse.padTo(4, '0').reverse.mkString
    println(s"Received message with command: 0x$cmd from channel: $channel")
    val clsName = s"com.example.commands.Command${cmd.take(2)}$$"
    val methodName = s"handler${cmd.drop(2)}"

    val cls = getObjectFromCache(clsName)

    cls match {
      case _: IPlayerCommand =>
        val playerId = ctx.channel().attr(ATTR_PLAYER_ID).get()
        invoke(clsName, methodName, Array(playerId, message), Array[Class[_]](classOf[String], classOf[Message]))
      case _: ISystemCommand =>
        invoke(clsName, methodName, Array(ctx, message), Array[Class[_]](classOf[ChannelHandlerContext], classOf[Message]))
      case _ =>
        println(s"Unknown command handler type for command: 0x$cmd")
    }
  }

  private def invoke(clsName: String, methodName: String, args: Array[Object], clsSet: Array[Class[_]]): Any = {
    val method = getMethodFromCache(clsName, methodName, clsSet: _*)
    val obj = getObjectFromCache(clsName)
    method.invoke(obj, args: _*)
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