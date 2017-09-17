package yeryomenkom

import akka.actor.{Actor, Props}
import yeryomenkom.ThrottlingActor._

import scala.concurrent.duration.FiniteDuration

class ThrottlingActor(val requests: RequestsCount, val per: PerInterval, val trackedPeriod: TrackedPeriod)
  extends Actor {

  assert(trackedPeriod <= per, "Tracked period should less or equal to per!")

  import context._

  system.scheduler.schedule(
    trackedPeriod,
    trackedPeriod,
    self,
    RequestsAllowed(requests / (per / trackedPeriod).toInt)
  )

  override def receive: Receive = working(requests)

  private def working(allowedRequestsCount: RequestsCount): Receive = {

    case RequestsAllowed(requestsCount) =>
      become(working(requests min allowedRequestsCount + requestsCount))

    case IsRequestAllowed =>
      if (allowedRequestsCount == 0) sender() ! RequestNotAllowed
      else {
        sender() ! RequestsAllowed
        become(working(allowedRequestsCount - 1))
      }

  }

}

object ThrottlingActor {

  type RequestsCount = Int
  type PerInterval = FiniteDuration
  type TrackedPeriod = FiniteDuration

  def props(requests: RequestsCount, per: PerInterval, trackedPeriod: TrackedPeriod) =
    Props(new ThrottlingActor(requests, per, trackedPeriod))

  case class RequestsAllowed(requestsCount: RequestsCount)
  case object IsRequestAllowed

  case object RequestAllowed
  case object RequestNotAllowed

}