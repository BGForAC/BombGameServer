package com.example.commands

import com.example.message.Message

/**
 * Command06 HP_CHANGE 命令处理器
 * 实现 IPlayerCommand，处理服务端→客户端单向广播（服务端权威）
 * 正常情况下客户端不应发送此命令，但实现接口防止 dispatch 异常
 */
object Command06 extends IPlayerCommand {

  def handler01(playerId: String, message: Message): Unit = {
    // HP_CHANGE 是服务端→客户端单向广播，客户端不应发送此命令
    //println(s"[Command06] 收到意外的 HP_CHANGE 请求来自 playerId=$playerId，已忽略")
  }
}
