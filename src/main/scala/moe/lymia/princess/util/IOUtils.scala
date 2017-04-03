/*
 * Copyright (c) 2017 Lymia Alusyia <lymia@lymiahugs.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package moe.lymia.princess.util

import java.io.{File, IOException, InputStream, InputStreamReader}
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

import scala.collection.JavaConverters._
import scala.io.Codec

final class FileLock(lockFile: Path) {
  private val channel  = FileChannel.open(lockFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
  private val lock     = Option(channel.tryLock)
  private var released = false

  val acquired = lock.isDefined
  def release() = if(!released) {
    lock.foreach(_.release)
    channel.close()
    released = true
  }
  if(lock.isEmpty) release()
}

object IOUtils {
  private val resPath = "/moe/lymia/princess/"

  def getResourceURL(s: String) = getClass.getResource(resPath + s)
  def getResource(s: String) = getClass.getResourceAsStream(resPath + s)
  def resourceExists(s: String) = getResource(s) != null
  def loadFromStream(s: InputStream) = scala.io.Source.fromInputStream(s)(Codec.UTF8).mkString
  def loadBinaryFromStream(s: InputStream) = Stream.continually(s.read).takeWhile(_ != -1).map(_.toByte).toArray
  def loadResource(s: String) = loadFromStream(getResource(s))
  def loadBinaryResource(s: String) = loadBinaryFromStream(getResource(s))

  def writeFile(path: Path, data: Array[Byte]): Unit = {
    if(path.getParent != null) Files.createDirectories(path.getParent)
    Files.write(path, data)
  }
  def writeFile(path: Path, data: String): Unit = writeFile(path, data.getBytes(StandardCharsets.UTF_8))

  def getInputStreamReader(in: InputStream) = new InputStreamReader(in, StandardCharsets.UTF_8)
  def getFileReader(path: Path) = getInputStreamReader(Files.newInputStream(path))
  def readFileAsBytes(path: Path) = Files.readAllBytes(path)
  def readFileAsString(path: Path) = new String(readFileAsBytes(path), StandardCharsets.UTF_8)

  def list(path: Path) = {
    val stream = Files.list(path)
    try {
      stream.iterator().asScala.toList
    } finally {
      stream.close()
    }
  }

  def openZip(path: Path, create: Boolean = false): FileSystem =
    FileSystems.newFileSystem(URI.create(s"jar:${path.toUri}"),
      if(create) Map("create" -> "true").asJava else Map().asJava, getClass.getClassLoader)

  private val validFilenameRegex = Pattern.compile("^[- 0-9a-zA-Z_./]+$")
  def paranoidResolve(basePath: Path, path: String, dir: Boolean = false): Option[Path] =
    if(!validFilenameRegex.matcher(path).matches()) None
    else {
      val splitPath = path.split("/")
      if(splitPath.exists(x => x.isEmpty || x.contains(".."))) None
      else {
        var currentPath = basePath
        var error = false
        for(elem <- path.split("/")) if (!error && elem != ".")
          if(Files.exists(currentPath) && Files.isDirectory(currentPath) &&
             list(currentPath).exists(x => x.getFileName.toString.replace("/", "") == elem))
            currentPath = currentPath.resolve(elem)
          else error = true
        if(error || !Files.exists(currentPath) ||
           (if(dir) !Files.isDirectory(currentPath) else !Files.isRegularFile(currentPath))) None
        else Some(currentPath)
      }
    }

  def deleteDirectory(path: Path) =
    if(Files.exists(path))
      Files.walkFileTree(path, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes) = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def visitFileFailed(file: Path, exc: IOException) = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException) = if(exc == null) {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        } else throw exc
      })

  def withTemporaryFile[T](prefix: String = "princess-edit-tmp-", extension: String = "tmp")(f: Path => T) = {
    val tempFile = File.createTempFile(prefix, s".$extension")
    try {
      f(tempFile.toPath)
    } finally {
      if(!tempFile.delete() && tempFile.exists()) tempFile.deleteOnExit()
    }
  }

  def withTemporaryDirectory[T](prefix: String = "princess-edit-tmp-")(f: Path => T) = {
    val tempDir = Files.createTempDirectory(prefix)
    try {
      f(tempDir)
    } finally {
      deleteDirectory(tempDir)
    }
  }

  def lock(lockFile: Path) = {
    val lock = new FileLock(lockFile)
    if(!lock.acquired) None else Some(lock)
  }
  def withLock[T](lockFile: Path, error: => T = sys.error("Could not acquire lock."))(f: => T) =
    lock(lockFile) match {
      case None => error
      case Some(lock) => try {
        f
      } finally {
        lock.release()
      }
    }
}