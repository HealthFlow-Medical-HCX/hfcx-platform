package org.healthflow.dp.core.exception

case class PipelineException(code: String, message: String, trace: String) extends Exception(message)
