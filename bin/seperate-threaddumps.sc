#!/usr/bin/env amm

import $ivy.`com.typesafe.akka::akka-stream:2.5.3`

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try
import ammonite.ops._

@main
def main(input: Path, logs: Path, threadDumps: Path): Unit = {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val ThreadDumpLine = "^(.+?marathon.+?: Full thread dump.+)$".r
  val MarathonLogLine = "^(.+?marathon.+?: \\[[^\\]]+\\] (INFO|WARN|DEBUG|ERROR).*)".r

  val reader = FileIO.fromPath(input.toNIO)

  val writeLogs = Flow[(Boolean, ByteString)]
    .collect { case (false, line) => line }
    .intersperse(ByteString("\n"))
    .toMat(FileIO.toPath(logs.toNIO))(Keep.right)

  val writeThreadDumps = Flow[(Boolean, ByteString)]
    .collect { case (true, line) => line }
    .intersperse(ByteString("\n"))
    .toMat(FileIO.toPath(threadDumps.toNIO))(Keep.right)

  val (r1, r2) =
    reader
      .concat(Source.single(ByteString("\n")))
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = Int.MaxValue))
      .map(_.utf8String)
      .sliding(2)
      .statefulMapConcat { () =>
        var isThreaddump = false

        { msg =>

          msg match {
            case Seq(_, ThreadDumpLine(_)) =>
              isThreaddump = true
            case Seq(MarathonLogLine(_, _), _) =>
              isThreaddump = false
            case _ =>
              ()
          }

          List((isThreaddump, ByteString(msg.head)))
        }
      }
      .alsoToMat(writeLogs)(Keep.right)
      .toMat(writeThreadDumps)(Keep.both)
      .run

  Await.result(r1, Duration.Inf)
  Await.result(r2, Duration.Inf)
  system.terminate()

}
