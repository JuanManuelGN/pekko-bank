package es.app

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http

import es.actors.Bank
import es.http.BankRoutes

object BankApp2:

  val rootBehavior: Behavior[Nothing] = Behaviors.setup { context =>
    // 1. El Guardián crea el actor Bank directamente
    val bankActor = context.spawn(Bank(), "bank")

    // 2. Extraemos los implicits necesarios del sistema
    given system: ActorSystem[Nothing] = context.system
    given ec: ExecutionContext         = context.executionContext

    // 3. Arrancamos el servidor HTTP directamente desde dentro del setup
    val routes        = new BankRoutes(bankActor).routes
    val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    bindingFuture.onComplete {
      case Success(binding) =>
        context.log.info(s"Server online at http://${binding.localAddress.getHostName}:${binding.localAddress.getPort}")
      case Failure(exception) =>
        context.log.error("Failed to start server", exception)
        context.system.terminate()
    }

    // El Guardián no necesita recibir ningún mensaje, su único trabajo era arrancar todo.
    Behaviors.empty
  }

  /*@main*/ def main(): Unit =
    // El punto de entrada se reduce a una sola línea.
    ActorSystem(rootBehavior, "bankSystem")
end BankApp2
