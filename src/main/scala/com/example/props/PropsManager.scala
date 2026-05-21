package com.example.props

import com.example.message.MessageBody
import scala.collection.mutable
import scala.util.Random

/**
 * PropsManager 道具管理器（单例）
 * 负责加载道具配置、按权重概率选择道具
 * 对应客户端 PropsManager + PropsProbabilityConfig
 */
object PropsManager {
  /** 道具整体生成概率 (0-100) */
  var propsGenerationProbability: Int = 50

  /** 所有道具配置列表 */
  private val propsConfigs: mutable.ListBuffer[PropsConfig] = mutable.ListBuffer.empty

  /** 权重前缀和数组，用于二分查找 */
  private var weightPre: Array[Long] = Array.empty

  /** 总权重 */
  private var totalWeight: Long = 0

  /** 是否已初始化 */
  private var isInit: Boolean = false

  /**
   * 初始化道具管理器，加载配置文件
   */
  def init(): Unit = {
    if (isInit) return

    val filePath = "props/props_config.json"
    val inputStream = getClass.getClassLoader.getResourceAsStream(filePath)
    if (inputStream == null) {
      println(s"[PropsManager] 道具配置文件[$filePath]不存在，道具系统将不启用")
      isInit = true
      return
    }

    try {
      val content = scala.io.Source.fromInputStream(inputStream).mkString
      val root = MessageBody.fromJson(content)

      propsGenerationProbability = root.getInt("propsGenerationProbability")

      val propsArray = root.get("props") match {
        case Some(mb: MessageBody) => mb
        case _ =>
          println("[PropsManager] 道具配置格式错误")
          isInit = true
          return
      }

      // 解析所有道具配置
      propsConfigs.clear()
      var i = 0
      var key = s"$i"
      while (propsArray.contains(key)) {
        propsArray.get(key) match {
          case Some(propJson: MessageBody) =>
            val config = PropsConfig().loadFromJson(propJson)
            propsConfigs += config
          case _ =>
        }
        i += 1
        key = s"$i"
      }

      // 构建权重前缀和
      weightPre = new Array[Long](propsConfigs.size)
      totalWeight = 0
      for (i <- propsConfigs.indices) {
        totalWeight += propsConfigs(i).weight
        weightPre(i) = totalWeight
      }

      println(s"[PropsManager] 成功加载 ${propsConfigs.size} 个道具配置，总权重=$totalWeight，生成概率=$propsGenerationProbability%")
      isInit = true
    } finally {
      inputStream.close()
    }
  }

  /**
   * 根据权重概率随机选择一个道具配置
   * @return 选中的 PropsConfig，如果不生成道具则返回 None
   */
  def randomPick(): Option[PropsConfig] = {
    if (!isInit) init()
    if (propsConfigs.isEmpty) return None

    // 首先生成概率判定
    val roll = Random.nextInt(100)
    if (roll >= propsGenerationProbability) {
      return None
    }

    // 按权重随机选择
    if (totalWeight <= 0) return None
    val randomWeight = Random.nextLong(totalWeight)

    // 二分查找
    var idx = 0
    while (idx < weightPre.length && weightPre(idx) <= randomWeight) {
      idx += 1
    }
    if (idx >= propsConfigs.size) idx = propsConfigs.size - 1

    Some(propsConfigs(idx))
  }

  /**
   * 获取道具配置总数
   */
  def count: Int = propsConfigs.size

  /**
   * 根据 propsId 查找道具配置
   */
  def getById(propsId: String): Option[PropsConfig] = {
    propsConfigs.find(_.id == propsId)
  }
}
