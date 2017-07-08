#!/usr/bin/env amm

import $ivy.`com.typesafe.play::play-json:2.6.0`
import $ivy.`com.typesafe.akka::akka-stream:2.5.3`
import $ivy.`com.typesafe.akka::akka-http:10.0.9`
import $ivy.`com.typesafe.akka::akka-slf4j:2.5.3`
import $ivy.`ch.qos.logback:logback-classic:1.2.3`

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import ammonite.ops._
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import play.api.libs.json._
import scala.annotation.tailrec
import scala.collection.generic.Growable
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

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

object Marshallers {
  import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }
  import akka.http.scaladsl.model.MediaTypes.`application/json`
  import akka.http.scaladsl.marshalling.{ Marshaller, ToEntityMarshaller }

  val jsonStringUnmarshaller =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(`application/json`)
      .mapWithCharset {
        case (ByteString.empty, _) => throw Unmarshaller.NoContentException
        case (data, charset) => data.decodeString(charset.nioCharset.name)
      }

  val jsonStringMarshaller =
    Marshaller.stringMarshaller(`application/json`)

  /**
    * HTTP entity => `A`
    *
    * @param reads reader for `A`
    * @tparam A type to decode
    * @return unmarshaller for `A`
    */
  implicit def playJsonUnmarshaller[A](
    implicit
      reads: Reads[A]
  ): FromEntityUnmarshaller[A] = {
    def read(json: JsValue) =
      reads
        .reads(json)
        .recoverTotal(
          error =>
          throw new IllegalArgumentException(JsError.toJson(error).toString)
        )
    jsonStringUnmarshaller.map(data => read(Json.parse(data)))
  }

  /**
    * `A` => HTTP entity
    *
    * @param writes writer for `A`
    * @param printer pretty printer function
    * @tparam A type to encode
    * @return marshaller for any `A` value
    */
  implicit def playJsonMarshaller[A](
    implicit
      writes: Writes[A],
    printer: JsValue => String = Json.prettyPrint
  ): ToEntityMarshaller[A] =
    jsonStringMarshaller.compose(printer).compose(writes.writes)
}

@main def main(index: String, queryFile: Path, host: String = "localhost:9200"): Unit = {
  import Marshallers._
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

  def runQuery(query: Either[String, JsValue]): Future[(String, List[JsObject])] = {
    Source.fromFuture {
      val (uri, reqQuery) = query match {
        case Left(scrollId) =>
          (s"http://${host}/_search/scroll", Json.obj("scroll" -> "1m", "scroll_id" -> scrollId))
        case Right(js) =>
          (s"http://${host}/${index}/_search?scroll=1m", js)
      }
      Marshal(reqQuery)
        .to[MessageEntity]
        .flatMap { ent =>
          Http().singleRequest(
            HttpRequest(
              method = HttpMethods.POST,
              uri = uri,
              entity = ent))
        }
    }
      .mapAsync(1) { response => Unmarshal(response).to[JsValue] }
      .map { jsResponse =>
        val scrollId = (jsResponse \ "_scroll_id").as[String]
        val hits = (jsResponse \ "hits" \ "hits").as[List[JsObject]]
        (scrollId, hits)
      }
      .runWith(Sink.head)
  }

  @tailrec def iter(query: Either[String, JsValue]): Unit = {
    val (scrollId, hits) = Await.result(runQuery(query), 1.day)
    if (hits.nonEmpty) {
      hits.foreach { hit => println(Json.stringify(hit)) }
      iter(Left(scrollId))
    }
  }

  val r = Try(iter(Right(Json.parse(read(queryFile)))))
  System.err.println(s"Done! result was: ${r}")
  as.terminate()
}
