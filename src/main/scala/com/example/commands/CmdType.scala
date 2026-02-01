package com.example.commands

object CmdType {
  val LOGIN = 0x0101
  val HEARTBEAT = 0x0102
  val INVALID = 0x01FF

  val ENTER_SCENE = 0x0201
  val EXIT_SCENE = 0x0202

  val MOVE = 0x0301

  val ENTER_BASE_GAME = 0x0401
}
