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

import com.google.inject.{Inject, Singleton}
import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

@Singleton
class ConfigurationService @Inject()(configuration: Configuration) {
  val esHost: String = configuration.get[String]("es.host")
  val refreshTokenFile: String = configuration.get[String]("auth.refreshTokenFile")
  val oidcEndpoint = s"${configuration.get[String]("auth.endpoint")}/oidc"
  val oidcUserInfoEndpoint = s"$oidcEndpoint/userinfo"
  val oidcTokenEndpoint = s"$oidcEndpoint/token"
  val cacheExpiration: FiniteDuration = configuration.get[FiniteDuration]("proxy.cache.expiration")
  val nexusEndpoint: String =
    configuration.getOptional[String]("nexus.endpoint").getOrElse("https://nexus-dev.humanbrainproject.org")
  val reconciledPrefix: String = configuration.getOptional[String]("nexus.reconciled.prefix").getOrElse("reconciled")
  val editorPrefix: String = configuration.getOptional[String]("nexus.editor.prefix").getOrElse("editor")
  val kgQueryEndpoint: String = configuration.getOptional[String]("kgquery.endpoint").getOrElse("http://localhost:8600")
  val iamEndpoint = configuration.get[String]("nexus.iam")
  val authEndpoint = configuration.get[String]("auth.endpoint")
  val idmApiEndpoint = s"$authEndpoint/idm/v1/api"
}
