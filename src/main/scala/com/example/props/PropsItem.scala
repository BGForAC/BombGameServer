package com.example.props

import com.example.actor.Actor
import com.example.commands.CmdType
import com.example.holder.{PlayerHolder, SceneHolder}
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels

/**
 * PropsItem 代表场景中一个可被拾取的道具
 * 继承 Actor，拥有位置信息和道具配置
 *
 * @param id 道具唯一ID
 * @param config 道具配置
 * @param sceneId 所属场景ID
 */
class PropsItem(id: String, val config: PropsConfig, private var sceneId: String) extends Actor(id) {

  /** 是否已被拾取 */
  private var isPickedUp: Boolean = false

  /**
   * 进入场景时设置道具位置
   */
  def spawnAt(x: Int, y: Int, z: Int): Unit = {
    movement.setToScene(SceneHolder.getScene(sceneId))
    movement.setPosition((x, y, z, 0f), checkMove = false)
  }

  /**
   * 玩家拾取道具
   * @param playerId 拾取道具的玩家ID
   */
  def onPickUp(playerId: String): Unit = {
    if (isPickedUp) {
      //println(s"[PropsItem] 道具[$id]已被拾取，忽略重复拾取")
      return
    }
    isPickedUp = true

    val player = PlayerHolder.getPlayer(playerId)
    if (player == null) {
      //println(s"[PropsItem] 玩家[$playerId]不存在，道具拾取失败")
      return
    }

    // 从场景中移除道具
    SceneHolder.exitScene(sceneId, this)

    // 应用道具效果到玩家
    PropsEffectSystem.applyEffect(playerId, config)

    // 广播道具被拾取
    val scene = SceneHolder.getScene(sceneId)
    if (scene != null) {
      scene.players.values.foreach { p =>
        PlayerChannels.send(p.id, Message(CmdType.PROP_PICKED_UP, MessageBody(
          "propsId" -> config.id,
          "itemId" -> id,
          "playerId" -> playerId
        )))
      }
    }

    //println(s"[PropsItem] 道具[${config.id}]($id) 被玩家[$playerId]拾取")

    // 通知拾取者拾取了什么道具
    PlayerChannels.info(playerId, s"拾取了道具: ${config.id}")
  }

  /**
   * 将道具信息转为网络消息体
   */
  def toSpawnMessage: MessageBody = {
    val info = movement.info
    MessageBody.addMessageBody(info, MessageBody(
      "propsId" -> config.id,
      "itemId" -> id,
      "propsType" -> config.propsType,
      "validTime" -> config.validTime,
      "propsSize" -> config.size
    ))
  }

  def sceneIdVal: String = sceneId
}

object PropsItem {
  private var idCounter: Long = 0

  /**
   * 创建道具实例
   */
  def apply(config: PropsConfig, sceneId: String): PropsItem = {
    idCounter += 1
    new PropsItem(s"prop_$idCounter", config, sceneId)
  }
}
