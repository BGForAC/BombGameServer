package com.example.utils

import java.net.URLDecoder

/**
 * ClassUtil 工具类，用于获取指定包下的所有类
 */
object ClassUtil {
  /**
   * 获取指定包名下的所有类
   * @param packageName 包名，例如 "com.example.utils"
   * @return 包中所有类的集合，类型为 Set[Class[_]]
   */
  def getClassesInPackage(packageName: String): Set[Class[_]] = {
    // 获取当前线程的上下文类加载器
    val classLoader = Thread.currentThread().getContextClassLoader
    // 将包名转换为路径格式
    val path = packageName.replace('.', '/')
    // 使用类加载器获取包路径下的所有资源
    val resources = classLoader.getResources(path)
    // 创建一个可变的集合来存储找到的类
    val classes = scala.collection.mutable.Set[Class[_]]()
    // 遍历所有资源
    while (resources.hasMoreElements) {
      val resource = resources.nextElement()
      // 使用 URLDecoder 解码文件路径，处理 URL 编码字符
      val decodedPath = URLDecoder.decode(resource.getFile, "UTF-8")
      val file = new java.io.File(decodedPath)
      // 如果资源是目录，则遍历其中的文件
      if (file.isDirectory) {
        file.listFiles().foreach { f =>
          // 查找所有的.class文件
          if (f.getName.endsWith(".class")) {
            // 构建完整的类名
            val className = s"$packageName.${f.getName.stripSuffix(".class")}"
            // 加载类并添加到集合中
            classes += Class.forName(className)
          }
        }
      }
    }
    // 将可变集合转换为不可变集合返回
    classes.toSet
  }
}
