package yeryomenkom

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.pipe
import yeryomenkom.RequestsManagerActor._

import scala.concurrent.duration._

class RequestsManagerActor(val slaService: SlaService,
                           val graceRps: Int,
                           graceThrottlingActorCreator: ThrottlingActorCreator,
                           userThrottlingActorCreator: ThrottlingActorCreator)
  extends Actor {

  import context._

  private val throttlingActorGrace: ActorRef = createThrottlingActor(graceThrottlingActorCreator, graceRps)
  private var tokensWaitingForSla: Set[String] = Set.empty
  private var slaByUserToken: Map[String, Sla] = Map.empty
  private var throttlingActorsByUser: Map[String, ActorRef] = Map.empty


  override def receive: Receive = {
    case IsRequestAllowed(userToken) =>
      val targetThrottlingActor = userToken.flatMap(slaByUserToken.get) match {
        case Some(sla) => getOrCreateThrottlingActor(sla)
        case None =>
          userToken.foreach { token =>
            if (!tokensWaitingForSla.contains(token)) getSlaForUserToken(token) pipeTo self
          }
          throttlingActorGrace
      }
      targetThrottlingActor forward ThrottlingActor.IsRequestAllowed

    case SlaResolved(token, sla) =>
      tokensWaitingForSla -= token
      slaByUserToken = slaByUserToken.updated(token, sla)

    case ErrorWhileResolvingSla(token) =>
      tokensWaitingForSla -= token
  }

  private def getSlaForUserToken(token: String) = {
    tokensWaitingForSla += token
    slaService.getSlaByToken(token)
      .map(SlaResolved(token, _))
      .recover { case _ => ErrorWhileResolvingSla(token) }
  }

  private def getOrCreateThrottlingActor(sla: Sla) = {
    throttlingActorsByUser.getOrElse(sla.user, {
      val throttlingActor = createThrottlingActor(userThrottlingActorCreator, sla.rps)
      throttlingActorsByUser = throttlingActorsByUser.updated(sla.user, throttlingActor)
      throttlingActor
    })
  }

  private def createThrottlingActor(creator: ThrottlingActorCreator, rps: Int) = {
    creator(system, rps, 1.second, 1.second / 10)
  }

}

object RequestsManagerActor {

  type ThrottlingActorCreator =
    (ActorSystem, ThrottlingActor.RequestsCount, ThrottlingActor.PerInterval, ThrottlingActor.TrackedPeriod) => ActorRef


  def props(slaService: SlaService, graceRps: Int) = {
    val throttlingActorCreator: ThrottlingActorCreator =
      (system, rps, per, trackedPeriod) => system.actorOf(ThrottlingActor.props(rps, per, trackedPeriod))
    Props(new RequestsManagerActor(slaService, graceRps, throttlingActorCreator, throttlingActorCreator))
  }

  def props(slaService: SlaService,
            graceRps: Int,
            graceThrottlingActorCreator: ThrottlingActorCreator,
            userThrottlingActorCreator: ThrottlingActorCreator) = Props(
    new RequestsManagerActor(slaService, graceRps, graceThrottlingActorCreator, userThrottlingActorCreator)
  )


  case class IsRequestAllowed(userToken: Option[String])
  case class SlaResolved(token: String, sla: Sla)
  case class ErrorWhileResolvingSla(token: String)

  case object RequestAllowed
  case object RequestNotAllowed

}
