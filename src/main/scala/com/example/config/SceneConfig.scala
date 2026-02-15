package com.example.config

import com.example.scene.SceneType

object SceneConfig extends IConfig {
  private val sceneDefs: collection.mutable.Map[Int, SceneDef] = collection.mutable.Map()

  override def loadConfigs(): Unit = {
    val sceneDef = new SceneDef
    sceneDef.id = 1
    sceneDef.typ = SceneType.BASE_GAME
    sceneDef.name = s"基础游戏场景"
    sceneDef.maxPlayerCnt = 4
    sceneDef.mapId = "map_01"
    sceneDef.spawnPoints = Array(
      (-1250, 40, -1250, 45f),
      (1250, 40, -1250, 135f),
      (1250, 40, 1250, 225f),
      (-1250, 40, 1250, 315f)
    )
    sceneDefs += (1 -> sceneDef)
  }

  def getDef(sceneId: String): SceneDef = {
    getDef(sceneId.toInt)
  }

  def getDef(sceneId: Int): SceneDef = {
    sceneDefs.getOrElse(sceneId, null)
  }
}

class SceneDef {
  var id: Int = _
  var typ: Int = _
  var name: String = _
  var spawnPoints: Array[(Int, Int, Int, Float)] = _
  var maxPlayerCnt: Int = _
  var mapId: String = _
}