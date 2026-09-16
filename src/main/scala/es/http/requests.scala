package es.http

import org.apache.pekko.actor.typed.ActorRef

import es.actors.PersistentBankAccount.Command
import es.actors.PersistentBankAccount.Command.*
import es.actors.PersistentBankAccount.Response
import es.http.Validation.ValidationResult
import es.http.Validation.Validator
import es.http.Validation.validateMinimumAbs
import es.http.Validation.validateMinimun
import es.http.Validation.validateRequired

import cats.implicits.*

object requests:

  case class BankAccountCreationRequest(user: String, currency: String, balance: Double)
  object BankAccountCreationRequest:
    extension (request: BankAccountCreationRequest)
      def toCommand(replyTo: ActorRef[Response]): Command =
        CreateBankAccount(request.user, request.currency, request.balance, replyTo)

    given Validator[BankAccountCreationRequest] with
      def validate(request: BankAccountCreationRequest): ValidationResult[BankAccountCreationRequest] =
        val userValidation     = validateRequired(request.user, "user")
        val currencyValidation = validateRequired(request.currency, "currency")
        val balanceValidation =
          validateMinimun(request.balance, 0, "balance").combine(validateMinimumAbs(request.balance, 0.01, "balance"))

        (userValidation, currencyValidation, balanceValidation).mapN(BankAccountCreationRequest.apply)

  end BankAccountCreationRequest

  case class BankAccountUpdateRequest(currency: String, amount: Double)
  object BankAccountUpdateRequest:
    extension (request: BankAccountUpdateRequest)
      def toCommand(id: String, replyTo: ActorRef[Response]): Command =
        UpdateBalance(id, request.currency, request.amount, replyTo)

    given Validator[BankAccountUpdateRequest] with
      def validate(request: BankAccountUpdateRequest): ValidationResult[BankAccountUpdateRequest] =
        val currencyValidation = validateRequired(request.currency, "currency")
        val amountValidation   = validateMinimumAbs(request.amount, 0.01, "amount")

        (currencyValidation, amountValidation).mapN(BankAccountUpdateRequest.apply)

  end BankAccountUpdateRequest

end requests
