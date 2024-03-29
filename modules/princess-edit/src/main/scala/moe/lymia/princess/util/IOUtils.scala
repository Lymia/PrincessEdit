/*
 * Copyright (c) 2017-2022 Lymia Alusyia <lymia@lymiahugs.com>
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
import java.net.{URI, URL}
import java.nio.channels.{FileChannel, OverlappingFileLockException}
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Codec

final class FileLock(lockFile: Path) {
  private val channel = FileChannel.open(lockFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
  private val lock    = try {
    Option(channel.tryLock)
  } catch {
    case e: OverlappingFileLockException => None
  }
  private var released = false

  val acquired: Boolean = lock.isDefined
  def release(): Unit = if(!released) {
    lock.foreach(_.release)
    channel.close()
    released = true
  }
  if(lock.isEmpty) release()
}

object IOUtils {
  private val resPath = "/moe/lymia/princess/"

  def getResourceURL(s: String): URL = getClass.getResource(resPath + s)
  def getResource(s: String): InputStream = getClass.getResourceAsStream(resPath + s)
  def resourceExists(s: String): Boolean = getResource(s) != null
  def loadFromStream(s: InputStream): String = scala.io.Source.fromInputStream(s)(Codec.UTF8).mkString
  def loadBinaryFromStream(s: InputStream): Array[Byte] =
    Stream.continually(s.read).takeWhile(_ != -1).map(_.toByte).toArray
  def loadResource(s: String): String = loadFromStream(getResource(s))
  def loadBinaryResource(s: String): Array[Byte] = loadBinaryFromStream(getResource(s))

  def hideFile(path: Path): Path =
    Files.setAttribute(path, "dos:hidden", true)
  def mapFileName(path: Path, mapFn: String => String): Path =
    path.toAbsolutePath.getParent.resolve(mapFn(path.getFileName.toString))

  def writeFile(path: Path, data: Array[Byte]): Unit = {
    if(path.getParent != null) Files.createDirectories(path.getParent)
    Files.write(path, data)
  }
  def writeFile(path: Path, data: String): Unit = writeFile(path, data.getBytes(StandardCharsets.UTF_8))

  def getInputStreamReader(in: InputStream) = new InputStreamReader(in, StandardCharsets.UTF_8)
  def getFileReader(path: Path): InputStreamReader = getInputStreamReader(Files.newInputStream(path))
  def readFileAsBytes(path: Path): Array[Byte] = Files.readAllBytes(path)
  def readFileAsString(path: Path) = new String(readFileAsBytes(path), StandardCharsets.UTF_8)

  def list(path: Path): List[Path] = {
    val stream = Files.list(path)
    try {
      stream.iterator().asScala.toList
    } finally {
      stream.close()
    }
  }

  def openZip(path: Path, create: Boolean = false): FileSystem = {
    val canonical = path.toAbsolutePath
    FileSystems.newFileSystem(URI.create(s"jar:${path.toUri}"),
      if(create) Map("create" -> "true").asJava else Map[String, Any]().asJava)
  }

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

  def deleteDirectory(path: Path): Any =
    if(Files.exists(path))
      Files.walkFileTree(path, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = if(exc == null) {
          Files.delete(dir)
          FileVisitResult.CONTINUE
        } else throw exc
      })

  def withTemporaryFile[T](prefix: String = "princess-edit-tmp-", extension: String = "tmp")(f: Path => T): T = {
    val tempFile = File.createTempFile(prefix, s".$extension")
    try {
      f(tempFile.toPath)
    } finally {
      if(!tempFile.delete() && tempFile.exists()) tempFile.deleteOnExit()
    }
  }

  def withTemporaryDirectory[T](prefix: String = "princess-edit-tmp-")(f: Path => T): T = {
    val tempDir = Files.createTempDirectory(prefix)
    try {
      f(tempDir)
    } finally {
      deleteDirectory(tempDir)
    }
  }

  def lock(lockFile: Path): Option[FileLock] = {
    val lock = new FileLock(lockFile)
    if(!lock.acquired) None else Some(lock)
  }
  def withLock[T](lockFile: Path, error: => T = sys.error("Could not acquire lock."))(f: => T): T =
    lock(lockFile) match {
      case None => error
      case Some(lock) => try {
        f
      } finally {
        lock.release()
      }
    }
}