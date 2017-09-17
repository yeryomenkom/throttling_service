package yeryomenkom

import scala.concurrent.Future

trait SlaService {
  def getSlaByToken(token: String): Future[Sla]
}