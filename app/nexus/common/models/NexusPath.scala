package nexus.common.models

case class NexusPath(org: String, domain:String, schema: String, version:String) {

  override def toString() = {
    Seq(org, domain, schema, version).mkString("/")
  }
}

object NexusPath {
    def apply(args: Seq[String]): NexusPath = {
      NexusPath(args(0), args(1), args(2), args(3))
    }

    def apply(fullPath: String): NexusPath = {
      NexusPath(fullPath.split("/"))
    }
}
