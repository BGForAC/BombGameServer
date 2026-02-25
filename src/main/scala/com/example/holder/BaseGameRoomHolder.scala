package com.example.holder

import com.example.commands.CmdType
import com.example.exception.ThrowBusinessException
import com.example.message.{Message, MessageBody}
import com.example.serer.PlayerChannels

import scala.collection.mutable
import scala.util.Random

object BaseGameRoomHolder {
  private val playerId2Room: mutable.Map[String, Room] = mutable.Map.empty
  private val rooms: mutable.Map[Int, Room] = mutable.Map.empty
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

  def reqRemoveRoom(playerId: String): Unit = {
    playerId2Room.get(playerId) match {
      case Some(room) => {
        if (room.leaderId != playerId) ThrowBusinessException("只有房主才能移除房间")
        removeRoom(room.id)
      }
      case None => ThrowBusinessException("玩家不在任何房间中")
    }
  }

  private def removeRoom(roomId: Int): Unit = {
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

  def leaveRoom(playerId: String): Unit = {
    playerId2Room.get(playerId) match {
      case Some(room) => room.removeMember(playerId)
      case None => ThrowBusinessException("你不在房间中")
    }
  }

  def kickPlayer(targetId: String, sourceId: String) = {
    playerId2Room.get(targetId) match {
      case Some(room) => {
        if (room.leaderId != sourceId) ThrowBusinessException("只有房主才能踢人")
        room.removeMember(targetId, ExitTypeEnum.KICK)
      }
      case None => ThrowBusinessException("房间不存在")
    }
  }

  def ready(playerId: String): Unit = {
    playerId2Room.get(playerId) match {
      case Some(room) => room.ready(playerId)
      case None => ThrowBusinessException("玩家不在任何房间中")
    }
  }

  def changeCareer(playerId: String, career: String): Unit = {
    playerId2Room.get(playerId) match {
      case Some(room) => room.changeCareer(playerId, career)
      case None => ThrowBusinessException("玩家不在任何房间中")
    }
  }

  def changeLeader(sourceId: String, targetId: String): Unit = {
    playerId2Room.get(targetId) match {
      case Some(room) => {
        if (sourceId != room.leaderId) ThrowBusinessException("只有房主才能更换房主")
        room.changeLeader(targetId)
      }
      case None => ThrowBusinessException("目标不在任何房间中")
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

  private class RoomMember(val id: String, room: Room) {
    var isReady = false
    val player = PlayerHolder.getPlayer(id)
    player.career = "Balance"

    def info: String = {
      player.baseInfoStr(Seq(("isReady" -> isReady)))
    }

    def changeCareer(career: String): Unit = {
      player.career = career
    }

    def changeStatus(): Unit = {
      isReady = !isReady
    }
  }

  private object RoomMember {
    def apply(playerId: String, room: Room) = {
      new RoomMember(playerId, room)
    }
  }

  private class Room(val id: Int, val roomName: String) {
    private val roomMember = mutable.Map.empty[String, RoomMember]

    var leaderId: String = _
    private var leaderName: String = _

    private val roomSize: Int = 4

    private def setLeader(leaderId: String) = {
      this.leaderId = leaderId
      leaderName = PlayerHolder.getPlayer(leaderId).uname
    }

    def tick(tickIdx: Long): Unit = {
      roomMember.keys.foreach(playerId => {
        if (!PlayerHolder.isOnline(playerId)) removeMember(playerId)
      })
      if (roomMember.size == 0) BaseGameRoomHolder.removeRoom(id)
    }

    def beforeRemove(): Unit = {
      val ids = mutable.ListBuffer.empty[String]
      roomMember.keys.foreach({ id =>
        ids += id
        removeMember(id, ExitTypeEnum.DISBAND)
      })
      BaseGameRoomHolder.refreshRoomInfo(ids: _*)
    }

    def addMember(playerId: String): Unit = {
      if (roomMember.size >= roomSize) ThrowBusinessException("房间人数已满")
      roomMember += (playerId -> RoomMember(playerId, this))
      playerId2Room += playerId -> this
      PlayerChannels.send(playerId, Message(CmdType.BASE_GAME_JOIN_ROOM, MessageBody("result" -> "success", "info" -> infoStr)))
      notifyRoomChange()
    }

    def removeMember(playerId: String, exitType: Int = ExitTypeEnum.LEAVE): Unit = {
      roomMember -= playerId
      playerId2Room -= playerId
      if (playerId == leaderId) {
        val nextLeader = roomMember.keys.toArray.apply(Random.nextInt(roomMember.size))
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

    def ready(playerId: String): Unit = {
      roomMember.get(playerId) match {
        case Some(member) => {
          member.changeStatus()
          notifyRoomChange()
        }
        case None => ThrowBusinessException("玩家不在队伍中")
      }
    }

    def changeCareer(playerId: String, career: String): Unit = {
      roomMember.get(playerId) match {
        case Some(member) => {
          member.changeCareer(career)
          notifyRoomChange()
        }
        case None => ThrowBusinessException("玩家不在队伍中")
      }
    }

    private def notifyRoomChange(): Unit = {
      roomMember.keys.foreach(playerId => {
        PlayerChannels.send(playerId, Message(CmdType.BASE_GAME_CURRENT_ROOM_CHANGE, info))
      })
    }

    private def info: MessageBody = {
      val memberInfo = roomMember.values.map(_.info).mkString("|")
      MessageBody("roomId" -> id, "roomName" -> roomName, "members" -> memberInfo, "leaderId" -> leaderId,
        "leaderName" -> leaderName, "memberCnt" -> roomMember.size)
    }

    def infoStr: String = {
      info.toSeq.map { case (key, any) =>
        s"$key?${any.toString}"
      }.mkString("-")
    }

    def changeLeader(targetId: String): Unit = {
      if (!roomMember.contains(targetId)) ThrowBusinessException("目标玩家不存在")
      setLeader(targetId)
      notifyRoomChange()
      PlayerChannels.alert(targetId, s"你成为了队伍 ${roomName} 的队长")
    }
  }

  private object Room extends AutoGrow {
    def apply(leaderId: String, roomName: String): Room = {
      val room = new Room(generateId(), roomName)
      room.setLeader(leaderId)
      room
    }
  }
}
