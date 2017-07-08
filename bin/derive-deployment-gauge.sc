#!/usr/bin/env

import $ivy.`com.typesafe.akka::akka-stream:2.5.3`

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString

import ammonite.ops._

import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

@main def main(file: Path): Unit = {
  val DeploymentStartedLog = "^.+: \\[(^[\\]]+)\\] INFO *Received new deployment plan ([0-9a-z-]+).+".r
  // Jun 29 21:21:37 ip-10-0-7-50.us-west-2.compute.internal marathon.sh[25135]: [2017-06-29 21:21:37,076] INFO  Received new deployment plan 469659cd-8839-4b4c-9a55-9469d2cf9e82, no conflicts detected (mesosphere.marathon.upgrade.DeploymentManager:marathon-akka.actor.default-dispatcher-8)
  val DeploymentFinishedLog = ".+: \\[(^[\\]]+)\\] INFO ^.+?Deployment ([0-9a-z-]+):.+finished.+?".r
  // Deployment eee605da-c246-435a-8d6b-58996fd55982:2017-06-29T22:40:36.570Z of / finished (mesosphere.marathon.MarathonSchedulerActor:marathon-akka.actor.default-dispatcher-955)

  sealed trait Event
  case class DeployStarted(planId: String) extends Event
  case class DeployCompleted(planId: String) extends Event

  val format = DateTimeFormatter.ofPattern("EEE MMM d kk:mm:ss z yyyy")
  def parseDate(s: String): ZonedDateTime =
    DeployStarted(LocalDateTime.parse(s, format).atZone(UTC), planId)

  FileIO.fromPath(file.toNIO).
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = Int.MaxValue))
    .collect {
      case DeploymentStartedLog(dateTime, planId) =>
        DeployStarted(parseDate(dateTime), planId)
      case DeploymentFinishedLog(dateTime, planId) =>
        DeployCompleted(parseDate(dateTime), planId)
    }
}
