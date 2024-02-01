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
  .settings(scalafmtOnCompile := true)
  .settings(PlayKeys.playDefaultPort := 7001)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test()
  )
  // Disable default sbt Test options (might change with new versions of bootstrap)
  .settings(Test / testOptions -= Tests
    .Argument("-o", "-u", "target/test-reports", "-h", "target/test-reports/html-report"))
  // Suppress successful events in Scalatest in standard output (-o)
  // Options described here: https://www.scalatest.org/user_guide/using_scalatest_with_sbt
  .settings(
    Test / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-oNCHPQR",
      "-u",
      "target/test-reports",
      "-h",
      "target/test-reports/html-report"))
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings) *)
  .settings(
    IntegrationTest / Keys.fork := false,
    IntegrationTest / unmanagedSourceDirectories := (IntegrationTest / baseDirectory)(base => Seq(base / "it")).value,
    addTestReportOption(IntegrationTest, "int-test-reports"),
    IntegrationTest / parallelExecution := false,
    // Disable default sbt Test options (might change with new versions of bootstrap)
    IntegrationTest / testOptions -= Tests
      .Argument("-o", "-u", "target/int-test-reports", "-h", "target/int-test-reports/html-report"),
    IntegrationTest / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-oNCHPQR",
      "-u",
      "target/int-test-reports",
      "-h",
      "target/int-test-reports/html-report")
  )
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
