package com.example.scene

import com.example.actor.{Actor, Player}
import com.example.config.SceneDef

import scala.collection.mutable

/**
 * BaseGameScene类是游戏场景的基础实现类，它继承自Scene类
 * 用于管理游戏场景中的基本逻辑，包括玩家进入、位置生成等功能
 *
 * @param id 场景的唯一标识符
 * @param _def 场景定义对象，包含场景的各种配置信息
 */
class BaseGameScene(id: String, _def: SceneDef) extends Scene(id, _def) {
  // 玩家索引计数器，用于分配玩家在场景中的位置索引
  private var idx: Int = 0
  // 玩家ID到索引的映射表，用于记录每个玩家在场景中的索引位置
  private val player2Idx: mutable.Map[String, Int] = mutable.Map.empty

  /**
   * 场景的tick方法，在每一帧游戏循环中调用
   * @param tickIdx 当前tick的索引值
   */
  override def tick(tickIdx: Long): Unit = {
    super.tick(tickIdx)
  }

  /**
   * 当玩家进入场景时的处理方法
   * @param actor 进入场景的演员对象
   */
  override def onEnter(actor: Actor): Unit = {
    actor match {
      case player: Player =>
        // 将玩家ID和当前索引添加到映射表中
        player2Idx += player.id -> idx
        // 调用父类的onEnter方法
        super.onEnter(player)
        // 设置玩家的出生点位置
        player.movement.setPosition(getSpawnPoint(player.id), checkMove = false)
        // 索计数器递增
        idx = idx + 1
      case _ =>
    }
  }

  /**
   * 根据玩家ID获取其出生点位置
   * @param playerId 玩家的唯一标识符
   * @return 返回玩家的出生点坐标(x, y, z)和朝向(浮点数)
   */
  private def getSpawnPoint(playerId: String): (Int, Int, Int, Float) = {
    _def.spawnPoints(player2Idx(playerId))
  }

  /**
   * 获取玩家ID到索引的映射表
   * @return 返回包含玩家ID和索引的映射表
   */
  def playerIdxInfo: mutable.Map[String, Int] = {
    player2Idx
  }
}

/**
 * BaseGameSceneFacade是BaseGameScene的外观类(Facade)
 * 提供创建和管理BaseGameScene实例的接口
 */
object BaseGameSceneFacade extends SceneFacade {
  // 支持的场景类型集合，这里只支持BASE_GAME类型的场景
  override val keySet: Set[Int] = Set(SceneType.BASE_GAME)

  /**
   * 检查玩家是否可以进入场景
   * @param actor 想要进入场景的演员对象
   * @return 返回布尔值表示是否允许进入
   */
  override def checkEnterScene(actor: Actor): Boolean = {
    true
  }

  /**
   * 创建新的BaseGameScene实例
   * @param _def 场景定义对象，包含场景的各种配置信息
   * @return 返回新创建的BaseGameScene实例
   */
  override def apply(_def: SceneDef): Scene = {
    new BaseGameScene(genSceneId(_def), _def)
  }
}
