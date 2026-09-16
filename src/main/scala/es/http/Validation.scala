package es.http

import cats.data.ValidatedNel
import cats.implicits.*

object Validation:

  // field must be present in JSON
  trait Required[A] extends (A => Boolean)

  // minimum value for numerical fields
  trait Minimum[A]    extends ((A, Double) => Boolean)
  trait MinimumAbs[A] extends ((A, Double) => Boolean)

  // TC instances
  given requiredString: Required[String]     = _.nonEmpty
  given minimumInt: Minimum[Int]             = _ >= _
  given minimunDouble: Minimum[Double]       = _ >= _
  given minimumIntAbs: MinimumAbs[Int]       = Math.abs(_) >= _
  given minimunDoubleAbs: MinimumAbs[Double] = Math.abs(_) >= _

  // usage
  def required[A](value: A)(using req: Required[A]): Boolean                        = req(value)
  def minimum[A](value: A, threshold: Double)(using min: Minimum[A]): Boolean       = min(value, threshold)
  def minimumAbs[A](value: A, threshold: Double)(using min: MinimumAbs[A]): Boolean = min(value, threshold)

  // Validated
  // Validated
  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  trait ValidationFailure:
    def errorMessage: String

  case class EmptyField(fieldName: String) extends ValidationFailure:
    override def errorMessage: String = s"${fieldName} is empty"

  case class NegativeValue(fieldName: String) extends ValidationFailure:
    override def errorMessage: String = s"${fieldName} cannot be negative"

  case class BelowMinimumValue(fieldName: String, min: Double) extends ValidationFailure:
    override def errorMessage: String = s"${fieldName} is below minimum value $min"

  def validateMinimun[A: Minimum](value: A, threshold: Double, fieldName: String): ValidationResult[A] =
    if minimum(value, threshold) then value.validNel
    else if threshold == 0.0 then NegativeValue(fieldName).invalidNel
    else BelowMinimumValue(fieldName, threshold).invalidNel

  def validateMinimumAbs[A: MinimumAbs](value: A, threshold: Double, fieldName: String): ValidationResult[A] =
    if minimumAbs(value, threshold) then value.validNel
    else BelowMinimumValue(fieldName, threshold).invalidNel

  def validateRequired[A: Required](value: A, fieldName: String): ValidationResult[A] =
    if required(value) then value.validNel
    else EmptyField(fieldName).invalidNel

  // general type class for requests
  trait Validator[A]:
    def validate(value: A): ValidationResult[A]

  def validateEntity[A](value: A)(using validator: Validator[A]): ValidationResult[A] =
    validator.validate(value)

end Validation
