package com.example.utils

object ClassUtil {
  def getClassesInPackage(packageName: String): Set[Class[_]] = {
    val classLoader = Thread.currentThread().getContextClassLoader
    val path = packageName.replace('.', '/')
    val resources = classLoader.getResources(path)
    val classes = scala.collection.mutable.Set[Class[_]]()
    while (resources.hasMoreElements) {
      val resource = resources.nextElement()
      val file = new java.io.File(resource.getFile)
      if (file.isDirectory) {
        file.listFiles().foreach { f =>
          if (f.getName.endsWith(".class")) {
            val className = s"$packageName.${f.getName.stripSuffix(".class")}"
            classes += Class.forName(className)
          }
        }
      }
    }
    classes.toSet
  }
}
