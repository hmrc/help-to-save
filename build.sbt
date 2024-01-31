import sbt.*
import sbt.Keys.*
import uk.gov.hmrc.DefaultBuildSettings.addTestReportOption
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val appName = "help-to-save"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(onLoadMessage := "")
  .settings(majorVersion := 2)
  .settings(CodeCoverageSettings.settings *)
  .settings(scalaVersion := "2.13.8")
  .settings(PlayKeys.playDefaultPort := 7001)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test()
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings) *)
  .settings(
    IntegrationTest / Keys.fork  := false,
    IntegrationTest / unmanagedSourceDirectories := Seq((IntegrationTest / baseDirectory).value / "it"),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    IntegrationTest / parallelExecution  := false)
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
