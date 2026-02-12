package com.example.commands

import com.example.message.{Message, MessageBody}
import com.example.serer.{MasterHandler, PlayerChannels}
import com.example.service.PlayerService
import io.netty.channel.ChannelHandlerContext

object Command01 extends ISystemCommand {
  def handler01(ctx: ChannelHandlerContext, message: Message): Unit = {
    val uname = message.getString("username")
    val pwd = message.getString("password")
    if (uname.isEmpty || pwd.isEmpty) {
      ctx.writeAndFlush(message.response(MessageBody("result" -> "fail", "reason" -> "用户名或密码不能为空")))
      return
    }
    val player = PlayerService.getPlayer(uname, pwd)
    val playerId = player.id
    ctx.channel().attr(MasterHandler.ATTR_PLAYER_ID).set(playerId)
    PlayerChannels.addChannel(playerId, ctx)
    ctx.writeAndFlush(message.response(MessageBody("result" -> "success", "playerId" -> playerId)))
    println(s"Player $playerId has connected.")
  }
}
