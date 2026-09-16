package es.http

import scala.concurrent.Future
import scala.concurrent.duration.*

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.Location
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.Timeout

import io.circe.generic.auto.*

import es.actors.PersistentBankAccount
import es.actors.PersistentBankAccount.Command
import es.actors.PersistentBankAccount.Command.*
import es.actors.PersistentBankAccount.Response
import es.actors.PersistentBankAccount.Response.BankAccountBalanceUpdatedResponse
import es.actors.PersistentBankAccount.Response.BankAccountCreatedResponse
import es.actors.PersistentBankAccount.Response.GetBankAccountResponse
import es.http.Validation.*
import es.http.errors.FailureResponse
import es.http.requests.*

import cats.data.Validated.*
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*

class BankRoutes(bank: ActorRef[Command])(using system: ActorSystem[?]):
  given Timeout = Timeout(5.seconds)

  def createBankAccount(request: BankAccountCreationRequest): Future[Response] =
    bank.ask(replyTo => request.toCommand(replyTo))

  def getBankAccount(id: String): Future[Response] =
    bank.ask(replyTo => GetBankAccount(id, replyTo))

  def updateBankAccount(id: String, request: BankAccountUpdateRequest): Future[Response] =
    bank.ask(replyTo => request.toCommand(id, replyTo))

  def validateRequest[R: Validator](request: R)(routeIdValid: Route): Route =
    validateEntity(request) match
      case Valid(_) => routeIdValid
      case Invalid(failures) =>
        complete(StatusCodes.BadRequest, FailureResponse(failures.toList.map(_.errorMessage).mkString(", ")))

  val routes =
    pathPrefix("bank") {
      pathEndOrSingleSlash {
        // format: off
        /*
          POST /bank/
              payload: bank account creation request
              response:
                  201 Created
                  Location /bank/uuid
         */
        // format: on
        post {
          entity(as[BankAccountCreationRequest]) { request =>
            validateRequest(request) {
              // format: off
              /*
                convert the request into a Command
                send the commando to the bank
                expect a reply
                send back an http response
               */
              // format: on
              onSuccess(createBankAccount(request)) { case BankAccountCreatedResponse(id) =>
                respondWithHeader(Location(s"/bank/$id")) {
                  complete(StatusCodes.Created)
                }
              }
            }
          }
        }
      } ~
        // format: off
        /*
            GET /bank/uuid
                response:
                    200 Ok
                    JSON with bank details
         */
        // format: on
        path(Segment) { id =>
          get {
            // send command to the bank
            // expect a reply
            // send back an http response
            onSuccess(getBankAccount(id)) {
              case GetBankAccountResponse(Some(bankAccount)) =>
                complete(bankAccount) // 200 OK
              case GetBankAccountResponse(None) =>
                complete(StatusCodes.NotFound, FailureResponse(s"Bank account $id not found"))
            }
          } ~
            // format: off
            /*
              PUT /bank/uuid
                  payload: (currency, ammount) as Json
                  response:
                      200 OK
                      payload: bank account as JSON
                      404 Not Found
                      400 Bad Request
             */
             // format: on
            put {
              // format: off
              // parse the request to a command
              // send command to the bank
              // expect a reply
              // send back and http response
              // format: on
              entity(as[BankAccountUpdateRequest]) { request =>
                validateRequest(request) {
                  onSuccess(updateBankAccount(id, request)) {
                    case BankAccountBalanceUpdatedResponse(Right(bankAccount)) =>
                      complete(StatusCodes.OK, bankAccount)
                    case BankAccountBalanceUpdatedResponse(Left(error)) =>
                      val errorMsg = error match
                        case PersistentBankAccount.BankError.AccountNotFound   => s"Bank account $id not found"
                        case PersistentBankAccount.BankError.InsufficientFunds => s"Insufficient funds for account $id"
                      complete(StatusCodes.BadRequest, FailureResponse(errorMsg))
                  }
                }
              }
            }
        }
    }
end BankRoutes
