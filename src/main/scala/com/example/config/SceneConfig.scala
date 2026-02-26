package com.example.config

import com.example.scene.SceneType

/**
 * 场景配置对象，实现了IConfig接口
 * 用于管理和加载游戏场景的配置信息
 */
object SceneConfig extends IConfig {
  // 使用可变Map来存储场景定义，键为场景ID，值为SceneDef对象
  private val sceneDefs: collection.mutable.Map[Int, SceneDef] = collection.mutable.Map()

  /**
   * 加载场景配置的方法
   * 初始化并设置基础游戏场景的各项参数
   */
  override def loadConfigs(): Unit = {
    // 创建一个新的场景定义对象
    val sceneDef = new SceneDef
    // 设置场景ID为1
    sceneDef.id = 1
    // 设置场景类型为基础游戏场景
    sceneDef.typ = SceneType.BASE_GAME
    // 设置场景名称
    sceneDef.name = s"基础游戏场景"
    // 设置最大玩家数量为4
    sceneDef.maxPlayerCnt = 4
    // 设置地图ID
    sceneDef.mapId = "map_01"
    // 设置出生点坐标和朝向，共4个位置
    sceneDef.spawnPoints = Array(
      (-1250, 40, -1250, 45f),
      (1250, 40, -1250, 135f),
      (1250, 40, 1250, 225f),
      (-1250, 40, 1250, 315f)
    )
    // 将场景定义添加到Map中
    sceneDefs += (1 -> sceneDef)
  }

  /**
   * 根据场景ID(字符串类型)获取场景定义
   * @param sceneId 场景ID，字符串类型
   * @return SceneDef对象，如果找不到则返回null
   */
  def getDef(sceneId: String): SceneDef = {
    getDef(sceneId.toInt)
  }

  /**
   * 根据场景ID(Int类型)获取场景定义
   * @param sceneId 场景ID，整型
   * @return SceneDef对象，如果找不到则返回null
   */
  def getDef(sceneId: Int): SceneDef = {
    sceneDefs.getOrElse(sceneId, null)
  }
}

/**
 * 场景定义类
 * 用于存储单个场景的详细配置信息
 */
class SceneDef {
  // 场景ID
  var id: Int = _
  // 场景类型
  var typ: Int = _
  // 场景名称
  var name: String = _
  // 出生点数组，每个元素是一个元组，包含x、y、z坐标和朝向角度
  var spawnPoints: Array[(Int, Int, Int, Float)] = _
  // 最大玩家数量
  var maxPlayerCnt: Int = _
  // 地图ID
  var mapId: String = _
}