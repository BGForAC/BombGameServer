package com.example

import com.example.serer.GameServer
import com.example.thread.EventThread


object Main {
  def main(args: Array[String]): Unit = {
    GameServer.run()
    EventThread.run()
  }
}
