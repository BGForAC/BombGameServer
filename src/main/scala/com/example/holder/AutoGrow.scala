package com.example.holder

trait AutoGrow {
  var id: Int = 0

  def generateId() = {
    id += 1
    id
  }
}
