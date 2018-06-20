package helpers

import com.google.inject.Inject
import controllers.Authentication.{AuthProvider, OIDCAuthProvider}
import models.{UserInfo, UserRequest}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Cobbled this together from:
  * https://www.playframework.com/documentation/2.6.x/ScalaActionsComposition#Authentication
  */
class AuthenticatedUserAction @Inject()(parser: BodyParsers.Default, authprovider: OIDCAuthProvider)(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[UserRequest, AnyContent] {
  private val logger = play.api.Logger(this.getClass)
  override def invokeBlock[A](request: Request[A],
                              block: (UserRequest[A]) => Future[Result]): Future[Result] = {
    authprovider.getUserInfo(request.headers).flatMap{ res =>
      block(new UserRequest(res, request))
    }
  }

}