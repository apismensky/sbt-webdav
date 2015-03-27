package webdav

import com.github.sardine.{SardineFactory, Sardine}
import sbt._
import std.TaskStreams

/**
 * Convenience class to be able to write a String with '/' in code.
 */
object StringPath {
  class StringPath(val path: String) {
    def / (part: String): String = path +
      (if(path.endsWith("/")) "" else "/") +
      (if(part.startsWith("/")) part.substring(1) else part)

    def asPath: String = "/" + path.replace('.','/')
  }
  implicit def string2StringPath(path: String) = new StringPath(path)
}

object WebDavPlugin extends Plugin {

  trait WebDavKeys {
    lazy val webdav = config("webdav")
    lazy val mkcol = TaskKey[Unit]("mkcol", "Make collections (folder) in remote WebDav location.")
  }

  trait MkCol {
    import StringPath._

    /**
     * Create artifact pathParts
     * -when is sbtPlugin then sbt version must be added to path
     * -when not crossPaths then not add any version number to path
     * -otherwise add scala version to path
     *
     * -when Scala 2.10.x then only add 2.10 to path
     * -otherwise add whole version to path (e.g. 2.9.2)
     */
    def createPaths(organization: String, artifactName: String, version: String, crossScalaVersions: Seq[String],
                    sbtVersion: String, crossPaths: Boolean, mavenStyle: Boolean, isSbtPlugin: Boolean) = {
      if(crossPaths){
        crossScalaVersions map { scalaVersion =>
          def topLevel(v: String, level: Int) = v split '.' take level mkString "."
          // The publish location for Scala 2.10.x is only '2.10', for Scala 2.9.x it is '2.9.x' !
          val scalaVer = if(scalaVersion startsWith "2.10") topLevel(scalaVersion, 2) else scalaVersion

          if (isSbtPlugin) {
            // e.g. /com/organization/artifact_2.9.2_0.12/0.1
            organization.asPath / (("%s_%s_%s") format (artifactName, scalaVer, topLevel(sbtVersion,2))) / version
          } else {
            // e.g. /com/organization/artifact_2.9.2/0.1
            organization.asPath / (("%s_%s") format (artifactName, scalaVer)) / version
          }
        }
      }else{
        // e.g. /com/organization/artifact/0.1
        Seq( organization.asPath / artifactName / version )
      }
    }
    /**
     * Return all collections (folder) for path.
     * @param path "/part/of/url"
     * @return List("part","part/of","part/of/url")
     */
    def pathCollections(path: String) = {
      def pathParts(path: String) = path.substring(1) split "/" toSeq
      def addPathToUrls(urls: List[String], path: String) = {
        if(urls.isEmpty) List(path)
        else urls :+ urls.last / path
      }

      pathParts(path).foldLeft(List.empty[String])(addPathToUrls)
    }


    /**
     * Get Maven root from Resolver. Returns None if Resolver is not MavenRepository.
     */
    def mavenRoot(resolver: Option[Resolver]) = resolver match {
      case Some(m: MavenRepository) => Some(m.root)
      case _ => None
    }

    def publishToUrls(paths: Seq[String], resolver: Option[Resolver]) =  resolver match {
      case Some(m: MavenRepository) => {
        Some(paths map { path =>
          m.root / path
        })
      }
      case _ => None
    }

    /**
     * Check if url exists.
     */
    def exists(sardine: Sardine, url: String): Either[Throwable,Boolean] = {
      try {
        Right(sardine.exists(url))
      } catch {
        case e: Throwable => Left(e)
      }
    }

    /**
     * Make collector (folder) for all paths.
     */
    def mkcol(sardine: Sardine, urlRoot: String, paths: List[String], logger: Logger) = {
      val notExistMsg =  "Root '%s' does not exist."
      val errorMsg = "Could not access '%s'."
      exists(sardine, urlRoot) match {
        case Left(e) => throw new MkColException(errorMsg.format(urlRoot), e)
        case Right(b) if !b => throw new MkColException(notExistMsg format urlRoot)
        case _ =>
          paths foreach { path =>
            val fullUrl = urlRoot / path
            exists(sardine, fullUrl) match {
              case Left(e) => throw new MkColException(errorMsg.format(fullUrl), e)
              case Right(b) if !b =>
                logger.info("WebDav: Creating collection '%s'" format fullUrl)
                sardine.createDirectory(fullUrl)
              case _ => logger.info("WebDav: Found collection '%s'" format fullUrl)
            }
          }
      }
    }

    val hostRegex = """^http[s]?://([a-zA-Z0-9\.\-]*)/.*$""".r
    def getCredentialsForHost(publishTo: Option[Resolver], creds: Seq[Credentials], streams: TaskStreams[_]) = {
      mavenRoot(publishTo) flatMap { root =>
        val hostRegex(host) = root
        Credentials.allDirect(creds) find {
          case c: DirectCredentials => {
            streams.log.info("WebDav: Found credentials for host: "+c.host)
            c.host == host
          }
          case _ => false
        }
      }
    }

    /**
     * Creates a collection for all artifacts that are going to be published
     * if the collection does not exist yet.
     */
    def mkcolAction(organization: String, artifactName: String, version: String, crossScalaVersions: Seq[String], sbtVersion: String,
                    crossPaths: Boolean, publishTo: Option[Resolver], credentialsSet: Seq[Credentials], streams: TaskStreams[_],
                    mavenStyle: Boolean, sbtPlugin: Boolean) = {
      streams.log.info("WebDav: Check whether (new) collection need to be created.")
      val artifactPaths = createPaths(organization, artifactName, version, crossScalaVersions, sbtVersion, crossPaths, mavenStyle, sbtPlugin)
      val artifactPathParts = artifactPaths map pathCollections

      def makeCollections(credentials: DirectCredentials) = {
        mavenRoot(publishTo) foreach { root =>
          val sardine = SardineFactory.begin(credentials.userName, credentials.passwd)
          artifactPathParts foreach { pathParts =>
            mkcol(sardine, root, pathParts, streams.log)
          }
        }
      }

      val cc = getCredentialsForHost(publishTo, credentialsSet, streams)
      cc match {
        case Some(creds: DirectCredentials) => makeCollections(creds)
        case _ => {
          streams.log.error("WebDav: No credentials available to publish to WebDav")
          throw new MkColException("No credentials available to publish to WebDav")
        }
      }

      streams.log.info("WebDav: Done.")
    }

    case class MkColException(msg: String, inner: Throwable = null) extends RuntimeException(msg, inner)
  }

  object WebDav extends MkCol with WebDavKeys {
    import sbt.Keys._
    val globalSettings = Seq(
      mkcol <<= (organization, name, version, crossScalaVersions, sbtVersion, crossPaths, publishTo, credentials, streams, publishMavenStyle, sbtPlugin) map mkcolAction,
      publish <<= publish.dependsOn(mkcol)
    )

    val scopedSettings = inConfig(webdav)(globalSettings)
  }
}