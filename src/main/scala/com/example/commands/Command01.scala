package com.example.commands

import com.example.actor.Player
import com.example.holder.PlayerHolder
import com.example.message.{Message, MessageBody}
import com.example.serer.{MasterHandler, PlayerChannels}
import io.netty.channel.ChannelHandlerContext

object Command01 extends ISystemCommand {
  def handler01(ctx: ChannelHandlerContext, message: Message): Unit = {
    val playerId = message.getString("playerId")
    ctx.channel().attr(MasterHandler.ATTR_PLAYER_ID).set(playerId)
    PlayerHolder.addPlayer(new Player(playerId))
    PlayerChannels.addChannel(playerId, ctx)
    ctx.writeAndFlush(message.response(MessageBody("result" -> "success", "playerId" -> playerId)))
    println(s"Player $playerId has connected.")
  }
}
