import play.core.PlayVersion
import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning
import wartremover.{Wart, Warts}
import wartremover.WartRemover.autoImport.{wartremoverErrors, wartremoverExcluded}

val appName = "help-to-save"

lazy val appDependencies: Seq[ModuleID] = dependencies ++ testDependencies()
lazy val playSettings: Seq[Setting[_]] = Seq.empty
lazy val plugins: Seq[Plugins] = Seq.empty

val hmrc = "uk.gov.hmrc"
val playVersion = "play-28"
val mongoVersion = "0.68.0"

val dependencies = Seq(
  ws,
  hmrc                %% s"bootstrap-backend-$playVersion"  % "5.12.0",
  hmrc                %% "domain"                           % s"6.2.0-$playVersion",
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"               % mongoVersion,
  hmrc                %% "crypto"                           % "6.0.0",
  "org.typelevel"     %% "cats-core"                        % "2.2.0",
  "com.github.kxbmap" %% "configs"                          % "0.6.1",
  compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.5" cross CrossVersion.full),
  "com.github.ghik" % "silencer-lib" % "1.7.5" % Provided cross CrossVersion.full
)

def testDependencies(scope: String = "test,it") = Seq(
  hmrc                    %% s"bootstrap-test-$playVersion"   % "5.12.0"                % scope,
  hmrc                    %% "service-integration-test"       % s"1.1.0-$playVersion"   % scope,
  hmrc                    %% "domain"                         % s"6.2.0-$playVersion"   % scope,
  hmrc                    %% "stub-data-generator"            % "0.5.3"                 % scope,
  "uk.gov.hmrc.mongo"     %% "hmrc-mongo-test-play-28"        % mongoVersion            % scope,
  "org.scalatest"         %% "scalatest"                      % "3.2.9"                 % scope,
  "org.scalatestplus"     %% "scalatestplus-scalacheck"       % "3.1.0.0-RC2"           % scope,
  "com.vladsch.flexmark"  %  "flexmark-all"                   % "0.35.10"               % scope,
  "com.typesafe.play"     %% "play-test"                      % PlayVersion.current     % scope,
  "org.scalamock"         %% "scalamock-scalatest-support"    % "3.6.0"                 % scope,
  "com.miguno.akka"       %% "akka-mock-scheduler"            % "0.5.1"                 % scope,
  "com.typesafe.akka"     %% "akka-testkit"                   % "2.6.14"                % scope
)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    ScoverageKeys.coverageExcludedPackages :=
      """<empty>;.*\.config\..*;
        |.*\.(BuildInfo|EligibilityStatsProviderImpl|HttpClient.*|JsErrorOps|Reverse.*|Routes.*)"""
        .stripMargin,
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
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

lazy val catsSettings = scalacOptions ++= Seq("-Ypartial-unification","-deprecation", "-feature")

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
  .settings(addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17"))
  .settings(majorVersion := 2)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(scalaVersion := "2.12.13")
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 7001)
  .settings(scalariformSettings: _*)
  .settings(wartRemoverSettings)
  .settings(catsSettings)
  .settings(scalacOptions += "-Xcheckinit")
  .settings(
    libraryDependencies ++= appDependencies,
    retrieveManaged := false
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    IntegrationTest / Keys.fork  := false,
    IntegrationTest / unmanagedSourceDirectories := Seq((IntegrationTest / baseDirectory).value / "it"),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    IntegrationTest / parallelExecution  := false)
  .settings(scalacOptions += "-P:silencer:pathFilters=routes")
