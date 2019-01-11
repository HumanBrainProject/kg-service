/*
 *   Copyright (c) 2018, EPFL/Human Brain Project PCO
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package models.commands
import models.NexusPath
import models.errors.APIEditorError
import models.instance.{LinkingInstance, NexusInstance, NexusInstanceReference, NexusLink}
import play.api.Logger
import services.EditorService

import scala.concurrent.{ExecutionContext, Future}

case class AddLinkingInstanceCommand(
  targetId: NexusLink,
  currentInstanceRef: NexusInstanceReference,
  linkingInstanceType: String,
  linkingInstancePath: NexusPath,
  editorService: EditorService,
  baseUrl: String,
  token: String
) extends Command {
  val log = Logger(this.getClass)
  override def execute()(implicit executionContext: ExecutionContext): Future[Either[APIEditorError, Unit]] = {
    val linkingInstance = LinkingInstance(
      s"$baseUrl/v0/data/${currentInstanceRef.toString}",
      s"$baseUrl/v0/data/${targetId.ref.toString}",
      linkingInstanceType
    )
    editorService.insertInstance(NexusInstance(None, linkingInstancePath, linkingInstance.toNexusFormat), token).map {
      case Right(i) =>
        log.debug(
          s"Added linking instance with id ${i.id}, from ${targetId.ref.toString} - to ${currentInstanceRef.toString}"
        )
        Right(())
      case Left(err) =>
        log.error(s"Could not add linking instance with to ${currentInstanceRef.toString}")
        Left(err)
    }
  }
}