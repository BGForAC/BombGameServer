package com.example.props

import com.example.actor.Player
import com.example.commands.CmdType
import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels
import com.example.tick.ITick

import scala.collection.mutable

/**
 * PropsEffectSystem 道具效果系统
 * 管理所有活跃的道具效果，包括定时器过期
 *
 * 对应客户端 PropsStatus.UseProps() → Timer → PropsEnable/PropsDisable 的流程
 */
object PropsEffectSystem extends ITick {

  /**
   * 活跃效果记录
   * key: "playerId_propsId" (与客户端 GetTimerKey 一致)
   */
  private case class ActiveEffect(
    playerId: String,
    config: PropsConfig,
    expireTime: Long   // 到期时间戳（毫秒），-1 表示永不过期
  )

  /** 玩家ID → 活跃效果列表 */
  private val activeEffects: mutable.Map[String, mutable.ListBuffer[ActiveEffect]] = mutable.Map.empty

  /**
   * 为玩家应用道具效果
   * @param playerId 玩家ID
   * @param config 道具配置
   */
  def applyEffect(playerId: String, config: PropsConfig): Unit = {
    val player = PlayerHolder.getPlayer(playerId)
    if (player == null) {
      println(s"[PropsEffectSystem] 玩家[$playerId]不存在，无法应用道具效果")
      return
    }

    // 同类型道具互斥：先移除已有的同类型效果
    removeEffectByType(playerId, config.propsType)

    // 应用效果到玩家属性
    applyEffectToAttr(player, config, isEnable = true)

    // 记录活跃效果
    val effect = if (config.validTime <= 0) {
      // validTime <= 0: 永久效果，不过期
      ActiveEffect(playerId, config, -1L)
    } else {
      // validTime > 0: 限时效果，计算到期时间
      ActiveEffect(playerId, config, System.currentTimeMillis() + (config.validTime * 1000).toLong)
    }
    activeEffects.getOrElseUpdate(playerId, mutable.ListBuffer.empty) += effect

    // 广播道具效果启用
    broadcastEffect(playerId, config, isEnabled = true)

    println(s"[PropsEffectSystem] 道具[${config.id}]效果已应用到玩家[$playerId]，类型=${config.propsType}，有效时长=${config.validTime}s")
  }

  /**
   * 移除玩家指定类型的道具效果
   */
  private def removeEffectByType(playerId: String, propsType: String): Unit = {
    activeEffects.get(playerId) match {
      case Some(effects) =>
        val toRemove = effects.filter(_.config.propsType == propsType)
        toRemove.foreach { effect =>
          val player = PlayerHolder.getPlayer(playerId)
          if (player != null) {
            applyEffectToAttr(player, effect.config, isEnable = false)
            broadcastEffect(playerId, effect.config, isEnabled = false)
          }
        }
        effects --= toRemove
        if (effects.isEmpty) activeEffects -= playerId
      case None =>
    }
  }

  /**
   * 强制移除玩家的所有道具效果（用于玩家死亡等场景）
   */
  def removeAllEffects(playerId: String): Unit = {
    activeEffects.get(playerId) match {
      case Some(effects) =>
        val player = PlayerHolder.getPlayer(playerId)
        effects.foreach { effect =>
          if (player != null) {
            applyEffectToAttr(player, effect.config, isEnable = false)
          }
          broadcastEffect(playerId, effect.config, isEnabled = false)
        }
        activeEffects -= playerId
        println(s"[PropsEffectSystem] 已移除玩家[$playerId]的所有道具效果")
      case None =>
    }
  }

  /**
   * Tick 更新：检查限时效果是否过期
   */
  override def tick(tickIndex: Long): Unit = {
    val now = System.currentTimeMillis()
    val toRemove = mutable.ListBuffer.empty[(String, ActiveEffect)]

    activeEffects.foreach { case (playerId, effects) =>
      effects.foreach { effect =>
        if (effect.expireTime > 0 && now >= effect.expireTime) {
          toRemove += ((playerId, effect))
        }
      }
    }

    toRemove.foreach { case (playerId, effect) =>
      activeEffects.get(playerId).foreach { effects =>
        effects -= effect
        if (effects.isEmpty) activeEffects -= playerId
      }
      val player = PlayerHolder.getPlayer(playerId)
      if (player != null) {
        applyEffectToAttr(player, effect.config, isEnable = false)
      }
      broadcastEffect(playerId, effect.config, isEnabled = false)
      println(s"[PropsEffectSystem] 道具[${effect.config.id}]效果到期，已从玩家[$playerId]移除")
    }
  }

  /**
   * 将道具效果应用/移除到玩家属性
   * 与客户端 BaseState.ApplyPropsEffect 逻辑一一对应
   */
  private def applyEffectToAttr(player: Player, config: PropsConfig, isEnable: Boolean): Unit = {
    val sign = if (isEnable) 1f else -1f

    // 基础属性 (Addition)
    player.attr.maxHp = (player.attr.maxHp + config.maxHpAddition * sign).toInt
    player.attr.hp = Math.min(player.attr.hp + (config.maxHpAddition * sign).toInt, player.attr.maxHp)

    // 生命恢复 (Addition)
    // 注：服务端暂不实现 hpRegen tick 逻辑，仅记录属性变化

    // 移动速度 (Multiply)
    if (config.speedMultiply != 0) {
      player.attr.speed = if (isEnable)
        (player.attr.speed * (1 + config.speedMultiply)).toInt
      else
        (player.attr.speed / (1 + config.speedMultiply)).toInt
    }

    // 体力 (Addition)
    player.attr.maxStamina = (player.attr.maxStamina + config.maxStaminaAddition).toInt
    player.attr.staminaDrainRate = (player.attr.staminaDrainRate + config.staminaDrainRateAddition).toInt
    player.attr.staminaRegenRate = (player.attr.staminaRegenRate + config.staminaRegenRateAddition).toInt

    // 速度倍率 (Multiply)
    if (config.speedMultiplierMultiply != 0) {
      player.attr.speedMultiplier = if (isEnable)
        player.attr.speedMultiplier * (1 + config.speedMultiplierMultiply)
      else
        player.attr.speedMultiplier / (1 + config.speedMultiplierMultiply)
    }

    // 炸弹属性 (Addition)
    player.attr.maxBombCount = (player.attr.maxBombCount + config.maxBombCountAddition * sign).toInt
    player.attr.bombDamage = (player.attr.bombDamage + config.bombDamageAddition * sign).toInt
    player.attr.bombRadius = player.attr.bombRadius + config.bombRadiusAddition * sign

    // 炸弹属性 (Subtract)
    player.attr.bombFuseTime = Math.max(0.5f, player.attr.bombFuseTime - config.bombFuseTimeSubtract * sign)

    // 炸弹属性 (Divide)
    if (config.bombCooldownDivide != 0) {
      player.attr.bombCooldown = if (isEnable)
        player.attr.bombCooldown / (1 + config.bombCooldownDivide)
      else
        player.attr.bombCooldown * (1 + config.bombCooldownDivide)
    }
    if (config.bombRecoveryTimeDivide != 0) {
      player.attr.bombRecoveryTime = if (isEnable)
        player.attr.bombRecoveryTime / (1 + config.bombRecoveryTimeDivide)
      else
        player.attr.bombRecoveryTime * (1 + config.bombRecoveryTimeDivide)
    }
  }

  /**
   * 广播道具效果启用/禁用事件
   */
  private def broadcastEffect(playerId: String, config: PropsConfig, isEnabled: Boolean): Unit = {
    val player = PlayerHolder.getPlayer(playerId)
    if (player == null) return

    val scene = SceneHolder.getScene(player.movement.sceneId)
    if (scene == null) return

    val cmd = if (isEnabled) CmdType.PROP_EFFECT_ENABLE else CmdType.PROP_EFFECT_DISABLE
    val msg = Message(cmd, MessageBody(
      "playerId" -> playerId,
      "propsId" -> config.id,
      "propsType" -> config.propsType
    ))

    scene.players.values.foreach { p =>
      PlayerChannels.send(p.id, msg)
    }
  }
}
