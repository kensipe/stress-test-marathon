#!/usr/bin/env amm

// extracts crude metrics from the scale scripts. Expects a date, followed by
// app id, followed by the unix "time" command output

import ammonite.ops._
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime

@main
def main(file: Path): Unit  = {
  val data = read(file).split("\n")
  val format = DateTimeFormatter.ofPattern("EEE MMM d kk:mm:ss z yyyy")

  val RealTime = "^real\\t0m([0-9.]+)s".r
  val DateLike = "^([A-Z][a-z]+ [A-Z][a-z]+ [0-9].+)".r
  val App = "^app ([0-9]+)".r

  var currentApp: Option[Int] = None
  var currentDate: Option[ZonedDateTime] = None
  var currentRealTime: Option[Double] = None

  case class Row(appId: Int, date: Option[ZonedDateTime], realTime: Double)
  val output = List.newBuilder[Row]

  data.foreach {
    case RealTime(ts) =>
      currentRealTime = Some(ts.toDouble)
    case App(appId) =>
      if (currentApp.nonEmpty) {
        output += Row(currentApp.get, currentDate, currentRealTime.getOrElse(0))
        currentApp = None
        currentDate = None
        currentRealTime = None
      }
      currentApp = Some(appId.toInt)
    case DateLike(date) =>
      currentDate = Some(ZonedDateTime.parse(date.replaceAll(" +", " "), format))
    case _ =>
      ()
  }

  val results = output.result

  println(s"idx\tapp-id\tdate\ttime")
  results.zipWithIndex.foreach { case (r,i) =>
    println(s"""${i}\t${r.appId}\t${r.date.fold("")(_.toEpochSecond.toString)}\t${r.realTime}""")
  }

}
