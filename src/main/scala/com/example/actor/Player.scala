package com.example.actor

class Player(pid: String) extends Actor(pid) {
  def baseInfo: Seq[(String, Any)] = {
    movement.info ++ attr.info
  }
}