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

import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.duration.FiniteDuration

object ConfigMock {
  val nexusEndpoint: String = "http://www.nexus.com"
  val reconcileEndpoint: String = "http://www.reconcile.com"
  val idm = "https://services.humanbrainproject.eu/idm/v1/api"
  val userInfo = "https://userinfo.com"
  val esHost = "https://eshost.com"
  val reconciledPrefix = "reconciled"
  val editorPrefix = "editor"
  val kgQueryEndpoint = "kgqueryEndpoint"
  val refreshTokenFile = "/opt/tokenfolder"
  val authEndpoint = "auth.com"
  val cacheExpiration = FiniteDuration(10, "min")
  val nexusIam = "nexus-iam.com"

  val fakeApplicationConfig = GuiceApplicationBuilder().configure(
    "play.http.filters" -> "play.api.http.NoHttpFilters",
    "nexus.endpoint" -> nexusEndpoint,
    "reconcile.endpoint" -> reconcileEndpoint,
    "idm.api" -> idm,
    "auth.userinfo" -> userInfo,
    "es.host" -> esHost,
    "nexus.reconciled.prefix" -> reconciledPrefix,
    "nexus.editor.prefix" -> editorPrefix,
    "kgquery.endpoint" -> kgQueryEndpoint,
    "auth.refreshTokenFile" -> refreshTokenFile,
    "auth.endpoint"-> authEndpoint,
    "proxy.cache.expiration" -> cacheExpiration.toMillis,
    "nexus.iam" -> nexusIam
  )
}
