package com.example.serer

import com.example.commands.CmdType
import com.example.exception.ExceptionType
import com.example.message.{ErrorMessage, Message, MessageBody}
import io.netty.channel.ChannelHandlerContext

object PlayerChannels {
  private val channels = scala.collection.mutable.Map[String, ChannelHandlerContext]()

  def addChannel(playerId: String, ctx: ChannelHandlerContext): Unit = {
    channels += (playerId -> ctx)
  }

  def removeChannel(playerId: String): Unit = {
    channels -= playerId
  }

  def send(playerId: String, msg: Message): Unit = {
    channels.get(playerId) match {
      case Some(ctx) => ctx.writeAndFlush(msg)
      case None => println(s"玩家 $playerId 未找到.")
      case _ => println("未知的message类型")
    }


  }

  def sendError(playerId: String, msg: String, errTyp: Int = ExceptionType.DEFAULT): Unit = {
    send(playerId, ErrorMessage(msg, errTyp))
  }

  def sendToAll(msg: Message): Unit = {
    channels.keys.foreach(send(_, msg))
  }

  def alert(playerId: String, msg: String): Unit = {
    send(playerId, Message(CmdType.ALERT, MessageBody("msg" -> msg)))
  }
}