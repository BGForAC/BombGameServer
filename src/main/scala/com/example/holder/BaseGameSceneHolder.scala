package com.example.holder

import com.example.commands.CmdType
import com.example.exception.ThrowBusinessException
import com.example.message.{Message, MessageBody}
import com.example.scene.BaseGameScene
import com.example.serer.PlayerChannels

import scala.collection.mutable
import scala.util.Random

object BaseGameSceneHolder {
  // key: mapId 0-随机 其他-固定地图
  // value: MatchQueue
  private val matchQueues: mutable.Map[Int, MatchQueue] = mutable.Map.empty

  private val minRoomSize = 1
  private val maxRoomSize = 4
  private val tickInterval = 50
  private val mapCnt = 1
  private val baseSceneId = 1

  // 暂时不考虑玩家加入多个地图匹配队列的情况
  def addToMatchQueue(playerId: String, mapId: Int): Unit = {
    if (mapId < 0 || mapId > mapCnt) {
      ThrowBusinessException(s"地图ID[$mapId]无效，加入匹配队列失败")
      return
    }
    val queue = matchQueues.getOrElseUpdate(mapId, new MatchQueue(mapId))
    queue.addToMatchQueue(playerId)
  }

  def removeFromMatchQueue(playerId: String): Unit = {
    matchQueues.values.foreach(_.removeFromMatchQueue(playerId))
  }

  def tick(tickIdx: Long): Unit = {
    matchQueues.values.foreach(_.tick(tickIdx))
  }

  private class MatchQueue(mapId: Int) {
    private val matchQueue: mutable.ListBuffer[String] = mutable.ListBuffer.empty

    def addToMatchQueue(playerId: String): Unit = {
      if (!matchQueue.contains(playerId)) {
        matchQueue += playerId
        println(s"玩家[$playerId]加入匹配队列")
      }
    }

    def removeFromMatchQueue(playerId: String): Unit = {
      if (matchQueue.contains(playerId)) {
        matchQueue -= playerId
        println(s"玩家[$playerId]离开匹配队列")
      }
    }

    def tick(tickIdx: Long): Unit = {
      if (tickIdx % tickInterval == 0) {
        while (matchQueue.lengthCompare(minRoomSize) >= 0) {
          val roomSize = Math.min(maxRoomSize, matchQueue.length)
          val playerIds = matchQueue.take(roomSize)
          val players = playerIds.map(id => (id, PlayerHolder.getPlayer(id)))
          val offLinePlayers = players.filter(_._2 == null)
          if (offLinePlayers.nonEmpty) {
            offLinePlayers.foreach { case (playerId, _) =>
              println(s"玩家[$playerId]已离线，移出匹配队列")
              removeFromMatchQueue(playerId)
            }
          } else {
            matchQueue.remove(0, roomSize)
            val actualMapId = if (mapId == 0) {
              Random.nextInt(mapCnt) + baseSceneId
            } else mapId
            val scene = SceneHolder.createScene(actualMapId)
            players.foreach { case (_, player) =>
              SceneHolder.enterScene(scene.id, player)
            }

            val playerIdxInfo = scene.asInstanceOf[BaseGameScene].playerIdxInfo

            if (playerIdxInfo.size != players.size) {
              println(s"玩家索引信息数量[${playerIdxInfo.size}]与玩家数量[${players.size}]不匹配")
              players.foreach { case (playerId, _) =>
                PlayerChannels.sendError(playerId, "匹配失败，有玩家没有成功进入场景")
              }
              return
            }

            val playersInfo = playerIdxInfo.toSeq.sortBy(_._2).map{ case (playerId, idx) =>
              val player = PlayerHolder.getPlayer(playerId)
              player.baseInfo.map{case (k, v) => s"$k:$v"}.mkString(",")
            }.mkString("|")
            players.foreach { case (_, player) =>
              PlayerChannels.send(player.id, Message(CmdType.ENTER_BASE_GAME, MessageBody(
                Seq("mapId" -> actualMapId, "playersInfo" -> playersInfo): _*))
              )
            }

            println(s"匹配成功，玩家[${players.mkString(",")}]进入场景")
          }
        }
      }
    }

  }
}
