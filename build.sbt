import play.core.PlayVersion
import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning
import wartremover.{Wart, Warts, wartremoverErrors, wartremoverExcluded}

val appName = "help-to-save"

lazy val appDependencies: Seq[ModuleID] = dependencies ++ testDependencies()
lazy val playSettings: Seq[Setting[_]] = Seq.empty
lazy val plugins: Seq[Plugins] = Seq.empty

val dependencies = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.1.0",
  "uk.gov.hmrc" %% "auth-client" % "2.31.0-play-26",
  "uk.gov.hmrc" %% "play-config" % "7.5.0",
  "uk.gov.hmrc" %% "domain" % "5.6.0-play-26",
  "org.typelevel" %% "cats-core" % "2.0.0",
  "com.github.kxbmap" %% "configs" % "0.4.4",
  "uk.gov.hmrc" %% "simple-reactivemongo" % "7.20.0-play-26",
  "uk.gov.hmrc" %% "crypto" % "5.4.0",
  "uk.gov.hmrc" %% "mongo-lock" % "6.15.0-play-26"
)

def testDependencies(scope: String = "test,it") = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % scope,
  "org.scalatest" %% "scalatest" % "3.0.8" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "org.scalamock" %% "scalamock" % "4.4.0" % scope,
  "uk.gov.hmrc" %% "stub-data-generator" % "0.5.3" % scope,
  "com.miguno.akka" %% "akka-mock-scheduler" % "0.5.1" % scope,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.13" % scope, // upgrading to 2.5.26 causes errors
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.15.0-play-26" % scope
)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*config.*;.*(AuthService|BuildInfo|Routes|JsErrorOps|EligibilityStatsProviderImpl|HttpClient).*",
    ScoverageKeys.coverageMinimum := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val scalariformSettings = {
  import com.typesafe.sbt.SbtScalariform.ScalariformKeys
  import scalariform.formatter.preferences._
  // description of options found here -> https://github.com/scala-ide/scalariform
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignArguments, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(CompactControlReadability, false)
    .setPreference(CompactStringConcatenation, false)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DoubleIndentMethodDeclaration, true)
    .setPreference(FirstArgumentOnNewline, Preserve)
    .setPreference(FirstParameterOnNewline, Preserve)
    .setPreference(FormatXml, true)
    .setPreference(IndentLocalDefs, true)
    .setPreference(IndentPackageBlocks, true)
    .setPreference(IndentSpaces, 2)
    .setPreference(IndentWithTabs, false)
    .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
    .setPreference(NewlineAtEndOfFile, true)
    .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(SpaceBeforeColon, false)
    .setPreference(SpaceBeforeContextColon, false)
    .setPreference(SpaceInsideBrackets, false)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(SpacesAroundMultiImports, false)
    .setPreference(SpacesWithinPatternBinders, true)
}

lazy val wartRemoverSettings = {
  // list of warts here: http://www.wartremover.org/doc/warts.html
  val excludedWarts = Seq(
    Wart.DefaultArguments,
    Wart.FinalCaseClass,
    Wart.FinalVal,
    Wart.ImplicitConversion,
    Wart.ImplicitParameter,
    Wart.LeakingSealed,
    Wart.Nothing,
    Wart.Overloading,
    Wart.ToString,
    Wart.Var)

  Seq(wartremoverErrors in(Compile, compile) ++= Warts.allBut(excludedWarts: _*),
    // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
    // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
    // imcompatible with a lot of WordSpec
    wartremoverErrors in(Test, compile) --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference),
    wartremoverExcluded in(Compile, compile) ++=
      routes.in(Compile).value ++
        (baseDirectory.value ** "*.sc").get ++
        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala") ++
        (baseDirectory.value ** "UCThresholdManager.scala").get ++
        (baseDirectory.value ** "UCThresholdConnectorProxyActor.scala").get ++
        (baseDirectory.value ** "UCThresholdMongoProxy.scala").get ++
        (baseDirectory.value ** "EligibilityStatsActor.scala").get ++
        (baseDirectory.value ** "Lock.scala").get ++
        (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosave" / "config").get
  )
  wartremoverExcluded in(Test, compile) ++=
    (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosave" / "config").get
}

lazy val catsSettings = scalacOptions += "-Ypartial-unification"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
  .settings(addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17"))
  .settings(majorVersion := 2)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(scalaVersion := "2.11.12")
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 7001)
  .settings(scalariformSettings: _*)
  .settings(wartRemoverSettings)
  .settings(catsSettings)
  .settings(scalacOptions += "-Xcheckinit")
  .settings(
    libraryDependencies ++= appDependencies,
    retrieveManaged := false,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := Seq((baseDirectory in IntegrationTest).value / "it"),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    //testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false)
  .settings(resolvers ++= Seq(
    Resolver.bintrayRepo("hmrc", "releases"),
    Resolver.jcenterRepo,
    Resolver.bintrayRepo("hmrclt", "maven")
  ))
