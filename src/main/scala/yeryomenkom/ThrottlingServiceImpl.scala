package yeryomenkom

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ThrottlingServiceImpl(override val graceRps: Int, override val slaService: SlaService)
                           (implicit val throttlingTimeout: Timeout = Timeout(5.millis))
  extends ThrottlingService {

  private implicit val system: ActorSystem = ActorSystem("ThrottlingSystem")

  private val requestsManagerActor =
    system.actorOf(RequestsManagerActor.props(slaService, graceRps))

  override def isRequestAllowed(token: Option[String]): Boolean = {
    val result = (requestsManagerActor ? RequestsManagerActor.IsRequestAllowed(token))
      .map {
        case ThrottlingActor.RequestsAllowed => true
        case ThrottlingActor.RequestNotAllowed => false
      }
      .recover { case _ => false }

    Await.result(result, Duration.Inf)
  }
}

