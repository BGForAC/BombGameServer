package com.example.commands

import com.example.holder.{BaseGameRoomHolder, BaseGameSceneHolder, PlayerHolder}
import com.example.message.Message
import com.example.serer.PlayerChannels

object Command04 extends IPlayerCommand {
  def handler01(playerId: String, message: Message): Unit = {
    val mapId: Int = message.getInt("mapId")
    val career: String = message.getString("career")
    val controlConfig = message.getInt("controlConfig")
    val player = PlayerHolder.getPlayer(playerId)
    if (player == null) {
      throw new RuntimeException("玩家未登录")
    }
    player.attr.initAttr(career)
    player.career = career
    player.controlConfig = controlConfig
    BaseGameSceneHolder.addToMatchQueue(playerId, mapId)
  }

  def handler02(playerId: String, message: Message): Unit = {
    BaseGameSceneHolder.removeFromMatchQueue(playerId)
  }

  def handler04(playerId: String, message: Message): Unit = {
    val roomName: String = message.getString("roomName")
    BaseGameRoomHolder.createRoom(playerId, roomName)
  }

  def handler05(playerId: String, message: Message): Unit = {
    val roomId: Int = message.getInt("roomId")
    BaseGameRoomHolder.joinRoom(playerId, roomId)
  }

  def handler06(playerId: String, message: Message): Unit = {
    BaseGameRoomHolder.leaveRoom(playerId)
  }

  def handler08(playerId: String, message: Message): Unit = {
    BaseGameRoomHolder.refreshRoomInfo(playerId)
  }

  def handler09(playerId: String, message: Message): Unit = {
    val targetId: String = message.getString("targetId")
    BaseGameRoomHolder.kickPlayer(targetId, playerId)
    PlayerChannels.alert(playerId, "玩家踢除成功")
    PlayerChannels.alert(targetId, "你被房主踢出房间")

  }

  def handler0A(playerId: String, message: Message): Unit = {
    BaseGameRoomHolder.reqRemoveRoom(playerId)
  }

  def handler0B(playerId: String, message: Message): Unit = {
    val targetId: String = message.getString("targetId")
    BaseGameRoomHolder.changeLeader(playerId, targetId)
    PlayerChannels.alert(playerId, "转让成功")
  }

  def handler0C(playerId: String, messgae: Message): Unit = {
    BaseGameRoomHolder.ready(playerId)
  }

  def handler0D(playerId: String, message: Message): Unit = {
    val career: String = message.getString("career")
    BaseGameRoomHolder.changeCareer(playerId, career)
  }
}