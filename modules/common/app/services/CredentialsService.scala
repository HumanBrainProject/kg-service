
/*
 * Copyright 2018 - 2021 Swiss Federal Institute of Technology Lausanne (EPFL)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This open source software code was developed in part or in whole in the
 * Human Brain Project, funded from the European Union's Horizon 2020
 * Framework Programme for Research and Innovation under
 * Specific Grant Agreements No. 720270, No. 785907, and No. 945539
 * (Human Brain Project SGA1, SGA2 and SGA3).
 *
 */

package services

import java.io.FileInputStream

import com.google.inject._
import play.api.Configuration
import play.api.libs.json.Json

trait Credentials {
  def getClientCredentials(): ClientCredentials
}

case class ClientCredentials(refreshToken:String, clientId:String, clientSecret:String, openidHost: String)

@Singleton
class CredentialsService @Inject()(configuration: ConfigurationService) extends Credentials {
  var clientCredentials: Option[ClientCredentials] = None
  val fileName = s"${configuration.refreshTokenFile}/oidc"
  override def getClientCredentials(): ClientCredentials = {
    if(clientCredentials.isEmpty){

      val stream = new FileInputStream(fileName)
      try {
        val json = Json.parse(stream)
        clientCredentials = Some(ClientCredentials((json \ "refresh_token").as[String], (json \ "client_id").as[String], (json \ "client_secret").as[String], (json \ "openid_host").as[String] ))
        clientCredentials.get
      } finally {
        stream.close()
      }
    }else{
      clientCredentials.get
    }
  }
}
