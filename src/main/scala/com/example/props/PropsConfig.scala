package com.example.props

import com.example.message.{Message, MessageBody}

/**
 * PropsConfig 道具配置数据类
 * 对应客户端 PropsConfig，描述一个道具的所有属性和效果
 */
class PropsConfig {
  var id: String = _                        // 道具ID
  var propsType: String = _                 // 道具类型: SpeedUpper, StaminaUpper, BombDamageUpper, BombRadiusUpper, BombFuseTimeLower, BombCooldownLower, BombRecoveryTimeLower, MaxHpUpper, HpRegen
  var weight: Int = _                       // 生成权重
  var validTime: Float = _                  // 有效时长（秒）: -1=永久, 0=定时触发, >0=限时
  var size: String = _                      // 道具大小: Small, Medium, Large

  // === 效果值（与客户端 PropsConfig 一一对应） ===

  // 基础属性 (Addition)
  var maxHpAddition: Float = 0f             // 最大生命值加值
  var hpRegenAddition: Float = 0f           // 生命恢复加值
  var speedMultiply: Float = 0f             // 移动速度乘法因子
  var maxLevelAddition: Int = 0             // 最大等级加值

  // 体力属性 (Addition)
  var maxStaminaAddition: Float = 0f        // 体力上限加值
  var staminaDrainRateAddition: Float = 0f  // 体力消耗速率加值
  var staminaRegenRateAddition: Float = 0f  // 体力恢复速率加值
  var speedMultiplierMultiply: Float = 0f   // 速度倍率乘法因子

  // 炸弹属性 (Addition)
  var maxBombCountAddition: Int = 0         // 最大炸弹数加值
  var bombDamageAddition: Float = 0f        // 炸弹伤害加值
  var bombRadiusAddition: Int = 0           // 炸弹范围加值

  // 炸弹属性 (Subtract)
  var bombFuseTimeSubtract: Float = 0f      // 引信时间减少值

  // 炸弹属性 (Divide)
  var bombCooldownDivide: Float = 0f        // 冷却时间除数因子
  var bombRecoveryTimeDivide: Float = 0f    // 恢复时间除数因子

  /**
   * 从 JSON 解析的 MessageBody 加载配置
   */
  def loadFromJson(json: MessageBody): PropsConfig = {
    id = json.getString("id")
    propsType = json.getString("type")
    weight = json.getInt("weight")
    validTime = json.getFloat("validTime")
    size = json.getString("size")

    // 效果值
    maxHpAddition = json.getFloat("maxHpAddition")
    hpRegenAddition = json.getFloat("hpRegenAddition")
    speedMultiply = json.getFloat("speedMultiply")
    maxLevelAddition = json.getInt("maxLevelAddition")

    maxStaminaAddition = json.getFloat("maxStaminaAddition")
    staminaDrainRateAddition = json.getFloat("staminaDrainRateAddition")
    staminaRegenRateAddition = json.getFloat("staminaRegenRateAddition")
    speedMultiplierMultiply = json.getFloat("speedMultiplierMultiply")

    maxBombCountAddition = json.getInt("maxBombCountAddition")
    bombDamageAddition = json.getFloat("bombDamageAddition")
    bombRadiusAddition = json.getInt("bombRadiusAddition")

    bombFuseTimeSubtract = json.getFloat("bombFuseTimeSubtract")
    bombCooldownDivide = json.getFloat("bombCooldownDivide")
    bombRecoveryTimeDivide = json.getFloat("bombRecoveryTimeDivide")

    this
  }

  /**
   * 将道具配置转为网络消息体（发给客户端用于创建 PropsStatus）
   */
  def toMessageBody: MessageBody = {
    MessageBody(
      "propsId" -> id,
      "propsType" -> propsType,
      "weight" -> weight,
      "validTime" -> validTime,
      "propsSize" -> size,
      "maxHpAddition" -> maxHpAddition,
      "hpRegenAddition" -> hpRegenAddition,
      "speedMultiply" -> speedMultiply,
      "maxLevelAddition" -> maxLevelAddition,
      "maxStaminaAddition" -> maxStaminaAddition,
      "staminaDrainRateAddition" -> staminaDrainRateAddition,
      "staminaRegenRateAddition" -> staminaRegenRateAddition,
      "speedMultiplierMultiply" -> speedMultiplierMultiply,
      "maxBombCountAddition" -> maxBombCountAddition,
      "bombDamageAddition" -> bombDamageAddition,
      "bombRadiusAddition" -> bombRadiusAddition,
      "bombFuseTimeSubtract" -> bombFuseTimeSubtract,
      "bombCooldownDivide" -> bombCooldownDivide,
      "bombRecoveryTimeDivide" -> bombRecoveryTimeDivide
    )
  }
}

object PropsConfig {
  def apply(): PropsConfig = new PropsConfig
}
