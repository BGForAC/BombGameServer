package com.example.commands

import com.example.holder.PlayerHolder
import com.example.message.{Message, MessageBody}
import com.example.serer.{MasterHandler, PlayerChannels}
import com.example.service.PlayerService
import io.netty.channel.ChannelHandlerContext

/**
 * Command01 是一个系统命令实现类，继承自 ISystemCommand
 * 用于处理用户登录请求
 */
object Command01 extends ISystemCommand {
  /**
   * 处理用户登录请求的具体实现
   * @param ctx Netty通道上下文，用于网络通信
   * @param message 包含登录信息的消息对象
   */
  def handler01(ctx: ChannelHandlerContext, message: Message): Unit = {
    // 从消息中获取用户名和密码
    val uname = message.getString("username")
    val pwd = message.getString("password")
    // 验证用户名和密码是否为空
    if (uname.isEmpty || pwd.isEmpty) {
      // 如果为空，返回失败响应
      ctx.writeAndFlush(message.response(MessageBody("result" -> "fail", "reason" -> "用户名或密码不能为空")))
      return
    }
    // 通过PlayerService验证用户名和密码，获取玩家信息
    val player = PlayerService.getPlayer(uname, pwd)
    val playerId = player.id
    // 将玩家ID绑定到通道属性中
    ctx.channel().attr(MasterHandler.ATTR_PLAYER_ID).set(playerId)
    // 将当前时间戳绑定到通道属性中，用于心跳检测
    ctx.channel().attr(MasterHandler.ATTR_HEARTBEAT).set(System.currentTimeMillis() + 10000)
    // 将通道添加到PlayerChannels中管理
    PlayerChannels.addChannel(playerId, ctx)
    // 返回成功响应，包含玩家ID
    ctx.writeAndFlush(message.response(MessageBody("result" -> "success", "playerId" -> playerId)))
    // 打印连接信息
    println(s"Player $playerId has connected.")
  }

  /**
   * 心跳
   * @param ctx Netty通道上下文，用于网络通信
   * @param message 包含心跳信息的消息对象
   */
  def handler02(ctx: ChannelHandlerContext, message: Message): Unit ={
    // 更新当前时间戳
    ctx.channel().attr(MasterHandler.ATTR_HEARTBEAT).set(System.currentTimeMillis())
    ctx.writeAndFlush(message.response(MessageBody("result" -> "success")))
    //println(s"[Heartbeat] 已接受到玩家${ctx.channel().attr(MasterHandler.ATTR_PLAYER_ID).get()}的心跳包.")
  }

  /**
   * 用于处理断开连接的事件
   * @param ctx Netty通道上下文，用于网络通信
   * @param message 包含断开连接信息的消息对象
   */
  def handler03(ctx: ChannelHandlerContext, message: Message): Unit ={
    val playerId = ctx.channel().attr(MasterHandler.ATTR_PLAYER_ID).get()
    PlayerHolder.getPlayer(playerId).onDisConnect()
    println(s"Player $playerId has disconnected.")
  }




}
