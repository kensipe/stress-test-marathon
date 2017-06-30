#!/usr/bin/env amm

/*
 * Given a series of metrics generated on a loop, such as:
 *
 *   while sleep 1; do date; curl marathon.mesos:8080/metrics; done >> metrics.log
 *
 * We parse this crude format and turn it into something we can feed to influxDB
 *
 * TODO - stop being silly; output date on it's own line so we don't need such
 * complex regexes to repair this mistake
 */
import $ivy.`ch.qos.logback:logback-classic:1.2.3`
import $ivy.`com.typesafe.play::play-json:2.6.0`
import $ivy.`com.typesafe.akka::akka-stream:2.5.3`
import $ivy.`com.typesafe.akka::akka-slf4j:2.5.3`

import ammonite.ops._
import com.typesafe.config.ConfigFactory
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
import scala.util.Try

val format = DateTimeFormatter.ofPattern("EEE MMM d kk:mm:ss z yyyy")

object LogbackConfig {
  def start(): Unit = {
    import ch.qos.logback.classic.LoggerContext
    import ch.qos.logback.classic.joran.JoranConfigurator
    import org.slf4j.LoggerFactory
    val context  = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
    val configurator = new JoranConfigurator()

    configurator.setContext(context)
    context.reset()
    configurator.doConfigure(new java.io.ByteArrayInputStream("""
<configuration>
    <appender name="stderr" class="ch.qos.logback.core.ConsoleAppender">
        <target>System.err</target>
        <encoder><pattern>[%date] %-5level %message \(%logger:%thread\)%n</pattern></encoder>
    </appender>
    <root level="INFO"><appender-ref ref="stderr"/></root>
</configuration>
""".getBytes))

  }
}
object Helpers {
  val LineMatcher = "^(.+\\}|)([a-zA-Z]+ [a-zA-Z]+ [0-9]+ [0-9:]+ [A-Z]+ [0-9]+)$".r
  def shorten(name: String): String = {
    val pieces = name.split('.')
    if (pieces.length < 2)
      name
    else
      pieces.splitAt(pieces.length - 2) match {
        case (prior, after) =>
          prior.map(_.head).mkString("") + "." + after.mkString(".")
      }
  }

  def sanitize(name: String): String =
    name.replaceAll("=", "_").replaceAll("@", "_")

  val ColonDelimited = "^([^:]+):(.+)$".r
  val HttpResponse = "^(.+?)(\\dxx)-?(.*)".r
  val Resource = "^(.+\\.(api|v2)\\.[^.]+Resource)\\.([^.]+)$".r

  def categorize(metricName: String): (String, Option[String]) = metricName match {
    case ColonDelimited(name, category) =>
      (name, Some(category))
    case HttpResponse(prefix, code, rest) =>
      (prefix + rest, Some(code))
    case Resource(resource, _, action) =>
      (resource, Some(action))
    case _ =>
      (metricName, None)
  }

  def toInfluxDb(date: ZonedDateTime, data: JsValue): List[String] = {
    val ts = date.toEpochSecond * 1000000000
    val output = List.newBuilder[String]
    for {
      kind <- List("counters", "gauges", "histograms", "meters", "timers")
      payload = (data \ kind).as[JsObject]
      (metricName, v: JsObject) <- payload.fields
    } {
      val (iMetricName, categoryField) = categorize(metricName)
      val name = s"${kind}:${sanitize(shorten(iMetricName))}"
      val tags = v.fields.collect {
        case (name, JsString(value)) => s"${name}=${value}"
      } ++ categoryField.map { c => s"category=${c}" }
      val values = v.fields.collect { case (name, JsNumber(value)) => s"${name}=${value}" }
      if (values.nonEmpty)
        output += (name +: tags).mkString(",") + " " + values.mkString(",") + " " + ts
    }
    output.result
  }

}

class DerpLogger
@main
def main(file: Path): Unit = {
  import Helpers._
  LogbackConfig.start()

  val cfg = ConfigFactory.parseString("""
akka {
  loggers = ["akka.event.slf4j.Slf4jLogger"]
  loglevel = "DEBUG"
  stdout-loglevel = OFF
  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
}
""")

  implicit val as = ActorSystem("main", cfg)
  implicit val mat = ActorMaterializer()

  val done = FileIO.fromPath(file.toNIO)
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = Int.MaxValue))
    .map(_.utf8String)
    .collect { case LineMatcher(data, dateAsString) =>
      val json = if (data == "") JsNull else Json.parse(data)
      (json, ZonedDateTime.parse(dateAsString, format))
    }
    .sliding(2)
    .collect { case Seq((_, date), (json, _)) => toInfluxDb(date, json) }
    .mapConcat(identity)
    .runForeach(println)

  System.err.println(Try(Await.result(done, 1.day)))
  as.terminate()
}
