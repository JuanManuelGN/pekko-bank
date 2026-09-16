package es.actors

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.Effect
import org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehavior

// a single bank account
object PersistentBankAccount:

  // commands = message
  enum Command:
    case CreateBankAccount(
        user: String,
        currency: String,
        initialBalance: Double,
        replyTo: ActorRef[Response]
    )
    case UpdateBalance(
        id: String,
        currency: String,
        amount: Double,
        replyTo: ActorRef[Response]
    )
    case GetBankAccount(id: String, replyTo: ActorRef[Response])

  // events = to persist to Cassandra
  enum Event:
    case BankAccountCreated(bankAccount: BankAccount)
    case BalanceUpdated(amount: Double)

  // state
  case class BankAccount(
      id: String,
      user: String,
      currency: String,
      balance: Double
  )

  // errors
  enum BankError:
    case AccountNotFound, InsufficientFunds

  // responses
  enum Response:
    case BankAccountCreatedResponse(id: String)
    case BankAccountBalanceUpdatedResponse(
        maybeBankAccount: Either[BankError, BankAccount]
    )
    case GetBankAccountResponse(maybeBankAccount: Option[BankAccount])

  import BankError.*
  import Command.*
  import Event.*
  import Response.*

  // command handler = message handler => persist and event
  val commandHandler: (BankAccount, Command) => Effect[Event, BankAccount] =
    (state, command) =>
      command match
        case CreateBankAccount(user, currency, initialBalance, bank) =>
          val id = state.id
          Effect
            .persist(
              BankAccountCreated(
                BankAccount(id, user, currency, initialBalance)
              )
            )
            .thenReply(bank)(_ => BankAccountCreatedResponse(id))
        case UpdateBalance(_, _, amount, bank) =>
          val newBalance = state.balance + amount
          if newBalance < 0 then Effect.reply(bank)(BankAccountBalanceUpdatedResponse(Left(InsufficientFunds)))
          else
            Effect
              .persist(BalanceUpdated(amount))
              .thenReply(bank)(newState => BankAccountBalanceUpdatedResponse(Right(newState)))
        case GetBankAccount(_, bank) =>
          Effect.reply(bank)(GetBankAccountResponse(Some(state)))

  // event handler => update an state
  val eventHandler: (BankAccount, Event) => BankAccount = (state, event) =>
    event match
      case BankAccountCreated(bankAccount) =>
        bankAccount
      case BalanceUpdated(amount) =>
        state.copy(balance = state.balance + amount)

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Event, BankAccount](
      persistenceId = PersistenceId.ofUniqueId(id),
      emptyState = BankAccount(id, "", "", 0.0),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )
end PersistentBankAccount
