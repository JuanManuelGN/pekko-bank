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
/* 1. El arranque (@main def main) La aplicación comienza a ejecutarse aquí.
 * La línea ActorSystem(rootBehavior, "bankSystem") es el "Big Bang" de tu aplicación. En este momento, Pekko reserva la
 * memoria, levanta sus thread pools (dispatchers) y arranca tu actor principal (el Guardián, definido por
 * rootBehavior).
 *
 * 2. La inicialización del Guardián (rootBehavior) En el mismo milisegundo en que se crea el ActorSystem, el Guardián
 * entra en su bloque Behaviors.setup. Aquí ocurren dos cosas críticas:
 *
 * context.spawn(Bank(), "bank"): El Guardián crea inmediatamente a su primer hijo, el actor Bank. (Si tuvieras más
 * actores globales o bases de datos, los instanciarías aquí).
 *
 * Behaviors.receiveMessage: Tras crear al hijo, el Guardián se queda bloqueado en modo "escucha", esperando recibir un
 * mensaje de tipo RetrieveBankActor.
 *
 * 3. El puente entre el mundo normal y los actores (system.ask) Tú estás en el hilo principal de Java/Scala (main), que
 * está "fuera" del sistema de actores. Pekko HTTP necesita la referencia del actor Bank para poder enviarle peticiones
 * web, pero esa referencia está atrapada dentro del Guardián.
 *
 * Al usar system.ask, creas un buzón temporal y le gritas al Guardián: "¡Oye, envíame la referencia de tu hijo!".
 *
 * El Guardián recibe el RetrieveBankActor, saca la referencia de su hijo y te la envía de vuelta al buzón temporal
 * (replyTo ! bankActor).
 *
 * Como esto es asíncrono, el resultado te llega encapsulado en un Future[ActorRef[Command]].
 *
 * 4. La inyección de dependencias (foreach(startHttpServer)) Una vez que el Future se completa (es decir, el Guardián
 * te ha entregado el actor Bank), llamas a la función startHttpServer inyectándole esa referencia.
 *
 * 5. Levantando el servidor Web (startHttpServer) Instancias tus rutas (new BankRoutes(bank)), dándoles acceso directo
 * al actor para que puedan procesar los JSON.
 *
 * Http().newServerAt(...).bind(routes): Pekko HTTP abre el puerto 8080 de tu máquina y empieza a escuchar tráfico real
 * de red.
 *
 * Como abrir un puerto del sistema operativo puede tardar unos milisegundos (o fallar si el puerto está ocupado),
 * devuelve un httpBindingFuture. El bloque .onComplete simplemente loguea si el servidor ha arrancado con éxito o si ha
 * crasheado. */

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
