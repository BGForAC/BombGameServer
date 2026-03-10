package com.example.holder

import com.example.actor.Actor
import com.example.config.SceneConfig
import com.example.exception.ThrowBusinessException
import com.example.reflect.Scanner
import com.example.scene.{Scene, SceneFacade}

import scala.collection.mutable

/**
 * 场景持有者类，负责管理所有游戏场景
 * 继承自Scanner[Int, SceneFacade]，用于扫描和获取场景门面
 */
object SceneHolder extends Scanner[Int, SceneFacade] {
  // 使用可变Map存储场景，key为场景ID，value为场景对象
  private val scenes: mutable.Map[String, Scene] = mutable.Map()

  // 设置要扫描的包名，用于场景门面的自动发现
  override val packageName: String = "com.example.scene"

  /**
   * 场景tick方法，遍历所有场景并调用其tick方法
   * @param tickIdx tick索引
   */
  def tick(tickIdx: Long): Unit = {
    scenes.values.foreach(scene => scene.tick(tickIdx))
  }

  /**
   * 创建新场景
   * @param defId 场景定义ID
   * @return 创建的场景对象
   */
  def createScene(defId: Int): Scene = {
    val sceneFacade = getSceneFacade(defId)
    val sceneDef = SceneConfig.getDef(defId)
    val scene = sceneFacade(sceneDef)
    addScene(scene)
    scene
  }


  /**
   * 添加场景到场景持有者
   * @param scene 要添加的场景对象
   */
  def addScene(scene: Scene): Unit = {
    scenes += (scene.id -> scene)
  }

  /**
   * 根据场景ID获取场景
   * @param sceneId 场景ID
   * @return 场景对象，如果不存在则返回null
   */
  def getScene(sceneId: String): Scene = {
    scenes.getOrElse(sceneId, null)
  }

  /**
   * 根据场景定义ID获取场景门面
   * @param defId 场景定义ID
   * @return 场景门面对象
   */
  private def getSceneFacade(defId: Int): SceneFacade = {
    val sceneDef = SceneConfig.getDef(defId)
    getSceneFacadeByType(sceneDef.typ)
  }

  /**
   * 根据场景类型获取场景门面
   * @param sceneType 场景类型
   * @return 场景门面对象
   */
  private def getSceneFacadeByType(sceneType: Int): SceneFacade = {
    map.getOrElse(sceneType, ThrowBusinessException(s"场景类型[$sceneType]未定义"))
  }

  /**
   * 检查角色是否可以进入场景
   * @param defId 场景定义ID
   * @param actor 角色
   * @return 是否可以进入
   */
  def checkEnterScene(defId: Int, actor: Actor): Boolean = {
    val sceneFacade = getSceneFacade(defId)
    sceneFacade.checkEnterScene(actor)
  }

  /**
   * 检查角色是否可以进入场景
   * @param sceneId 场景ID
   * @param actor 角色
   * @return 是否可以进入
   */
  def checkEnterScene(sceneId: String, actor: Actor): Boolean = {
    val defId = sceneId.split("_")(0).toInt
    checkEnterScene(defId, actor)
  }

  /**
   * 角色进入场景
   * @param sceneId 场景ID
   * @param actor 角色
   */
  def enterScene(sceneId: String, actor: Actor): Unit = {
    if (actor == null) throw new IllegalArgumentException("actor不能为空")
    if (!scenes.keys.exists(sceneId == _)) ThrowBusinessException(s"场景[$sceneId]不存在")

    val lastScene = scenes.getOrElse(actor.movement.lastSceneId, null)
    if (lastScene != null) {
      if (lastScene.players.contains(actor.id)) {
        lastScene.onExit(actor)
      }
    }

    val scene = scenes(sceneId)
    scene.onEnter(actor)
  }

  /**
   * 角色离开场景
   * @param sceneId 场景ID
   * @param actor 角色
   */
  def exitScene(sceneId: String, actor: Actor): Unit = {
    if (actor == null) throw new IllegalArgumentException("actor不能为空")
    if (!scenes.keys.exists(sceneId == _)) ThrowBusinessException(s"场景[$sceneId]不存在")

    val scene = scenes(sceneId)
    scene.onExit(actor)
  }
}