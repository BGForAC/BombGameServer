package com.example.actor

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`

class BaseAttr(owner: Actor) {
  // 速度属性，乘100，保留两位小数
  var level: Int = 1
  var exp: Int = 0
  var maxExpToLevelUp: Int = 100

  var hp: Int = _
  var maxHp: Int = _
  var speed: Int = _
  var maxLevel: Int = _
  var maxStamina: Int = _
  var staminaDrainRate: Int = _
  var staminaRegenRate: Int = _
  var speedMultiplier: Float = _
  var maxBombCount: Int = _
  var bombDamage: Int = _
  var bombRadius: Float = _
  var bombFuseTime: Float = _
  var bombCooldown: Float = _
  var bombRecoveryTime: Float = _
  var speedGrowth: Float = _
  var staminaGrowth: Int = _
  var bombDamageGrowth: Int = _
  var bombRadiusGrowth: Int = _
  var bombFuseTimeGrowth: Float = _
  var bombCooldownGrowth: Float = _
  var bombRecoveryTimeGrowth: Float = _
  var maxBombCountGrowth: Int = _
  var maxHpGrowth: Int = _

  def Speed: Float = speed * speedMultiplier * 100
  def FuseTime: Int = (bombFuseTime * 1000).toInt
  def BombRecoveryTime: Int = (bombRecoveryTime * 1000).toInt
  def Cooldown: Int = (bombCooldown * 1000).toInt
  def BombDamage: Int = bombDamage
  def BombRadius: Float = bombRadius
  def MaxBombCount: Int = maxBombCount

//  def info: Seq[(String, Any)] = {
//    this.getClass.getDeclaredFields.filterNot(_.isSynthetic).flatMap { field =>
//      try {
//        field.setAccessible(true)
//        Option(field.getName -> field.get(this))
//      } catch {
//        case _: Throwable =>
//          None
//      }
//    }
//  }
//
  def initAttr(typ: String): BaseAttr = {
    val filePath = s"attr/$typ.attr"
    Thread.currentThread().getContextClassLoader.getResourceAsStream(filePath) match {
      case null =>
        throw new RuntimeException(s"属性文件[$filePath]不存在")
      case inputStream =>
        val props = new java.util.Properties()
        props.load(inputStream)
        inputStream.close()
        props.stringPropertyNames().foreach { name =>
          val value = props.getProperty(name)
          val field = this.getClass.getDeclaredField(name)
          field.setAccessible(true)
          field.getType match {
            case t if t == classOf[Int] => field.set(this, value.toInt)
            case t if t == classOf[Float] => field.set(this, value.toFloat)
            case t if t == classOf[Double] => field.set(this, value.toDouble)
            case t if t == classOf[Long] => field.set(this, value.toLong)
            case t if t == classOf[Boolean] => field.set(this, value.toBoolean)
            case _ =>
              println(s"不支持的属性类型：${field.getType}，属性名：$name")
          }
        }
        hp = maxHp
        this
    }
  }
}

object BaseAttr {
  def apply(owner: Actor): BaseAttr = new BaseAttr(owner)
}
