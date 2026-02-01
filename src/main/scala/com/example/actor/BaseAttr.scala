package com.example.actor

class BaseAttr(owner: Actor) {
  // 速度属性，乘100，保留两位小数
  val currentSpeed: Int = 500
  val baseSpeed: Int = 500
  val maxHp: Int = 100
  val hp: Int = 100
  val exp: Int = 0
  val level: Int = 1
  val stamina: Int = 100
  val maxStamina: Int = 100
  val staminaDrainRate: Int = 1
  val staminaRegenRate: Int = 1
  val bombCount: Int = 5
  val bombDamage: Int = 40
  val bombRadius: Int = 5
  val bombFuseTime: Int = 3
  val bombCooldown: Int = 1
  val maxBombCooldown: Int = 50
  val bombRecoveryTime: Int = 2
  val maxBombCount: Int = 5
  val maxBombRecoveryTime: Int = 2

  def info: Seq[(String, Any)] = {
    this.getClass.getDeclaredFields.filterNot(_.isSynthetic).map { field =>
      field.setAccessible(true)
      field.getName -> field.get(this)
    }
  }
}

object BaseAttr {
  def apply(owner: Actor): BaseAttr = new BaseAttr(owner)
}
