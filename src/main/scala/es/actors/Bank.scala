package es.actors

import java.util.UUID

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.scaladsl.ActorContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.persistence.typed.PersistenceId
import org.apache.pekko.persistence.typed.scaladsl.Effect
import org.apache.pekko.persistence.typed.scaladsl.EventSourcedBehavior
import org.apache.pekko.util.Timeout

import es.actors.PersistentBankAccount.Command.CreateBankAccount
import es.actors.PersistentBankAccount.Command.GetBankAccount
import es.actors.PersistentBankAccount.Response
import es.actors.PersistentBankAccount.Response.BankAccountCreatedResponse
import es.actors.PersistentBankAccount.Response.GetBankAccountResponse

object Bank:

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
        case createCmd @ CreateBankAccount(_, _, _, _) =>
          val id = UUID.randomUUID().toString
          ctx.log.info(s"creating a bank account with id $id")
          val newBankAccount = ctx.spawn(PersistentBankAccount(id), id)
          Effect
            .persist(BankAccountCreated(id))
            .thenReply(newBankAccount)(_ => createCmd)
        case updateCmd @ UpdateBalance(id, _, _, replyTo) =>
          ctx.log.info("updating a bank account {}", id)
          bankState.accounts.get(id) match
            case Some(account) => Effect.reply(account)(updateCmd)
            case None =>
              Effect.reply(replyTo)(BankAccountBalanceUpdatedResponse(Left(PersistentBankAccount.AccountNotFound)))
        case getCmd @ GetBankAccount(id, replyTo) =>
          ctx.log.info("get a bank account {}", id)
          bankState.accounts.get(id) match
            case Some(account) => Effect.reply(account)(getCmd)
            case None          => Effect.reply(replyTo)(GetBankAccountResponse(None))

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
  def apply(): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior(
      persistenceId = PersistenceId.ofUniqueId("bank"),
      emptyState = BankState(Map.empty),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler(context)
    )
  }
end Bank

object BankPlayground:
  @main def main(): Unit =
    val rootBehavior: Behavior[NotUsed] = Behaviors.setup { context =>
      val bank   = context.spawn(Bank(), "bank")
      val logger = context.log

      val responseHandler = context.spawn(
        Behaviors.receiveMessagePartial[Response] {
          case BankAccountCreatedResponse(id) =>
            logger.info(s"bank account $id has been created")
            Behaviors.same
          case GetBankAccountResponse(maybeBankAccount) =>
            logger.info("bank account {} has been retrieved", maybeBankAccount)
            Behaviors.same
        },
        "replyHandler"
      )

      // ask pattern
      import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
      import scala.concurrent.ExecutionContext
      import scala.concurrent.duration.*

      implicit val timeout: Timeout     = Timeout(10.seconds)
      implicit val scheduler: Scheduler = context.system.scheduler
      implicit val ec: ExecutionContext = context.executionContext

      // bank ! CreateBankAccount("juan", "EUR", 100, responseHandler)
      bank ! GetBankAccount("8e34d54b-d328-4960-a9f6-d6223caed051", responseHandler)

      Behaviors.empty
    }
    val system = ActorSystem(rootBehavior, "Bank")
  end main

end BankPlayground
