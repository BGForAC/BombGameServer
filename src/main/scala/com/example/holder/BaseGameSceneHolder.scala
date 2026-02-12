package com.example.holder

import com.example.exception.ThrowBusinessException

import scala.collection.mutable
import scala.util.Random

object BaseGameSceneHolder {
  // key: mapId 0-随机 其他-固定地图
  // value: MatchQueue
  private val matchQueues: mutable.Map[Int, MatchQueue] = mutable.Map.empty

  private val minRoomSize = 2
  private val maxRoomSize = 4
  private val tickInterval = 50
  private val mapCnt = 3
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
              Random.nextInt(mapCnt) + 1
            } else mapId
            val scene = SceneHolder.createScene(baseSceneId + actualMapId - 1)
            for (player <- players.map(_._2)) {
              SceneHolder.enterScene(scene.id, player)
            }

            println(s"匹配成功，玩家[${players.mkString(",")}]进入场景")
          }
        }
      }
    }

  }
}
