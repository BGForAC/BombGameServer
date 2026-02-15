package com.example.exception

import com.example.message.{ErrorMessage, Message}

import scala.util.control.NoStackTrace

object ThrowBusinessException {
  def apply(message: String, exceptionType: Int = ExceptionType.DEFAULT): Nothing = {
    throw new BusinessException(message, exceptionType)
  }
}

class BusinessException(message: String, exceptionType: Int = ExceptionType.DEFAULT) extends RuntimeException with NoStackTrace {
  def toMessage: Message = {
    ErrorMessage(message, exceptionType)
  }
}
