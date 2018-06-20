package authentication.models

import play.api.mvc.Headers

import scala.concurrent.Future

trait AuthService {
  type U

  def getUserInfo(headers: Headers): Future[U]
}
