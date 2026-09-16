package es.http

object errors:
  case class FailureResponse(reason: String)
