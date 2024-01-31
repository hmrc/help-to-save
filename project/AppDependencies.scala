import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {
  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-28"
  val mongoVersion = "0.73.0"
  val bootstrapBackendVersion = "7.23.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    hmrc                %% s"bootstrap-backend-$playVersion" % bootstrapBackendVersion,
    hmrc                %% "domain"                          % s"8.3.0-$playVersion",
    s"$hmrc.mongo"      %% s"hmrc-mongo-$playVersion"        % mongoVersion,
    "org.typelevel"     %% "cats-core"                       % "2.2.0",
    "com.github.kxbmap" %% "configs"                         % "0.6.1",
  )

  def test(scope: String = "test, it"): Seq[ModuleID] = Seq(
    hmrc                %% s"bootstrap-test-$playVersion"  % bootstrapBackendVersion % scope,
    s"$hmrc.mongo"      %% s"hmrc-mongo-test-$playVersion" % mongoVersion            % scope,
    "org.scalatestplus" %% "scalatestplus-scalacheck"      % "3.1.0.0-RC2"           % scope,
    "org.scalamock"     %% "scalamock"                     % "5.2.0"                 % scope,
    "com.typesafe.akka" %% "akka-testkit"                  % "2.6.21"                % scope
  )
}
