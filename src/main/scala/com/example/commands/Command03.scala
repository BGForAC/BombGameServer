package com.example.commands

object Command03 extends IPlayerCommand {
  def handler01(playerId: String, message: com.example.message.Message): Unit = {
    val x = message.getInt("x")
    val y = message.getInt("y")
    val z = message.getInt("z")
    val angle = message.getInt("angle")
    val player = com.example.holder.PlayerHolder.getPlayer(playerId)
    player.movement.setPosition((x, y, z, angle))
  }
}
