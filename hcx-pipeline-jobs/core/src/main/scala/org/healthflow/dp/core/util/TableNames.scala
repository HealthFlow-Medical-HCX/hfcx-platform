package org.healthflow.dp.core.util

/**
 * Allow-list for SQL table names used by the pipeline-jobs.
 *
 * <p>JDBC PreparedStatement cannot parameterize identifiers (table or column
 * names), so any code path that string-concatenates a table name into a SQL
 * statement must first run that name through [[TableNames.validate]] to ensure
 * the value originated from configuration we control rather than from
 * external input.
 *
 * <p>Mirrors the Java helper at
 * `org.healthflow.common.utils.TableNames` in hcx-common. The two are kept
 * in sync manually because hcx-pipeline-jobs is not part of the root pom and
 * does not depend on hcx-common.
 */
object TableNames {

  private val Allowed: Set[String] = Set(
    "payload",
    "payload_audit",
    "search",
    "subscription",
    "notification",
    "composite_search",
    "onboarding",
    "onboarding_otp"
  )

  @throws(classOf[IllegalArgumentException])
  def validate(name: String): String = {
    if (name == null || !Allowed.contains(name)) {
      throw new IllegalArgumentException("Table name not allow-listed: " + name)
    }
    name
  }
}
