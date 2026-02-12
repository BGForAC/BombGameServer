package com.example.serer

import com.example.message.Message
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
      case None => println(s"Channel for player $playerId not found.")
    }
  }

}
