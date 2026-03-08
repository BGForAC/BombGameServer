package com.example.commands

/**
 * 命令类型对象，定义了系统中使用的各种命令常量
 * 这些常量用于标识不同类型的消息和操作
 */
object CmdType {

  // 基础系统命令
  val LOGIN = 0x0101      // 登录命令
  val HEARTBEAT = 0x0102  // 心跳命令，用于保持连接
  val INVALID = 0x01FF    // 无效命令，用于错误处理
  val ALERT = 0x01FE      // 警告命令，用于显示警告信息
  val INFO = 0x01FD       // 信息命令，用于显示一般信息



  // 场景相关命令
  val ENTER_SCENE = 0x0201  // 进入场景命令
  val EXIT_SCENE = 0x0202   // 退出场景命令



  // 移动相关命令
  val MOVE = 0x0301         // 移动命令



  // 基础游戏相关命令
  val BASE_GAME_MATCH = 0x0401              // 开始匹配游戏命令
  val BASE_GAME_CANCEL_MATCH = 0x0402       // 取消匹配游戏命令
  val ENTER_BASE_GAME = 0x0403              // 进入基础游戏命令
  val BASE_GAME_CREATE_ROOM = 0x0404        // 创建房间命令
  val BASE_GAME_JOIN_ROOM = 0x0405          // 加入房间命令
  val BASE_GAME_LEAVE_ROOM = 0x0406        // 离开房间命令
  val BASE_GAME_CURRENT_ROOM_CHANGE = 0x0407 // 当前房间变化通知
  val BASE_GAME_REQ_ROOM_INFO = 0x0408      // 请求房间信息命令
  val BASE_GAME_KICK_PLAYER = 0x0409        // 踢出玩家命令
  val BASE_GAME_REMOVE_ROOM = 0x040A        // 移除房间命令
  val BASE_GAME_LEADER_CHANGE = 0x040B      // 房主变更通知
  val BASE_GAME_READY = 0x040C              // 准备游戏命令
  val BASE_GAME_CHANGE_CAREER = 0x040D      // 更改职业命令

//BaseGameStartMatch

  // 炸弹相关命令
  val PUT_BOMB = 0x0501      // 放置炸弹命令



  // 生命值相关命令
  val HP_CHANGE = 0x0601     // 生命值变化命令
}
