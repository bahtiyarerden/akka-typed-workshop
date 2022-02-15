package io.workshop.auction

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior, PostStop }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives.concat

import scala.util.{ Failure, Success }

object AuctioneerApp {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message

  private final case class Started(binding: ServerBinding) extends Message

  case object Stop extends Message

  def apply(host: String, port: Int): Behavior[Message] = Behaviors.setup { ctx =>
    implicit val system: ActorSystem[_] = ctx.system
    val persistenceId                   = system.settings.config.getString("auction.manager-persistence-id")
    val auctionManagerActor             = ctx.spawn(AuctionManagerEntity(persistenceId), "AuctionManager")
    val auctionRoute                    = new AuctionServiceImpl(auctionManagerActor)(ctx.system).route
    val lotRoute                        = new LotServiceImpl(auctionManagerActor)(ctx.system).route
    val wsRoute                         = new BiddingWebSocketServiceImpl(auctionManagerActor)(ctx.system).route

    val routes = concat(auctionRoute, lotRoute, wsRoute)

    val serverBinding = Http().newServerAt(host, port).bind(routes)
    ctx.pipeToSelf(serverBinding) {
      case Success(binding) => Started(binding)
      case Failure(ex)      => StartFailed(ex)
    }

    def running(binding: ServerBinding): Behavior[Message] =
      Behaviors
        .receiveMessagePartial[Message] { case Stop =>
          ctx.log.info(
            "Stopping server http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort
          )
          Behaviors.stopped
        }
        .receiveSignal { case (_, PostStop) =>
          binding.unbind()
          Behaviors.same
        }

    def starting(wasStopped: Boolean): Behaviors.Receive[Message] =
      Behaviors.receiveMessage[Message] {
        case StartFailed(cause) =>
          throw new RuntimeException("Server failed to start", cause)
        case Started(binding) =>
          ctx.log.info(
            "Server online at http://{}:{}/",
            binding.localAddress.getHostString,
            binding.localAddress.getPort
          )
          if (wasStopped) ctx.self ! Stop
          running(binding)
        case Stop => starting(wasStopped = true)
      }

    starting(wasStopped = false)
  }

  def main(args: Array[String]): Unit =
    ActorSystem(AuctioneerApp("localhost", 8080), "BuildAuctioneerServer")
}
