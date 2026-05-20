package com.example.actor

import com.example.exception.ThrowBusinessException
import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.message.MessageBody
import com.example.scene.Scene
import com.example.serer.PlayerChannels

/**
 * Player类，代表游戏中的一个玩家角色
 * @param pid 玩家的唯一标识符
 */
class Player(pid: String) extends Actor(pid) {
  // 玩家用户名
  var uname: String = _

  // 玩家职业
  var career: String = _

  // 控制配置
  var controlConfig: Int = _

  // 是否离线状态
  private var offLine: Boolean = false

  // 当前拥有的炸弹数量
  private var bombNum: Int = 0

  // 上次放置炸弹的时间戳
  private var lastPutBombTime: Long = 0

  // 上次恢复炸弹的时间戳
  private var lastRecoverBombTime: Long = 0

  // 将玩家添加到玩家持有者中
  PlayerHolder.addPlayer(this)
  /**
   * 处理玩家断开连接的方法
   * 当玩家断开连接时，将玩家标记为离线状态，并从场景中移除
   * 同时从玩家持有者和玩家通道中移除
   */
  def onDisConnect(): Unit = {
    // 设置玩家为离线状态
    offLine = true
    // 现在的设计是玩家断线了直接删了，后续可以改成离线状态，等玩家重连了再把数据加载回来, 倘若玩家长时间不重连了，才把数据删了
    // 获取玩家当前所在场景
    val curScene = SceneHolder.getScene(movement.sceneId)
    // 如果场景存在，则让玩家离开场景
    if (curScene != null) {
      curScene.onExit(this)
    }
    // 从玩家持有者中移除玩家
    PlayerHolder.removePlayer(pid)
    // 从玩家通道中移除玩家
    PlayerChannels.removeChannel(pid)
  }

  /**
   * 每个游戏周期的处理方法
   * @param tickIdx 当前周期的索引
   */
  override def tick(tickIdx: Long): Unit = {
    // 调用父类的tick方法
    super.tick(tickIdx)
    // 如果玩家离线，则直接返回，不做任何处理
    if (offLine) {
      return
    }
    // 如果玩家有炸弹且距离上次恢复炸弹的时间超过恢复间隔，则减少炸弹数量
    if (bombNum > 0 && System.currentTimeMillis() - lastRecoverBombTime >= attr.BombRecoveryTime) {
      // 减少炸弹数量
      bombNum = bombNum - 1
      // 更新上次恢复炸弹的时间戳
      lastRecoverBombTime = System.currentTimeMillis()
    }
  }


  /**
   * 获取玩家基本信息
   * @return 包含玩家基本信息的序列，如ID、用户名、职业和控制配置
   */
  def baseInfo: MessageBody = {
    // 返回玩家基本信息，包括移动属性和额外属性
    // 注释掉的代码是原来的实现方式，现在改为显式列出所有属性
    //    movement.info ++ attr.info
    val info = movement.info
    info += "uname" -> uname
    info += "career" -> career
    info += "controlConfig" -> controlConfig
    info += "id" -> id
    //println(info.toJsonString)
    info
  }

  /**
   * 将玩家基本信息转换为字符串格式
   * @param extraInfo 额外的信息序列，默认为空
   * @return 格式化后的玩家信息字符串
   */
  def baseInfoStr(extraInfo: MessageBody = MessageBody()): MessageBody = {
    // 将额外信息和基本信息合并，然后转换为字符串
    MessageBody.addMessageBody(baseInfo, extraInfo)
  }

  /**
   * 设置玩家离开场景
   * @param scene 离开的场景
   */
  override def setOutScene(scene: Scene): Unit = {
    // 调用父类的setOutScene方法
    super.setOutScene(scene)
    // 清空玩家职业
    career = null
    // 重置控制配置
    controlConfig = 0
  }

  /**
   * 放置炸弹的方法
   * 检查玩家是否可以放置炸弹（数量限制和冷却时间）
   * 如果可以，则在当前场景中放置一个炸弹，并更新相关状态
   */
  def putBomb(): Bomb = {
    // 检查炸弹数量是否达到上限
    if (bombNum >= attr.MaxBombCount) ThrowBusinessException(s"你放的炸弹太多了，等炸弹爆炸了再放吧")
//    println(s"玩家[$id]放了一个炸弹 当前炸弹数量[$bombNum] 总共炸弹数量[${attr.bombFuseTime}]
    //    当前时间[${System.currentTimeMillis()}]
    //    上次放炸弹时间[$lastPutBombTime] 炸弹冷却时间[${attr.Cooldown}]")
    // 检查是否在炸弹冷却时间内
    if (System.currentTimeMillis() - lastPutBombTime < attr.Cooldown) ThrowBusinessException(s"你放炸弹太快了，等会再放吧")

    var bomb = Bomb(this)
    // 在当前场景中放置炸弹
    SceneHolder.enterScene(movement.sceneId, bomb)

    // 更新炸弹数量和时间戳
    bombNum = bombNum + 1
    lastPutBombTime = System.currentTimeMillis()
    if (bombNum == 1) {
      // 只有当炸弹从满到不满的时候才开始计算恢复炸弹的时间
      lastRecoverBombTime = System.currentTimeMillis()
    }
    bomb
  }
}