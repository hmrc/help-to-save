import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {
  val hmrc                    = "uk.gov.hmrc"
  val playVersion             = "play-30"
  val mongoVersion            = "2.6.0"
  val bootstrapBackendVersion = "9.11.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    hmrc            %% s"bootstrap-backend-$playVersion" % bootstrapBackendVersion,
    hmrc            %% s"domain-$playVersion"            % "10.0.0",
    s"$hmrc.mongo"  %% s"hmrc-mongo-$playVersion"        % mongoVersion,
    "org.typelevel" %% "cats-core"                       % "2.10.0"
  )

  def test(scope: String = "test, it"): Seq[ModuleID] = Seq(
    hmrc                    %% s"bootstrap-test-$playVersion"  % bootstrapBackendVersion % scope,
    s"$hmrc.mongo"          %% s"hmrc-mongo-test-$playVersion" % mongoVersion            % scope,
    "org.scalatestplus"     %% "scalacheck-1-17"               % "3.2.18.0"              % scope,
    "org.apache.pekko"      %% "pekko-testkit"                 % "1.0.3"                 % scope,
    "org.scalatestplus"     %% "mockito-4-11"                  % "3.2.17.0"              % scope,
    "com.github.tomakehurst" % "wiremock"                      % "3.0.0-beta-7"          % scope
  )
}
