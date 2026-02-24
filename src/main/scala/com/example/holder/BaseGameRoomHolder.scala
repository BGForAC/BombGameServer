package com.example.holder

import com.example.commands.CmdType
import com.example.exception.ThrowBusinessException
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels

import scala.collection.mutable
import scala.util.Random

object BaseGameRoomHolder {
  private val rooms: mutable.Map[Int, Room] = mutable.Map.empty[Int, Room]
  private var nextUpdateTime = 0L

  def tick(tickIdx: Long): Unit = {
    rooms.values.foreach(_.tick(tickIdx))
    if (nextUpdateTime < System.currentTimeMillis()) {
      nextUpdateTime = System.currentTimeMillis() + 5000
      PlayerChannels.sendToAll(roomMessage)
    }
  }

  def createRoom(playerId: String, roomName: String): Unit = {
    if (Seq('-', '=', ':', '%', ';', '|').exists(c => roomName.contains(c))) ThrowBusinessException("房间名不合法，创建失败")
    val room = Room(playerId, roomName)
    rooms += room.id -> room
    joinRoom(playerId, room.id)
  }

  def isRoomLeader(playerId: String, roomId: Int): Boolean = {
    rooms.get(roomId) match {
      case Some(room) => room.leaderId == playerId
      case None => false
    }
  }

  def removeRoom(roomId: Int): Unit = {
    rooms.get(roomId) match {
      case Some(room) => room.beforeRemove()
      case None =>
    }
    rooms -= roomId
  }

  def joinRoom(playerId: String, roomId: Int): Unit = {
    rooms.get(roomId) match {
      case Some(room) => room.addMember(playerId)
      case None => ThrowBusinessException("房间不存在")
    }
  }

  def leaveRoom(playerId: String, roomId: Int): Unit = {
    rooms.get(roomId) match {
      case Some(room) => room.removeMember(playerId)
      case None => ThrowBusinessException("你不在房间中")
    }
  }

  def kickPlayer(roomId: Int, targetId: String, sourceId: String) = {
    rooms.get(roomId) match {
      case Some(room) => {
        if (room.leaderId != sourceId) ThrowBusinessException("只有房主才能踢人")
        room.removeMember(targetId, ExitTypeEnum.KICK)
      }
      case None => ThrowBusinessException("房间不存在")
    }
  }

  def changeLeader(roomId: Int, targetId: String): Unit = {
    rooms.get(roomId) match {
      case Some(room) => room.changeLeader(targetId)
      case None => ThrowBusinessException("房间不存在")
    }
  }

  def refreshRoomInfo(playerIds: String*): Unit = {
    val message = roomMessage
    playerIds.foreach(PlayerChannels.send(_, message))
  }

  private def roomMessage: Message = {
    Message(CmdType.BASE_GAME_REQ_ROOM_INFO, info)
  }

  def info: MessageBody = {
    MessageBody("rooms" -> rooms.values.map(_.infoStr).mkString("%"))
  }

  private class Room(val id: Int,var leaderId: String, val roomName: String) {
    private val roomMember = mutable.Set.empty[String]
    roomMember += leaderId

    private val roomSize: Int = 4

    def tick(tickIdx: Long): Unit = {
      roomMember.foreach(playerId => {
        if (!PlayerHolder.isOnline(playerId)) removeMember(playerId)
      })
      if (roomMember.size == 0) BaseGameRoomHolder.removeRoom(id)
    }

    def beforeRemove(): Unit = {
      val ids = mutable.ListBuffer.empty[String]
      roomMember.foreach({ id =>
        ids += id
        removeMember(id, ExitTypeEnum.DISBAND)
      })
      BaseGameRoomHolder.refreshRoomInfo(ids: _*)
    }

    def addMember(playerId: String): Unit = {
      if (roomMember.size >= roomSize) ThrowBusinessException("房间人数已满")
      roomMember += playerId
      notifyRoomChange()
    }

    def removeMember(playerId: String, exitType: Int = ExitTypeEnum.LEAVE): Unit = {
      roomMember -= playerId
      if (playerId == leaderId) {
        val nextLeader = roomMember.toArray.apply(Random.nextInt(roomMember.size))
        changeLeader(nextLeader)
      }
      // 为了性能不会在解散队伍移除成员的时候通知其他人队伍成员变化
      if (!Seq(ExitTypeEnum.DISBAND).contains(exitType)) {
        notifyRoomChange()
        // 通知被踢出的成员新的房间信息，因为每次调用都会重新获取服务器上的所有房间的新信息，比较耗性能，所以解散时要用同一个数据进行刷新,避免重复获取
        BaseGameRoomHolder.refreshRoomInfo(playerId)
      }
      PlayerChannels.send(playerId, Message(CmdType.BASE_GAME_LEAVE_ROOM, null))
    }

    private def notifyRoomChange(): Unit = {
      roomMember.foreach(playerId => {
        PlayerChannels.send(playerId, Message(CmdType.BASE_GAME_CURRENT_ROOM_CHANGE, info))
      })
    }

    private def info: MessageBody = {
      val memberInfo = roomMember.map(playerId => {
        val player = PlayerHolder.getPlayer(playerId)
        player.baseInfoStr()
      }).mkString("|")
      MessageBody("roomId" -> id, "roomName" -> roomName, "members" -> memberInfo, "leaderId" -> leaderId)
    }

    def infoStr: String = {
      info.toSeq.map { case (key, any) =>
        s"$key=${any.toString}"
      }.mkString("-")
    }

    def changeLeader(targetId: String): Unit = {
      if (!roomMember.contains(targetId)) ThrowBusinessException("目标玩家不存在")
      leaderId = targetId
      notifyRoomChange()
      PlayerChannels.alert(targetId, s"你成为了队伍 ${roomName} 的队长")
    }
  }

  private object Room extends AutoGrow {
    def apply(holderId: String, roomName: String): Room = {
      new Room(generateId(), holderId, roomName)
    }
  }
}
