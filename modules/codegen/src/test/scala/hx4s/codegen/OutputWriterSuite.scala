package hx4s.codegen

import hx4s.codegen.TestConfig.withTempDir
import munit.FunSuite

import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.Using

class OutputWriterSuite extends FunSuite {
  private val marker = "// generated-by-test"
  private val generated = s"$marker\nexport default 1;\n"

  private def names(root: Path): List[String] =
    Using.resource(Files.list(root))(_.iterator().asScala.map(_.getFileName.toString).toList.sorted)

  private def write(root: Path, files: (String, String)*) =
    OutputWriter.write(files.toList.map { case (path, content) => GeneratedFile(root, path, content) }, List(root), marker, root)

  test("regeneration deletes stale generated files, keeps hand-written files and returns counts") {
    withTempDir { root =>
      Files.writeString(root.resolve("stale.ts"), generated)
      Files.writeString(root.resolve("index.ts"), "export default manual;\n")
      Files.writeString(root.resolve("notes.md"), s"$marker\n")
      val summary = write(root, "fresh.ts" -> generated).toOption.get
      assert(!Files.exists(root.resolve("stale.ts")))
      assertEquals(Files.readString(root.resolve("index.ts")), "export default manual;\n")
      assert(Files.exists(root.resolve("notes.md")), "files without a generated extension are not deleted")
      assertEquals(Files.readString(root.resolve("fresh.ts")), generated)
      assertEquals((summary.written.size, summary.unchanged.size, summary.deleted.size), (1, 0, 1))
    }
  }

  test("identical content is not rewritten and the modification time stays; different line endings count as identical") {
    withTempDir { root =>
      val path = root.resolve("same.ts")
      val crlf = root.resolve("crlf.ts")
      Files.writeString(path, generated)
      Files.writeString(crlf, generated.replace("\n", "\r\n"))
      val old = FileTime.fromMillis(1_000_000_000_000L)
      Files.setLastModifiedTime(path, old)
      Files.setLastModifiedTime(crlf, old)
      val summary = write(root, "same.ts" -> generated, "crlf.ts" -> generated).toOption.get
      assertEquals(summary.unchanged.size, 2)
      assertEquals(summary.written, Nil)
      assertEquals(Files.getLastModifiedTime(path), old)
      assertEquals(Files.getLastModifiedTime(crlf), old)
    }
  }

  test("conflicts with hand-written files fail before deleting or writing") {
    withTempDir { root =>
      Files.writeString(root.resolve("stale.ts"), generated)
      Files.writeString(root.resolve("index.ts"), "handwritten")
      assert(write(root, "index.ts" -> generated).isLeft)
      assertEquals(Files.readString(root.resolve("index.ts")), "handwritten")
      assert(Files.exists(root.resolve("stale.ts")))
    }
  }

  test("paths differing only in case are rejected among outputs and against existing hand-written files and directories") {
    withTempDir { root =>
      assert(write(root, "a.ts" -> generated, "A.ts" -> generated).isLeft)
      Files.writeString(root.resolve("index.ts"), "handwritten")
      Files.createDirectories(root.resolve("auth"))
      Files.writeString(root.resolve("auth/manual.ts"), "handwritten")
      assert(write(root, "Index.ts" -> generated).isLeft)
      assert(write(root, "Auth/page.ts" -> generated).isLeft)
      assertEquals(Files.readString(root.resolve("index.ts")), "handwritten")
    }
  }

  test("a stale generated file differing only in case is deleted and written under the new name") {
    withTempDir { root =>
      Files.writeString(root.resolve("page.ts"), generated)
      write(root, "Page.ts" -> generated).toOption.get
      assertEquals(names(root), List("Page.ts"))
    }
  }

  test("paths outside the output root or to reserved names are rejected before writing") {
    withTempDir { root =>
      List("../x.ts", "a/../../x.ts", "/x.ts", "C:/x.ts", "a\\x.ts", "NUL.ts", "aux/x.ts", "x.ts.", "x .ts", "").foreach { path =>
        assert(write(root, path -> generated).isLeft, path)
      }
      assertEquals(names(root), Nil)
    }
  }

  test("existing targets without the generated marker are not overwritten; an older banner with the same marker is replaced") {
    withTempDir { root =>
      Files.writeString(root.resolve("legacy.ts"), s"$marker (old tool)\nold\n")
      write(root, "legacy.ts" -> generated).toOption.get
      assertEquals(Files.readString(root.resolve("legacy.ts")), generated)
    }
  }

  test("a generated marker that is empty, blank or only comment symbols is rejected, and hand-written files are not deleted") {
    withTempDir { root =>
      Files.writeString(root.resolve("manual.ts"), "export default manual;\n")
      List("", "   ", "//", " // ", "// generated\nsecond").foreach { blank =>
        val result = OutputWriter.write(List(GeneratedFile(root, "fresh.ts", s"$blank\n")), List(root), blank, root)
        assert(result.left.exists(_.exists(_.message.contains("generatedMarker"))), s"$blank: $result")
      }
      assertEquals(names(root), List("manual.ts"))
    }
  }

  test("file/directory conflicts are rejected before writing, and stale generated files are not deleted") {
    withTempDir { root =>
      Files.writeString(root.resolve("stale.ts"), generated)
      Files.writeString(root.resolve("plain"), "not a directory")
      Files.createDirectories(root.resolve("dir.ts"))
      val cases = List(
        List("a.ts" -> generated, "a.ts/b.ts" -> generated) -> "is also used as a directory",
        List("plain/b.ts" -> generated) -> "is not a directory",
        List("dir.ts" -> generated) -> "existing directory"
      )
      cases.foreach { case (files, message) =>
        val result = write(root, files*)
        assert(result.left.exists(_.exists(_.message.contains(message))), s"$files: $result")
      }
      assertEquals(names(root), List("dir.ts", "plain", "stale.ts"))
    }
  }

  test("after a write failure existing generated files keep their content and marker, and rerunning after the fix converges") {
    withTempDir { root =>
      Files.writeString(root.resolve("stale.ts"), generated)
      val target = root.resolve("target.ts")
      Files.writeString(target, s"$marker\nold\n")
      // Put a non-empty directory at the temporary file name so that the temporary file cannot be created
      val obstacle = Files.createDirectories(root.resolve(".target.ts.hx4s-tmp"))
      Files.writeString(obstacle.resolve("keep"), "x")
      val failed = write(root, "target.ts" -> generated)
      assert(failed.left.exists(_.exists(_.message.startsWith("cannot write file"))), failed)
      assertEquals(Files.readString(target), s"$marker\nold\n")
      assert(Files.exists(root.resolve("stale.ts")), "stale generated files are not deleted until writing is done")
      Files.delete(obstacle.resolve("keep"))
      Files.delete(obstacle)
      assertEquals(write(root, "target.ts" -> generated).map(_.render), Right("wrote 1, unchanged 0, deleted 1"))
      assertEquals(write(root, "target.ts" -> generated).map(_.render), Right("wrote 0, unchanged 1, deleted 0"))
      assertEquals(names(root), List("target.ts"))
    }
  }

  test("temporary files left by a previous failure are not treated as hand-written and are deleted on the next run") {
    withTempDir { root =>
      Files.createDirectories(root.resolve("nested"))
      Files.writeString(root.resolve(".page.ts.hx4s-tmp"), "partial")
      Files.writeString(root.resolve("nested/.old.ts.hx4s-tmp"), "")
      assertEquals(write(root, "page.ts" -> generated).map(_.render), Right("wrote 1, unchanged 0, deleted 0"))
      assertEquals(names(root), List("nested", "page.ts"))
      assertEquals(names(root.resolve("nested")), Nil)
      assertEquals(Files.readString(root.resolve("page.ts")), generated)
    }
  }

  private def symlink(link: Path, target: Path): Unit =
    try { Files.createSymbolicLink(link, target) }
    catch {
      case e @ (_: java.io.IOException | _: UnsupportedOperationException | _: SecurityException) =>
        assume(false, s"symbolic links are not available here: ${e.getMessage}")
    }

  private def junction(link: Path, target: Path): Unit = {
    assume(System.getProperty("os.name").toLowerCase.contains("windows"), "junctions exist only on Windows")
    val process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString, target.toString).redirectErrorStream(true).start()
    process.getInputStream.readAllBytes()
    assertEquals(process.waitFor(), 0, "mklink /J failed")
  }

  /** Prepares root and a directory outside it (holding one file with the generated marker) */
  private def withOutside(test: (Path, Path) => Unit): Unit =
    withTempDir { tmp =>
      val root = Files.createDirectories(tmp.resolve("root"))
      val outside = Files.createDirectories(tmp.resolve("outside"))
      Files.writeString(outside.resolve("target.ts"), generated)
      test(root, outside)
    }

  private def assertRefusesLink(result: CodegenError.Result[WriteSummary]): Unit =
    assert(result.left.exists(_.exists(_.message.contains("symbolic link or junction"))), result)

  test("a symbolic link as a parent directory, output target or output root is rejected without following it") {
    withOutside { (root, outside) =>
      symlink(root.resolve("parent"), outside)
      assertRefusesLink(write(root, "parent/new.ts" -> generated))
      Files.delete(root.resolve("parent"))
      symlink(root.resolve("leaf.ts"), outside.resolve("target.ts"))
      assertRefusesLink(write(root, "leaf.ts" -> s"$marker\nreplaced\n"))
      Files.delete(root.resolve("leaf.ts"))
      symlink(root.resolve("dangling.ts"), outside.resolve("missing.ts"))
      assertRefusesLink(write(root, "dangling.ts" -> generated))
      val linkedRoot = root.getParent.resolve("linked-root")
      symlink(linkedRoot, outside)
      assertRefusesLink(OutputWriter.write(List(GeneratedFile(linkedRoot, "new.ts", generated)), Nil, marker, linkedRoot.getParent))
      assertEquals(names(outside), List("target.ts"))
      assertEquals(Files.readString(outside.resolve("target.ts")), generated)
    }
  }

  test("a symbolic link inside an output directory is rejected without following it when searching for stale files") {
    withOutside { (root, outside) =>
      symlink(root.resolve("linked"), outside)
      assertRefusesLink(write(root, "fresh.ts" -> generated))
      assertEquals(names(outside), List("target.ts"))
      assertEquals(names(root), List("linked"))
    }
  }

  test("Windows junctions are rejected too as parent directories, output roots and when searching for stale files") {
    withOutside { (root, outside) =>
      junction(root.resolve("joined"), outside)
      assertRefusesLink(write(root, "joined/new.ts" -> generated))
      assertRefusesLink(write(root, "fresh.ts" -> generated))
      val joinedRoot = root.getParent.resolve("joined-root")
      junction(joinedRoot, outside)
      assertRefusesLink(OutputWriter.write(List(GeneratedFile(joinedRoot, "new.ts", generated)), List(joinedRoot), marker, joinedRoot.getParent))
      assertEquals(names(outside), List("target.ts"))
      assertEquals(names(root), List("joined"))
    }
  }

  /** Writes with project/app being a link and the output root project/app/routes a regular directory behind it */
  private def writeThroughLinkedAncestor(link: (Path, Path) => Unit): Unit =
    withOutside { (project, outside) =>
      val routes = Files.createDirectories(outside.resolve("routes"))
      Files.writeString(routes.resolve("old.ts"), generated)
      link(project.resolve("app"), outside)
      val root = project.resolve("app").resolve("routes")
      assertRefusesLink(OutputWriter.write(List(GeneratedFile(root, "new.ts", generated)), List(root), marker, project))
      val scalaOutput = project.resolve("app").resolve("generated")
      assertRefusesLink(OutputWriter.write(List(GeneratedFile(scalaOutput, "Api.scala", s"$marker\n")), Nil, marker, project))
      assertEquals(names(outside), List("routes", "target.ts"))
      assertEquals(names(routes), List("old.ts"))
    }

  test("a symbolic link as an ancestor of the output root (below the base directory) is rejected") {
    writeThroughLinkedAncestor(symlink)
  }

  test("a junction as an ancestor of the output root (below the base directory) is rejected") {
    writeThroughLinkedAncestor(junction)
  }

  test("the base directory itself and ancestors outside it are not checked") {
    withOutside { (project, _) =>
      val result = OutputWriter.write(List(GeneratedFile(project.resolve("a/b"), "x.ts", generated)), Nil, marker, project.resolve("a"))
      assertEquals(result.map(_.render), Right("wrote 1, unchanged 0, deleted 0"))
      val elsewhere = project.getParent.resolve("elsewhere")
      assertEquals(OutputWriter.write(List(GeneratedFile(elsewhere, "x.ts", generated)), Nil, marker, project).map(_.render),
        Right("wrote 1, unchanged 0, deleted 0"))
    }
  }
}
