import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    ".*Reverse.*",
    "uk.gov.hmrc.BuildInfo",
    ".*routes.*",
    ".*javascript.*",
    "testOnlyDoNotUseInAppConf.*",
    "app.*",
    "prod.*",
    "com.*",
    ".*config.*",
    "uk.gov.hmrc.helptosave.audit.*",
    "uk.gov.hmrc.helptosave.metrics.*",
    "uk.gov.hmrc.helptosave.models.*",
    ".*EligibilityStatsProviderImpl",
    ".*HttpClient",
    ".*JsErrorOps"
  )

  val settings: Seq[Setting[?]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 85,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
