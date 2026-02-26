package com.example.holder

/**
 * 退出类型枚举类
 * 用于定义不同的退出场景类型
 */
object ExitTypeEnum {
  val LEAVE = 1    // 用户主动离开
  val KICK = 2     // 用户被踢出
  val DISBAND = 3  // 群组/团队解散

  def getExitTypeStr(exitType: Int): String = {
    exitType match {
      case LEAVE => "主动离开"
      case KICK => "被踢出"
      case DISBAND => "解散"
      case _ => "未知"
    }
  }
}
