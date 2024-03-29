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

package helpers

import models.BasicAccessToken
import models.user.IDMUser
import play.api.http.HeaderNames._
import play.api.mvc.Request

object OIDCHelper {

  /**
    * Retrieve the user's token from a request
    * @param request the request
    * @return A String containing the token or an empty String
    */
  def getTokenFromRequest[A](request: Request[A]): BasicAccessToken = {
    BasicAccessToken(request.headers.toMap.getOrElse(AUTHORIZATION, Seq("")).head)
  }

  /**
    * If the user is allowed, return the ES index requested by the user,
    * or else return the public index
    *
    * @param userInfo The user's info
    * @param hints The requested ES index
    * @return The requested ES index or the public index
    */
  def getESIndex(userInfo: IDMUser, hints: String): String = {
    val groups = ESHelper.filterNexusGroups(userInfo.groups)
    val h = hints.trim
    if (groups.contains(h)) {
      ESHelper.transformToIndex(h)
    } else {
      ESHelper.publicIndex
    }
  }
}
