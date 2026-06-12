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

  // 玩家密码（仅在登录时使用，不随游戏状态同步）
  var password: String = _

  // 玩家职业
  var career: String = _

  // 控制配置
  var controlConfig: Int = _

  // 是否离线状态（非 private，顶号登录时由 Command01 重置为 false）
  var offLine: Boolean = false

  // 当前拥有的炸弹数量（放置+1，恢复-1，供 Scene 读取）
  var bombNum: Int = 0

  // 上次放置炸弹的时间戳
  private var lastPutBombTime: Long = 0

  // 上次恢复炸弹的时间戳
  private var lastRecoverBombTime: Long = 0

  // ===== 体力系统（服务端权威） =====
  var stamina: Float = 0f       // 当前体力值
  var isSprinting: Boolean = false  // 是否正在冲刺（由客户端 MOVE 消息上报）
  var isStaminaEmpty: Boolean = false  // 体力是否耗尽
  private var lastStaminaTickTime: Long = 0L  // 上次体力 tick 的时间戳

  // ===== 速度（服务端权威，由体力+冲刺状态决定） =====
  var currentSpeed: Float = 0f  // 当前实际速度（= baseSpeed 或 baseSpeed * speedMultiplier）

  // 将玩家添加到玩家持有者中
  PlayerHolder.addPlayer(this)
  /**
   * 处理玩家断开连接的方法
   * 当玩家断开连接时，将玩家标记为离线状态，并从场景中移除
   * 同时从玩家持有者和玩家通道中移除
   */
  def onDisConnect(): Unit = {
    // 设置玩家为离线状态
    // 顶号登录保护：只有当玩家没有任何活跃通道时才标记离线
    // 旧通道异步断开时，新通道可能已建立（hasChannel 为 true），此时不应标记离线
    offLine = !PlayerChannels.hasChannel(pid)
    // 现在的设计是玩家断线了直接删了，后续可以改成离线状态，等玩家重连了再把数据加载回来, 倘若玩家长时间不重连了，才把数据删了
    // 获取玩家当前所在场景
    val curScene = SceneHolder.getScene(movement.sceneId)
    // 如果场景存在，则让玩家离开场景
    if (curScene != null) {
      curScene.onExit(this)
    }
    // 只有当玩家没有任何活跃通道时才从 PlayerHolder 移除
    // 避免顶号登录时新通道已建立，但旧通道的断开事件误删 PlayerHolder 中的玩家数据
    if (!PlayerChannels.hasChannel(pid)) {
      PlayerHolder.removePlayer(pid)
      PlayerChannels.removeChannel(pid)
    }
  }

  /**
   * 每个游戏周期的处理方法（服务端权威的状态推进）
   * @param tickIdx 当前周期的索引
   */
  override def tick(tickIdx: Long): Unit = {
    // 调用父类的tick方法
    super.tick(tickIdx)
    // 如果玩家离线，则直接返回，不做任何处理
    if (offLine) {
      return
    }

    val now = System.currentTimeMillis()

    // 初始化时间戳（首次 tick）
    if (lastStaminaTickTime == 0L) {
      lastStaminaTickTime = now
      stamina = attr.maxStamina.toFloat  // 初始满体力
      currentSpeed = attr.speed.toFloat
    }

    // ===== 体力系统 =====
    val deltaSeconds = (now - lastStaminaTickTime) / 1000.0f
    if (deltaSeconds > 0f && deltaSeconds < 1.0f) {  // 防止异常大 delta
      if (isSprinting && stamina > 0 && !isStaminaEmpty) {
        // 冲刺：消耗体力，速度 = baseSpeed * speedMultiplier
        stamina = Math.max(0f, stamina - attr.staminaDrainRate * deltaSeconds)
        currentSpeed = attr.speed.toFloat * attr.speedMultiplier
        if (stamina <= 0f) {
          isStaminaEmpty = true
          currentSpeed = attr.speed.toFloat
        }
      } else {
        // 未冲刺：恢复体力，速度 = baseSpeed
        stamina = Math.min(attr.maxStamina.toFloat, stamina + attr.staminaRegenRate * deltaSeconds)
        currentSpeed = attr.speed.toFloat
        if (isStaminaEmpty && stamina > 20f) {
          isStaminaEmpty = false
        }
      }
    }
    lastStaminaTickTime = now

    // ===== 炸弹恢复（每 RecoveryTime 毫秒恢复一个炸弹槽位） =====
    if (bombNum > 0 && now - lastRecoverBombTime >= attr.BombRecoveryTime) {
      bombNum = bombNum - 1
      lastRecoverBombTime = now
    }
  }

  /**
   * 获取炸弹放置冷却剩余时间（毫秒），供 PLAYER_SYNC 同步到客户端
   */
  def bombCooldownRemaining: Int = {
    if (lastPutBombTime == 0L) return 0  // 从未放置过炸弹，无冷却
    val elapsed = System.currentTimeMillis() - lastPutBombTime
    Math.max(0, attr.Cooldown - elapsed.toInt)
  }

  /**
   * 获取炸弹恢复剩余时间（毫秒），供 PLAYER_SYNC 同步到客户端
   */
  def bombRecoveryRemaining: Int = {
    if (bombNum <= 0) return 0  // 无炸弹待恢复
    val elapsed = System.currentTimeMillis() - lastRecoverBombTime
    Math.max(0, attr.BombRecoveryTime - elapsed.toInt)
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

    //println(s"[Player.putBomb] 玩家[$id]创建炸弹[${bomb.id}]: " +
    //  s"bombNum=$bombNum/${attr.MaxBombCount}, " +
    //  s"fuseTime=${attr.FuseTime}ms, " +
    //  s"cooldown=${attr.Cooldown}ms, " +
    //  s"radius=${attr.BombRadius}, " +
    //  s"damage=${attr.BombDamage}, " +
    //  s"sceneId=${movement.sceneId}")

    bomb
  }
}