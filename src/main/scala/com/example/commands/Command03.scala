package com.example.commands

import com.example.holder.PlayerHolder

object Command03 extends IPlayerCommand {
  def handler01(playerId: String, message: com.example.message.Message): Unit = {
    val x = message.getInt("x")
    val y = message.getInt("y")
    val z = message.getInt("z")
    val angle = message.getFloat("angle")
    val player = PlayerHolder.getPlayer(playerId)
    player.movement.setPosition((x, y, z, angle))
  }
}
