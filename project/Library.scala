import sbt._

object Library {
  val stm = "org.scala-stm" %% "scala-stm" % "0.8"
  val immutables = "org.immutables" % "value" % "2.5.6"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.8.0"
  val java8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"

  val scalatest = "org.scalatest" %% "scalatest" % "3.0.5"
  val scalamock = "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0"

  val sourcecode = "com.lihaoyi" %% "sourcecode" % "0.1.3"
}
