package es.app

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.util.Timeout

import es.actors.Bank
import es.actors.PersistentBankAccount.Command
import es.http.BankRoutes

object BankApp:
  enum RootCommand:
    case RetrieveBankActor(replyTo: ActorRef[ActorRef[Command]])
  import RootCommand.*

  val rootBehavior: Behavior[RootCommand] = Behaviors.setup { context =>
    val bankActor = context.spawn(Bank(), "bank")
    Behaviors.receiveMessage { case RetrieveBankActor(replyTo) =>
      replyTo ! bankActor
      Behaviors.same
    }
  }

  def startHttpServer(bank: ActorRef[Command])(using system: ActorSystem[?]): Unit =
    given ec: ExecutionContext = system.executionContext

    val router = new BankRoutes(bank)
    val routes = router.routes

    val httpBindingFuture = Http().newServerAt("localhost", 8080).bind(routes)
    httpBindingFuture.onComplete {
      case Success(value) =>
        val address = value.localAddress
        system.log.info(s"Server online at http://${address.getHostName}:${address.getPort}")
      case Failure(exception) =>
        system.log.error("Failed to start server", exception)
    }

  @main def main(): Unit =

    given system: ActorSystem[RootCommand] = ActorSystem(rootBehavior, "bankSystem")
    given timeout: Timeout                 = Timeout(5.seconds)
    given ec: ExecutionContext             = system.executionContext

    val bankActorFuture: Future[ActorRef[Command]] = system.ask(replyTo => RetrieveBankActor(replyTo))

    bankActorFuture.foreach(startHttpServer)
end BankApp
