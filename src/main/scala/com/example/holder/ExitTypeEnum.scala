package com.example.holder

/**
 * 退出类型枚举类
 * 用于定义不同的退出场景类型
 */
object ExitTypeEnum {
  val LEAVE = 1    // 用户主动离开
  val KICK = 2     // 用户被踢出
  val DISBAND = 3  // 群组/团队解散

  /**
   * 根据退出类型获取对应的描述字符串
   * @param exitType 退出类型的整数值
   * @return 返回退出类型的描述字符串
   */
  def getExitTypeStr(exitType: Int): String = {
    exitType match {
      case LEAVE => "主动离开"    // 匹配到用户主动离开类型
      case KICK => "被踢出"       // 匹配到用户被踢出类型
      case DISBAND => "解散"      // 匹配到群组/团队解散类型
      case _ => "未知"           // 处理其他未知类型
    }
  }
}
