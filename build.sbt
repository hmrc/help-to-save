import play.core.PlayVersion
import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning
import wartremover.{Wart, Warts}
import wartremover.WartRemover.autoImport.{wartremoverErrors, wartremoverExcluded}

val appName = "help-to-save"

val hmrc = "uk.gov.hmrc"
val playVersion = "play-28"
val mongoVersion = "0.73.0"
val bootstrapBackendVersion = "7.23.0"

val dependencies = Seq(
  ws,
  hmrc                %% s"bootstrap-backend-$playVersion"  % bootstrapBackendVersion,
  hmrc                %% "domain"                           % s"8.3.0-$playVersion",
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"               % mongoVersion,
  "org.typelevel"     %% "cats-core"                        % "2.2.0",
  "com.github.kxbmap" %% "configs"                          % "0.6.1",
  compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.12" cross CrossVersion.full),
  "com.github.ghik" % "silencer-lib" % "1.7.12" % Provided cross CrossVersion.full
)

def testDependencies(scope: String = "test,it") = Seq(
  hmrc                    %% s"bootstrap-test-$playVersion"   % bootstrapBackendVersion                % scope,
  hmrc                    %% "domain"                         % s"8.1.0-$playVersion"                  % scope,
  "uk.gov.hmrc.mongo"     %% "hmrc-mongo-test-play-28"        % mongoVersion                           % scope,
  "org.scalatest"         %% "scalatest"                      % "3.2.9"                                % scope,
  "org.scalatestplus"     %% "scalatestplus-scalacheck"       % "3.1.0.0-RC2"                          % scope,
  "com.typesafe.play"     %% "play-test"                      % PlayVersion.current                    % scope,
  "org.scalamock"         %% "scalamock"                      % "5.2.0"                                % scope,
  "com.typesafe.akka"     %% "akka-testkit"                   % "2.6.21"                               % scope
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
    .setPreference(RewriteArrowSymbols, false)
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

  Seq(Compile / compile / wartremoverErrors ++= Warts.allBut(excludedWarts: _*),
    // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
    // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
    // imcompatible with a lot of WordSpec
    Test / compile / wartremoverErrors --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference),
    Compile / compile / wartremoverExcluded ++=
      (Compile / routes).value ++
        (baseDirectory.value ** "*.sc").get ++
        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala") ++
        (baseDirectory.value ** "UCThresholdManager.scala").get ++
        (baseDirectory.value ** "UCThresholdConnectorProxyActor.scala").get ++
        (baseDirectory.value ** "UCThresholdMongoProxy.scala").get ++
        (baseDirectory.value ** "EligibilityStatsActor.scala").get ++
        (baseDirectory.value ** "Lock.scala").get ++
        (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosave" / "config").get
  )
  Test / compile / wartremoverExcluded ++=
    (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosave" / "config").get
}

lazy val catsSettings = scalacOptions ++= Seq("-deprecation", "-feature")

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(onLoadMessage := "")
  .settings( //fix scaladoc generation in jenkins
    Compile / scalacOptions -= "utf8")
  .settings( //Globally enable support for postfix operators
    scalacOptions += "-language:postfixOps")
  .settings(majorVersion := 2)
  .settings(scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(scalaVersion := "2.13.8")
  .settings(defaultSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 7001)
  .settings(scalariformSettings: _*)
  .settings(wartRemoverSettings)
  .settings(catsSettings)
  .settings(scalacOptions += "-Xcheckinit")
  .settings(
    libraryDependencies ++= (dependencies ++ testDependencies()),
    retrieveManaged := false
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    IntegrationTest / Keys.fork  := false,
    IntegrationTest / unmanagedSourceDirectories := Seq((IntegrationTest / baseDirectory).value / "it"),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    IntegrationTest / parallelExecution  := false)
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
