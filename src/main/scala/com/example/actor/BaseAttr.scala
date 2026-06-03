package com.example.actor

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`

/**
 * BaseAttr类，用于管理角色的基础属性
 * @param owner 拥有这些属性的Actor对象
 */
class BaseAttr(owner: Actor) {
  // 速度属性，乘100，保留两位小数
  var level: Int = 1  // 等级
  var exp: Int = 0    // 经验值
  var maxExpToLevelUp: Int = 100  // 升级所需最大经验值



  // 基础属性
  var hp: Int = _     // 当前生命值
  var maxHp: Int = _  // 最大生命值
  var speed: Int = _  // 基础速度
  var maxLevel: Int = _  // 最大等级
  var maxStamina: Int = _  // 最大体力值
  var staminaDrainRate: Int = _  // 体力消耗率
  var staminaRegenRate: Int = _  // 体力恢复率
  var speedMultiplier: Float = _  // 速度倍数
  var maxBombCount: Int = _  // 最大炸弹数量
  var bombDamage: Int = _  // 炸弹伤害
  var bombRadius: Float = _  // 炸弹爆炸半径
  var bombFuseTime: Float = _  // 炸弹引信时间
  var bombCooldown: Float = _  // 炸弹冷却时间
  var bombRecoveryTime: Float = _  // 炸弹恢复时间




  // 属性成长值
  var speedGrowth: Float = _  // 速度成长值
  var staminaGrowth: Int = _  // 体力成长值
  var bombDamageGrowth: Int = _  // 炸弹伤害成长值
  var bombRadiusGrowth: Int = _  // 炸弹半径成长值
  var bombFuseTimeGrowth: Float = _  // 炸弹引信时间成长值
  var bombCooldownGrowth: Float = _  // 炸弹冷却时间成长值
  var bombRecoveryTimeGrowth: Float = _  // 炸弹恢复时间成长值
  var maxBombCountGrowth: Int = _  // 最大炸弹数量成长值
  var maxHpGrowth: Int = _  // 最大生命值成长值

  /**
   * 获取实际速度（乘以速度倍数并乘以100）
   * @return 实际速度值
   */
  def Speed: Float = speed * speedMultiplier * 100
  /**
   * 获取炸弹引信时间（毫秒）
   * @return 引信时间（毫秒）
   */
  def FuseTime: Int = (bombFuseTime * 1000).toInt
  /**
   * 获取炸弹恢复时间（毫秒）
   * @return 恢复时间（毫秒）
   */
  def BombRecoveryTime: Int = (bombRecoveryTime * 1000).toInt
  /**
   * 获取炸弹冷却时间（毫秒）
   * @return 冷却时间（毫秒）
   */
  def Cooldown: Int = (bombCooldown * 1000).toInt
  /**
   * 获取炸弹伤害值
   * @return 伤害值
   */
  def BombDamage: Int = bombDamage
  /**
   * 获取炸弹爆炸半径
   * @return 爆炸半径
   */
  def BombRadius: Float = bombRadius
  /**
   * 获取最大炸弹数量
   * @return 最大数量
   */
  def MaxBombCount: Int = maxBombCount

  /**
   * 初始化角色属性
   * @param typ 角色类型
   * @return BaseAttr实例
   */
  def initAttr(typ: String): BaseAttr = {
    // 构建属性文件路径
    val filePath = s"attr/$typ.attr"
    // 从类加载器中获取属性文件
    Thread.currentThread().getContextClassLoader.getResourceAsStream(filePath) match {
      case null =>
        // 如果文件不存在，抛出异常
        throw new RuntimeException(s"属性文件[$filePath]不存在")
      case inputStream =>
        // 加载属性文件
        val props = new java.util.Properties()
        props.load(inputStream)
        inputStream.close()
        // 遍历所有属性名
        props.stringPropertyNames().foreach { name =>
          val value = props.getProperty(name)
          // 获取对应的字段
          val field = this.getClass.getDeclaredField(name)
          field.setAccessible(true)
          // 根据字段类型设置值
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
        // 初始化生命值为最大生命值
        hp = maxHp
        this
    }
  }

  /**
   * 增加经验值，返回是否触发升级
   * @param amount 经验值增量
   * @return true 表示触发了升级，false 表示未触发
   */
  def addExp(amount: Int): Boolean = {
    if (level >= maxLevel) {
      // 等级已满，经验值封顶
      exp = Math.min(exp + amount, maxExpToLevelUp)
      return false
    }
    exp += amount
    if (exp >= maxExpToLevelUp) {
      levelUp()
      return true
    }
    false
  }

  /**
   * 升级：消耗经验值、增加等级、应用成长属性
   */
  private def levelUp(): Unit = {
    exp -= maxExpToLevelUp
    level += 1

    // 基础属性成长
    maxHp += maxHpGrowth
    hp = maxHp  // 升级回满血
    speed += speedGrowth.toInt
    maxStamina += staminaGrowth

    // 炸弹属性成长
    maxBombCount += maxBombCountGrowth
    bombDamage += bombDamageGrowth
    bombRadius += bombRadiusGrowth
    bombFuseTime = Math.max(0.1f, bombFuseTime - bombFuseTimeGrowth)
    bombCooldown = Math.max(0.1f, bombCooldown - bombCooldownGrowth)
    bombRecoveryTime = Math.max(0.5f, bombRecoveryTime - bombRecoveryTimeGrowth)

    println(s"[LevelUp] 角色[${owner.id}] 升级: Lv.${level - 1} → Lv.$level, " +
      s"exp=$exp/$maxExpToLevelUp, maxHp=$maxHp, speed=$speed, maxStamina=$maxStamina, " +
      s"bombCount=$maxBombCount, bombDamage=$bombDamage, bombRadius=$bombRadius, " +
      s"bombFuseTime=$bombFuseTime, bombCooldown=$bombCooldown, bombRecoveryTime=$bombRecoveryTime")
  }
}

/**
 * BaseAttr的伴生对象
 */
object BaseAttr {
  /**
   * 创建BaseAttr实例的工厂方法
   * @param owner 拥有这些属性的Actor对象
   * @return 新的BaseAttr实例
   */
  def apply(owner: Actor): BaseAttr = new BaseAttr(owner)
}
