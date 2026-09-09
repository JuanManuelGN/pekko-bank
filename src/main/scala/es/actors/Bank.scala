package es.actors

import java.util.UUID

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.Effect
import org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehavior

class Bank:

  import PersistentBankAccount.Command
  // comands = messages
  import PersistentBankAccount.Command.*
  import PersistentBankAccount.Response.*
  // events
  sealed trait Event
  case class BankAccountCreated(id: String) extends Event

  // state
  case class BankState(accounts: Map[String, ActorRef[Command]])

  // command handler
  def commandHandler(ctx: ActorContext[Command]): (BankState, Command) => Effect[Event, BankState] =
    (bankState, command) =>
      command match
        case createCommand @ CreateBankAccount(_, _, _, _) =>
          val id             = UUID.randomUUID().toString
          val newBankAccount = ctx.spawn(PersistentBankAccount(id), id)
          Effect
            .persist(BankAccountCreated(id))
            .thenReply(newBankAccount)(_ => createCommand)
        case updateCommand @ UpdateBalance(id, _, _, replyTo) =>
          bankState.accounts.get(id) match
            case Some(account) =>
              Effect.reply(account)(updateCommand)
            case None =>
              Effect.reply(replyTo)(BankAccountBalanceUpdatedResponse(None))
        case getCommand @ GetBankAccount(id, replyTo) =>
          bankState.accounts.get(id) match
            case Some(account) =>
              Effect.reply(account)(getCommand)
            case None =>
              Effect.reply(replyTo)(GetBankAccountResponse(None))

  // event hanlder
  def eventHandler(ctx: ActorContext[Command]): (BankState, Event) => BankState = (bank, event) =>
    event match
      case BankAccountCreated(id) =>
        val account = ctx
          .child(id) // exist after command handler
          .getOrElse(
            ctx.spawn(PersistentBankAccount(id), id)
          ) // does not exist in the recovery mode, so needs to be recovered
          .asInstanceOf[ActorRef[Command]]
        bank.copy(bank.accounts + (id -> account))

  // behavior
  def apply(id: String): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior(
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = BankState(Map.empty),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }
end Bank


