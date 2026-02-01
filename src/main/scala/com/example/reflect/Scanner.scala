package com.example.reflect

import com.example.utils.ClassUtil

import scala.collection.mutable

trait Scanner[K, T] {
  val map: mutable.Map[K, T] = mutable.Map()

  def packageName: String

  def scan(): Unit = {
    val classes = ClassUtil.getClassesInPackage(packageName)
    for (cls <- classes) {
      try {
        val obj = cls.getDeclaredField("MODULE$").get(null)
        obj match {
          case s: ScanAble[K] =>
            s.keySet.foreach { key =>
              map += (key -> obj.asInstanceOf[T])
            }
          case _ =>

        }
      } catch {
        case _: Exception =>
      }
    }
  }
}
