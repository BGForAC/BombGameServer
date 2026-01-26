package com.example.message

import io.netty.buffer.{ByteBuf, Unpooled}

import java.nio.charset.StandardCharsets
import scala.collection.mutable

object Message {
  val MAX_LENGTH: Int = 1024 * 1024

  def fromByteBuf(byteBuf: ByteBuf): Message = {
    val size = byteBuf.capacity() - 4
    val command = byteBuf.readInt()
    val body = new Array[Byte](size)
    byteBuf.readBytes(body)
    println(s"Received message - Command: $command, Body Size: $size bytes")
    new Message(command, MessageBody.fromBytes(body))
  }
}

class Message(cmdTyp: Int,private var body: MessageBody) {
  def getCommand: Int = cmdTyp

  def response(messageBody: MessageBody): Message = {
    body = messageBody
    this
  }

  def toByteBuf: ByteBuf = {
    val byteBuf = Unpooled.buffer(4 + body.toBytes.length)
    byteBuf.writeInt(cmdTyp)
    byteBuf.writeBytes(body.toBytes)
    byteBuf
  }

  def getString(key: String): String = {
    body.getOrElse(key, "").asInstanceOf[String]
  }

  def getInt(key: String): Int = {
    body.getOrElse(key, 0).asInstanceOf[Int]
  }

  def getLong(key: String): Long = {
    body.getOrElse(key, 0L).asInstanceOf[Long]
  }

  def getDouble(key: String): Double = {
    body.getOrElse(key, 0.0).asInstanceOf[Double]
  }

  def getBoolean(key: String): Boolean = {
    body.getOrElse(key, false).asInstanceOf[Boolean]
  }

  def getBody: MessageBody = body
}

object MessageBody {
  def fromBytes(bytes: Array[Byte]): MessageBody = {
    val formatString = new String(bytes, StandardCharsets.UTF_8)
    formatString.split(";").map(kv => {
      val pair = kv.split("=", 2)
      (pair(0), pair(1))
    }).foldLeft(new MessageBody) { (mb, kv) =>
      mb.put(kv._1, kv._2)
      mb
    }
  }

  def apply(elems: (String, Any)*): MessageBody = {
    val mb = new MessageBody
    elems.foreach { case (k, v) => mb.put(k, v) }
    mb
  }
}

class MessageBody extends mutable.HashMap[String, Any] {
  def toBytes: Array[Byte] = {
    val formatString = this.map { case (k, v) => s"$k=$v" }.mkString(";")
    formatString.getBytes(StandardCharsets.UTF_8)
  }
}
