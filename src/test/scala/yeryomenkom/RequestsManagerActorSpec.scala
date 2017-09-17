package yeryomenkom

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._

class RequestsManagerActorSpec extends TestKit(ActorSystem())
  with WordSpecLike with MustMatchers with ImplicitSender with StopSystemAfterAll {

  private val waitingNoMsg = 1.second
  private val testUserToken = "userToken"
  private val testUser = "user"
  private val testSla = Sla(testUser, 10)
  private val testSlaService = new SlaService {
    override def getSlaByToken(token: String): Future[Sla] = Future.successful(testSla)
  }

  "RequestsManagerActor" must {

    "send request to grace throttling actor when user token not provided." in {
      val graceThrottlingActor = TestProbe()
      val userThrottlingActor = TestProbe()
      val requestsManagerActor = system.actorOf(
        RequestsManagerActor.props(
          slaService = testSlaService,
          graceRps = 10,
          graceThrottlingActorCreator = (_, _, _, _) => graceThrottlingActor.ref,
          userThrottlingActorCreator = (_, _, _, _) => userThrottlingActor.ref
        )
      )

      requestsManagerActor ! RequestsManagerActor.IsRequestAllowed(None)
      graceThrottlingActor.expectMsgType[ThrottlingActor.IsRequestAllowed.type]
      userThrottlingActor.expectNoMsg(waitingNoMsg)
    }

    "send request to grace throttling actor when user token provided but no user sla is cached." in {
      val graceThrottlingActor = TestProbe()
      val userThrottlingActor = TestProbe()
      val requestsManagerActor = system.actorOf(
        RequestsManagerActor.props(
          slaService = testSlaService,
          graceRps = 10,
          graceThrottlingActorCreator = (_, _, _, _) => graceThrottlingActor.ref,
          userThrottlingActorCreator = (_, _, _, _) => userThrottlingActor.ref
        )
      )

      requestsManagerActor ! RequestsManagerActor.IsRequestAllowed(Some(testUserToken))
      graceThrottlingActor.expectMsgType[ThrottlingActor.IsRequestAllowed.type]
      userThrottlingActor.expectNoMsg(waitingNoMsg)
    }

    "send request to user throttling actor when user token provided and user sla is cached." in {
      val graceThrottlingActor = TestProbe()
      val userThrottlingActor = TestProbe()
      val requestsManagerActor = system.actorOf(
        RequestsManagerActor.props(
          slaService = testSlaService,
          graceRps = 10,
          graceThrottlingActorCreator = (_, _, _, _) => graceThrottlingActor.ref,
          userThrottlingActorCreator = (_, _, _, _) => userThrottlingActor.ref
        )
      )

      requestsManagerActor ! RequestsManagerActor.SlaResolved(testUserToken, testSla)
      requestsManagerActor ! RequestsManagerActor.IsRequestAllowed(Some(testUserToken))
      userThrottlingActor.expectMsgType[ThrottlingActor.IsRequestAllowed.type]
      graceThrottlingActor.expectNoMsg(waitingNoMsg)
    }

  }

}
