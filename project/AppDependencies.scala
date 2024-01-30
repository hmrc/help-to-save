import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {
  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-28"
  val mongoVersion = "0.73.0"
  val bootstrapBackendVersion = "7.23.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    hmrc %% s"bootstrap-backend-$playVersion" % bootstrapBackendVersion,
    hmrc %% "domain" % s"8.3.0-$playVersion",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28" % mongoVersion,
    "org.typelevel" %% "cats-core" % "2.2.0",
    "com.github.kxbmap" %% "configs" % "0.6.1",
  )

  def test(scope: String = "test,it"): Seq[ModuleID] = Seq(
    hmrc %% s"bootstrap-test-$playVersion" % bootstrapBackendVersion % scope,
    hmrc %% "domain" % s"8.1.0-$playVersion" % scope,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % mongoVersion % scope,
    "org.scalatest" %% "scalatest" % "3.2.9" % scope,
    "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalamock" %% "scalamock" % "5.2.0" % scope,
    "com.typesafe.akka" %% "akka-testkit" % "2.6.21" % scope
  )
}
