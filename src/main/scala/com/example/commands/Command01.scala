package com.example.commands

import com.example.actor.Player
import com.example.holder.PlayerHolder
import com.example.message.{Message, MessageBody}
import com.example.serer.MasterHandler
import io.netty.channel.ChannelHandlerContext

object Command01 extends ISystemCommand {
  def handler01(ctx: ChannelHandlerContext, message: Message): Unit = {
    val playerId = message.getString("playerId")
    ctx.channel().attr(MasterHandler.ATTR_PLAYER_ID).set(playerId)
    PlayerHolder.addPlayer(new Player(playerId))
    ctx.write(message.response(MessageBody("result" -> "success", "playerId" -> playerId)))
    ctx.write(new Message(0x0101, MessageBody("welcome" -> s"Welcome Player $playerId!")))
    println(s"Player $playerId has connected.")
  }
}
