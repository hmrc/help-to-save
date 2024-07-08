import sbt.*
import uk.gov.hmrc.DefaultBuildSettings.addTestReportOption
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin

val appName = "help-to-save"

lazy val ItTest = config("it") extend Test

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(onLoadMessage := "")
  .settings(majorVersion := 2)
  .settings(CodeCoverageSettings.settings :_*)
  .settings(scalaVersion := "2.13.13")
  .settings(PlayKeys.playDefaultPort := 7001)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test())
  // Disable default sbt Test options (might change with new versions of bootstrap)
  .settings(
    Test / testOptions -= Tests.Argument(
      "-o",
      "-u",
      "target/test-reports",
      "-h",
      "target/test-reports/html-report"
    )
  )
  // Suppress successful events in Scalatest in standard output (-o)
  // Options described here: https://www.scalatest.org/user_guide/using_scalatest_with_sbt
  .settings(
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest,
                                         "-oNCHPQR",
                                         "-u",
                                         "target/test-reports",
                                         "-h",
                                         "target/test-reports/html-report"))
  .configs(ItTest)
  .settings(inConfig(ItTest)(Defaults.testSettings): _*)
  .settings(
    ItTest / Keys.fork := false,
    ItTest / unmanagedSourceDirectories := (ItTest / baseDirectory)(base => Seq(base / "it")).value,
    addTestReportOption(ItTest, "int-test-reports"),
    ItTest / parallelExecution := false,
    // Disable default sbt Test options (might change with new versions of bootstrap)
    ItTest / testOptions -= Tests.Argument(
      "-o",
      "-u",
      "target/int-test-reports",
      "-h",
      "target/int-test-reports/html-report"
    ),
    ItTest / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-oNCHPQR",
      "-u",
      "target/int-test-reports",
      "-h",
      "target/int-test-reports/html-report")
  )
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
