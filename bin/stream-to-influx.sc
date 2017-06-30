#!/usr/bin/env amm

import $ivy.`com.typesafe.play::play-json:2.6.0`
import $ivy.`com.typesafe.akka::akka-stream:2.5.3`
import $ivy.`com.typesafe.akka::akka-http:10.0.9`

import ammonite.ops._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.annotation.tailrec
import ammonite.ops._
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import scala.collection.generic.Growable
import play.api.libs.json._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import scala.util.Try

object Helpers {
}

@main
def main(file: Path, database: String): Unit = {
  import Helpers._
  implicit val as = ActorSystem()
  implicit val mat = ActorMaterializer()

  val done = FileIO.fromPath(file.toNIO)
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = Int.MaxValue))
    .map(_.utf8String)
    .grouped(10000)
    .map(_.mkString("\n"))
    .mapAsync(1) { lines =>
      println(s"Posting chunk of size ${lines.length}")
      val payload = ByteString.fromString(lines)

      Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:8086/write?db=${database}",
          entity = payload))
    }
    .mapAsync(1) { response =>
      if (response.status.isSuccess)
        response.entity.dataBytes.runWith(Sink.ignore)
      else
        response.entity.dataBytes.runWith(Sink.reduce[ByteString](_ ++ _)).map { bs =>
          System.err.println(s"Error! ${response.status}")
          System.err.println(bs.utf8String)
          throw new RuntimeException("we failed")
        }
    }.
    runWith(Sink.ignore)
    

  println(Try(Await.result(done, 1.day)))
  as.terminate()
}
