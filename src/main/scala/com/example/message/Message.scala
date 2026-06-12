package com.example.message

import com.example.commands.CmdType
import com.example.exception.ExceptionType
import io.netty.buffer.{ByteBuf, Unpooled}

import java.nio.charset.StandardCharsets
import scala.collection.mutable

/**
 * ErrorMessage 对象，用于创建错误消息
 * 提供了一个工厂方法来创建带有错误信息的消息
 */
object ErrorMessage {
  /**
   * 创建错误消息
   * @param message 错误信息字符串
   * @param exceptionType 异常类型，默认为 ExceptionType.DEFAULT
   * @return 返回一个 Message 对象
   */
  def apply(message: String, exceptionType: Int = ExceptionType.DEFAULT): Message = {
    Message(CmdType.INVALID, MessageBody("msg" -> message, "errType" -> exceptionType))
  }
}

/**
 * Message 对象，用于消息的创建和处理
 * 提供了消息的序列化和反序列化方法
 */
object Message {
  // 消息的最大长度限制，1MB
  val MAX_LENGTH: Int = 1024 * 1024

  /**
   * 从 ByteBuf 创建消息
   * @param byteBuf 包含消息数据的 ByteBuf
   * @return 返回解析后的 Message 对象
   */
  def fromByteBuf(byteBuf: ByteBuf): Message = {
    val size = byteBuf.capacity() - 4
    val command = byteBuf.readInt()
    val body = new Array[Byte](size)
    byteBuf.readBytes(body)
    new Message(command, MessageBody.fromBytes(body))
  }

  /**
   * 创建消息的工厂方法
   * @param cmdTyp 命令类型
   * @param body 消息体
   * @return 返回一个新的 Message 对象
   */
  def apply(cmdTyp: Int, body: MessageBody): Message = {
    new Message(cmdTyp, body)
  }
}

/**
 * Message 类，表示一个完整的消息
 * @param cmdTyp 命令类型
 * @param body 消息体
 */
class Message(cmdTyp: Int,private var body: MessageBody) {
  /**
   * 获取命令类型
   * @return 返回命令类型的整数值
   */
  def getCommand: Int = cmdTyp

  /**
   * 设置响应消息体
   * @param messageBody 新的消息体
   * @return 返回当前 Message 对象
   */
  def response(messageBody: MessageBody): Message = {
    body = messageBody
    this
  }

  /**
   * 将消息转换为 ByteBuf
   * @return 包含消息数据的 ByteBuf
   */
  def toByteBuf: ByteBuf = {
    val byteBuf = Unpooled.buffer(4 + body.toBytes.length)
    byteBuf.writeInt(cmdTyp)
    byteBuf.writeBytes(body.toBytes)
    byteBuf
  }

  /**
   * 从消息体中获取字符串值
   * @param key 键名
   * @return 返回对应的字符串值
   */
  def getString(key: String): String = {
    body.getOrElse(key, "").asInstanceOf[String]
  }

  /**
   * 从消息体中获取整数值
   * @param key 键名
   * @return 返回对应的整数值
   */
  def getInt(key: String): Int = {
    body.getOrElse(key, "0").asInstanceOf[String].toFloat.toInt
  }

  /**
   * 从消息体中获取长整数值
   * @param key 键名
   * @return 返回对应的长整数值
   */
  def getLong(key: String): Long = {
    body.getOrElse(key, "0").asInstanceOf[String].toFloat.toLong
  }

  /**
   * 从消息体中获取浮点数值
   * @param key 键名
   * @return 返回对应的浮点数值
   */
  def getFloat(key: String): Float = {
    body.getOrElse(key, "0").asInstanceOf[String].toFloat
  }

  /**
   * 从消息体中获取双精度浮点数值
   * @param key 键名
   * @return 返回对应的双精度浮点数值
   */
  def getDouble(key: String): Double = {
    body.getOrElse(key, "0").asInstanceOf[String].toDouble
  }

  /**
   * 从消息体中获取布尔值
   * @param key 键名
   * @return 返回对应的布尔值
   */
  def getBoolean(key: String): Boolean = {
    body.getOrElse(key, "false").asInstanceOf[String].toBoolean
  }

  /**
   * 获取消息体
   * @return 返回 MessageBody 对象
   */
  def getBody: MessageBody = body

  /**
   * 检查消息体中是否包含指定键
   */
  def contains(key: String): Boolean = body.contains(key)
}

/**
 * MessageBody 对象，用于消息体的创建和处理
 * 提供了消息体的序列化和反序列化方法
 */
object MessageBody {
  /**
   * 从字节数组创建消息体
   * @param bytes 包含消息体数据的字节数组
   * @return 返回解析后的 MessageBody 对象
   */
  def fromBytes(bytes: Array[Byte]): MessageBody = {
    val formatString = new String(bytes, StandardCharsets.UTF_8)
    if (formatString.isEmpty) {
      return new MessageBody
    }
    fromJson(formatString)
  }

  def addMessageBody(messageBody: MessageBody, oldMessageBody : MessageBody ): MessageBody = {
    oldMessageBody.foreach { case (key, value) =>
      messageBody.put(key, value)
    }
    messageBody
  }

  def main(args: Array[String]): Unit = {
//    val jsonString = """{"key1":"value1","key2":{"subKey1":"subValue1","subKey2":"subValue2"},"key3":"value3"}"""
//    val messageBody = fromJson(jsonString)
//    println(messageBody)
//    println(messageBody.toJsonString)
/*    val x = MessageBody(
      {"key1" -> "value1"},
      {"key2" -> "value2"},
      {"key3" -> MessageBody(
        {"subKey1" -> MessageBody(
          {"subKey1.1" -> "subValue1.1"},
          {"subKey1.2" -> "subValue1.2"}
        )},
        {"subKey2" -> "subValue2"}
      )}
    )
    val y = MessageBody(
      {"keyy1" -> "valuey1"},
      {"keyy2" -> "valuey2"},
      {"keyy3" -> MessageBody(
        {"subKeyy1" -> "subValuey1"},
        {"subKeyy2" -> "subValuey2"}
      )}
    )

    println(x.toJsonString)
    println(y.toJsonString)
    println(addMessageBody(x,y).toJsonString)
    println(x.toJsonString)
    var aa = Message.fromByteBuf(Message(CmdType.INVALID, x).toByteBuf)
    println(aa.getBody.toJsonString)*/


/*    val a = MessageBody.fromBytes(x.toBytes).toJsonString

    println(a)*/


    //println(initMap("1").toJsonString)
    


  }


  def initMap(typ: String): MessageBody = {
    // 构建属性文件路径
    val filePath = s"map/$typ.json"
    // 从类加载器中获取属性文件
    val inputStream = getClass.getClassLoader.getResourceAsStream(filePath)
    inputStream match {
      case null =>
        throw new RuntimeException(s"属性文件[$filePath]不存在")
      case inputStream =>
        try {
          // 读取JSON内容
          val content = scala.io.Source.fromInputStream(inputStream).mkString
          // 使用已有的fromJson方法解析JSON
          val messageBody = fromJson(content)
          messageBody
        } finally {
          inputStream.close()
        }
    }
  }



  // 数据是json类型的字符串，只有字符串或json类型的value
  def fromJson(jsonString: String): MessageBody = {
    // 去除 UTF-8 BOM (\uFEFF) 和首尾空白字符（换行、空格等）
    val cleaned = jsonString.stripPrefix("\uFEFF").trim
    if (!cleaned.startsWith("{") || !cleaned.endsWith("}")) {
      throw new RuntimeException(s"无效的消息体格式：$jsonString")
    }
    val mb = new MessageBody
    var content = cleaned.substring(1, cleaned.length - 1).trim
    if (content.isEmpty) {
      return mb
    }
    def findMatchBraceIndex(startIndex: Int, content: String): Int = {
      var count = 0
      for (i <- startIndex until content.length) {
        if (content.charAt(i) == '{') count += 1
        else if (content.charAt(i) == '}') count -= 1
        if (count == 0) return i
      }
      -1
    }
    while (content.nonEmpty) {
      val keyEndIndex = content.indexOf(":")
      if (keyEndIndex == -1) {
        throw new RuntimeException(s"无效的消息体格式：$jsonString")
      }
      val key = content.substring(0, keyEndIndex).trim.replaceAll("\"", "")
      content = content.substring(keyEndIndex + 1).trim
      if (content.startsWith("{")) {
        val valueEndIndex = findMatchBraceIndex(0, content)
        if (valueEndIndex == -1) {
          throw new RuntimeException(s"无效的消息体格式：$jsonString, 无法找到匹配的右括号, content: $content")
        }
        val value = content.substring(0, valueEndIndex + 1)
        mb.put(key, fromJson(value))
        content = if (valueEndIndex + 1 >= content.length) "" else content.substring(valueEndIndex + 2).trim
      } else {
        val valueEndIndex = content.indexOf(",") match {
          case -1 => content.length
          case idx => idx
        }
        val value = content.substring(0, valueEndIndex).trim.replaceAll("\"", "")
        mb.put(key, value)
        content = if (valueEndIndex == content.length) "" else content.substring(valueEndIndex + 1).trim
      }
    }
    mb
  }

  /**
   * 创建消息体的工厂方法
   * @param elems 键值对序列
   * @return 返回一个新的 MessageBody 对象
   */
  def apply(elems: (String, Any)*): MessageBody = {
    val mb = new MessageBody
    elems.foreach { case (k, v) => mb.put(k, v) }
    mb
  }
}

/**
 * MessageBody 类，继承自 mutable.HashMap，表示消息体
 * 使用键值对存储消息数据
 */
class MessageBody extends mutable.HashMap[String, Any] {
  /**
   * 从消息体中获取字符串值（兼容 Int/Float 等类型，统一 toString 转换）
   */
  def getString(key: String): String = getOrElse(key, "").toString

  /**
   * 从消息体中获取整数值（兼容字符串和数字类型）
   */
  def getInt(key: String): Int = getOrElse(key, "0").toString.toFloat.toInt

  /**
   * 从消息体中获取浮点数值（兼容字符串和数字类型）
   */
  def getFloat(key: String): Float = getOrElse(key, "0").toString.toFloat

  /**
   * 从消息体中获取布尔值
   */
  def getBoolean(key: String): Boolean = getOrElse(key, "false").toString.toBoolean

  /**
   * 将消息体转换为字节数组
   * 现在改为转为json类型的string
   * @return 包含消息体数据的字节数组
   */
  def toBytes: Array[Byte] = {
    val formatString = this.toJsonString
    formatString.getBytes(StandardCharsets.UTF_8)
  }

  def toJsonString: String = {
    val ret = new StringBuilder("{")
    this.foreach { case (k, v) =>
      v match {
        case body: MessageBody =>
          ret.append(s""""$k":${body.toJsonString}""")
        case _ =>
          ret.append(s""""$k":"${v.toString}"""")
      }
      ret.append(",")
    }
    if (this.nonEmpty) {
      ret.deleteCharAt(ret.length - 1) // 删除最后一个逗号
    }
    ret.append("}").toString()
  }
}
