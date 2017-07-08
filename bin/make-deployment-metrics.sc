#!/usr/bin/env amm

import $ivy.`com.typesafe.play::play-json:2.6.0`
import ammonite.ops._
import play.api.libs.json._
import java.time.ZonedDateTime

object Helpers {
  case class Entry(
    planId: String,
    removed: Option[ZonedDateTime],
    complete: Option[ZonedDateTime],
    computed: Option[ZonedDateTime],
    `new`: Option[ZonedDateTime],
    store: Option[ZonedDateTime],
    fail: Option[ZonedDateTime],
    step: Option[ZonedDateTime])
  implicit val readZonedDateTime =
    implicitly[Reads[String]].map { s => ZonedDateTime.parse(s) }
  implicit val format = Json.format[Entry]

  implicit val ZonedDateTimeOrdering: Ordering[ZonedDateTime] = new Ordering[ZonedDateTime] {
    override def compare(x: ZonedDateTime, y: ZonedDateTime): Int = {
      if (x.isEqual(y))
        0
      else if (x.isAfter(y))
        1
      else
        -1
    }
  }

}

@main def main(inputfile: Path): Unit ={
  import Helpers._

  val data = Json.parse(read(inputfile)).as[List[JsValue]].filterNot { l =>
    (l \ "planId").get == JsNull
  }.map(_.as[Entry])

  val fullyKnownDeploys = data.filter { e =>
    e.`new`.nonEmpty
  }

  val completeEvents = fullyKnownDeploys.flatMap { e =>
    e.fail.orElse(e.complete).map { v =>
      ('complete, v, e.planId)
    }
  }
  val newEvents = fullyKnownDeploys.flatMap { e =>
    e.`new`.map { v =>
      ('new, v, e.planId)
    }
  }
  val allEvents = (completeEvents ++ newEvents).sortBy(_._2)

  val runningtotals = allEvents.scanLeft((allEvents.head._2, 0)) {
    case ((_, activeDeployments), ('new, ts, _)) =>
      (ts, activeDeployments + 1)
    case ((_, activeDeployments), ('complete, ts, _)) =>
      (ts, activeDeployments - 1)
  }
  runningtotals.foreach { case (ts, value) =>
    println(s"deployments value=${value} ${ts.toEpochSecond * 1000000000}")
  }
}
