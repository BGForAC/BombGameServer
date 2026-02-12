package com.example.exception

import com.example.message.{Message, MessageBody}
import scala.util.control.NoStackTrace

object ThrowBusinessException {
  def apply(message: String, exceptionType: Int = ExceptionType.DEFAULT): Nothing = {
    throw new BusinessException(message, exceptionType)
  }
}

class BusinessException(message: String, exceptionType: Int = ExceptionType.DEFAULT) extends RuntimeException with NoStackTrace {
  private val errMessage = MessageBody("msg" -> message, "errType" -> exceptionType.toString)

  def toMessageBody: MessageBody = {
    errMessage
  }
}
