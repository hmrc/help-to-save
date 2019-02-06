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
  "uk.gov.hmrc" %% "auth-client" % "2.19.0-play-25",
  "uk.gov.hmrc" %% "play-config" % "7.2.0",
  "uk.gov.hmrc" %% "domain" % "5.3.0",
  "org.typelevel" %% "cats-core" % "1.5.0",
  "com.github.kxbmap" %% "configs" % "0.4.4",
  "uk.gov.hmrc" %% "play-reactivemongo" % "6.2.0",
  "uk.gov.hmrc" %% "crypto" % "5.2.0",
  "uk.gov.hmrc" %% "bootstrap-play-25" % "4.8.0",
  "uk.gov.hmrc" %% "mongo-lock" % "5.1.1"
)

def testDependencies(scope: String = "test,it") = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.4.0-play-25" % scope,
  "org.scalatest" %% "scalatest" % "3.0.5" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "org.scalamock" %% "scalamock" % "4.1.0" % scope,
  "uk.gov.hmrc" %% "stub-data-generator" % "0.5.3" % scope,
  "com.miguno.akka" %% "akka-mock-scheduler" % "0.5.1" % scope,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.13" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "3.1.0"
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

  wartremoverErrors in(Compile, compile) ++= Warts.allBut(excludedWarts: _*)
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
  // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
  // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
  // imcompatible with a lot of WordSpec
  .settings(wartremoverErrors in(Test, compile) --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference))
  .settings(wartremoverExcluded ++=
    routes.in(Compile).value ++
      (baseDirectory.value ** "*.sc").get ++
      Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala") ++
      (baseDirectory.value ** "UCThresholdManager.scala").get ++
      (baseDirectory.value ** "UCThresholdConnectorProxyActor.scala").get ++
      (baseDirectory.value ** "UCThresholdMongoProxy.scala").get ++
      (baseDirectory.value ** "EligibilityStatsActor.scala").get ++
      (baseDirectory.value ** "Lock.scala").get
  )
  .settings(catsSettings)
  .settings(scalacOptions += "-Xcheckinit")
  .settings(
    libraryDependencies ++= appDependencies,
    retrieveManaged := false,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    routesGenerator := StaticRoutesGenerator
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
