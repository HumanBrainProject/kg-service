package authentication.models

import play.api.libs.json.JsObject

case class UserInfo(json: JsObject) {

  import UserInfo._

  val id = (json \ idLabel).asOpt[String].getOrElse("")
  val name = (json \ nameLabel).asOpt[String].getOrElse("")
  val email = (json \ emailLabel).asOpt[String].getOrElse("")
  val groups = (json \ groupsLabel).asOpt[String].getOrElse("").split(",").toSeq

}

object UserInfo {
  val idLabel = "sub"
  val nameLabel = "name"
  val emailLabel = "email"
  val groupsLabel = "groups"
  val mandatoryFields = Seq(idLabel, nameLabel, emailLabel, groupsLabel)
}
