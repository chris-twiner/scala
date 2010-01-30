/* NSC -- new Scala compiler
 * Copyright 2005-2010 LAMP/EPFL
 * @author Paul Phillips
 */

package scala.tools.nsc
package interpreter

import java.io.File
import java.lang.reflect
import java.util.jar.{ JarEntry, JarFile }
import java.util.concurrent.ConcurrentHashMap
import util.ScalaClassLoader.getSystemLoader

object ByteCode {
  /** Until I figure out why I can't get scalap onto the classpath such
   *  that the compiler will bootstrap, we have to use reflection.
   */
  private lazy val DECODER: Option[AnyRef] =
    for (clazz <- getSystemLoader.tryToLoadClass[AnyRef]("scala.tools.scalap.Decode$")) yield
      clazz.getField("MODULE$").get()

  private def decoderMethod(name: String, args: Class[_]*): Option[reflect.Method] = {
    for (decoder <- DECODER ; m <- Option(decoder.getClass.getMethod(name, args: _*))) yield m
  }

  private lazy val aliasMap = {
    for (module <- DECODER ; method <- decoderMethod("typeAliases", classOf[String])) yield
      method.invoke(module, _: String).asInstanceOf[Option[Map[String, String]]]
  }

  /** Attempts to retrieve case parameter names for given class name.
   */
  def caseParamNamesForPath(path: String) =
    for {
      module <- DECODER
      method <- decoderMethod("caseParamNames", classOf[String])
      names <- method.invoke(module, path).asInstanceOf[Option[List[String]]]
    }
    yield names

  def aliasesForPackage(pkg: String) = aliasMap flatMap (_(pkg))

  /** Attempts to find type aliases in package objects.
   */
  def aliasForType(path: String): Option[String] = {
    val (pkg, name) = (path lastIndexOf '.') match {
      case -1   => return None
      case idx  => (path take idx, path drop (idx + 1))
    }
    aliasesForPackage(pkg) flatMap (_ get name)
  }
}