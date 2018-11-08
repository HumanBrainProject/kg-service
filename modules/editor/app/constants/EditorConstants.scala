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
package constants

import models.NexusPath

object EditorConstants {
  val EDITORNAMESPACE = "http://hbp.eu/kgeditor/"
  val BOOKMARKLIST = s"${EDITORNAMESPACE}bookmarkList"
  val BOOKMARKINSTANCELINK = s"${EDITORNAMESPACE}bookmarkInstanceLink"
  val BOOKMARKLISTFOLDER = s"${EDITORNAMESPACE}bookmarkListFolder"
  val USER = s"${EDITORNAMESPACE}user"
  val FOLDERTYPE = s"${EDITORNAMESPACE}folderType"

  val IDRESPONSEFIELD= "relativeUrl"

  val commonNodeTypes = List("minds/core/dataset/v1.0.0")
  val bookmarkListFolderPath = NexusPath("kg", "core", "bookmarklistfolder", "v0.0.1")
  val bookmarkListPath = NexusPath("kg", "core", "bookmarklist", "v0.0.1")
  val bookmarkPath = NexusPath("kg", "core", "bookmark", "v0.0.1")
}
