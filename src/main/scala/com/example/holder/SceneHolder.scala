package com.example.holder

import com.example.actor.Actor
import com.example.config.SceneConfig
import com.example.exception.ThrowBusinessException
import com.example.reflect.Scanner
import com.example.scene.{Scene, SceneFacade}

import scala.collection.mutable

object SceneHolder extends Scanner[Int, SceneFacade] {
  private val scenes: mutable.Map[String, Scene] = mutable.Map()

  override val packageName: String = "com.example.scene"

  def tick(tickIdx: Long): Unit = {
    scenes.values.foreach(scene => scene.tick(tickIdx))
  }

  def createScene(defId: Int): Scene = {
    val sceneFacade = getSceneFacade(defId)
    val sceneDef = SceneConfig.getDef(defId)
    val scene = sceneFacade(sceneDef)
    addScene(scene)
    scene
  }

  def addScene(scene: Scene): Unit = {
    scenes += (scene.id -> scene)
  }

  def getScene(sceneId: String): Scene = {
    scenes.getOrElse(sceneId, null)
  }

  private def getSceneFacade(defId: Int): SceneFacade = {
    val sceneDef = SceneConfig.getDef(defId)
    getSceneFacadeByType(sceneDef.typ)
  }

  private def getSceneFacadeByType(sceneType: Int): SceneFacade = {
    map.getOrElse(sceneType, ThrowBusinessException(s"场景类型[$sceneType]未定义"))
  }

  def checkEnterScene(defId: Int, actor: Actor): Boolean = {
    val sceneFacade = getSceneFacade(defId)
    sceneFacade.checkEnterScene(actor)
  }

  def checkEnterScene(sceneId: String, actor: Actor): Boolean = {
    val defId = sceneId.split("_")(0).toInt
    checkEnterScene(defId, actor)
  }

  def enterScene(sceneId: String, actor: Actor): Unit = {
    if (actor == null) {
      println(s"对象不存在")
      return
    }
    if (!scenes.keys.exists(sceneId == _)) {
      ThrowBusinessException(s"场景[$sceneId]不存在")
    }

    val lastScene = scenes.getOrElse(actor.movement.lastSceneId, null)
    if (lastScene != null) {
      if (lastScene.players.contains(actor.id)) {
        lastScene.onExit(actor)
      }
    }

    val scene = scenes(sceneId)
    scene.onEnter(actor)
  }
}