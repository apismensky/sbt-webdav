package webdav

import com.github.sardine.SardineFactory
import org.scalatest.{Matchers, OptionValues, FeatureSpec}
import webdav.WebDavPlugin.MkCol
import sbt.{DirectCredentials, Credentials, JavaNet1Repository, MavenRepository}
import sbt.std.Streams
import com.typesafe.config.ConfigFactory
import java.net.URL


/**
 * A sbt.Logger implementation for testing.
 */
trait TestLogger {

  val testLogger = new sbt.Logger {
    def trace(t: => Throwable) { println(t.getMessage) }

    def success(message: => String) { println(message) }

    import sbt.Level
    def log(level: Level.Value, message: => String) { println("%s: %s" format (level, message))}
  }
}

/**
 * Test mkcol action.
 */
class MkColSpec extends FeatureSpec with Matchers with OptionValues with TestLogger with MkCol with WebDavConfig {

  // Validate real values are set. If not, put a 'test.conf' file in classpath.
  username should not startWith "fill"

  import StringPath._
  val IVY_STYLE = false
  val NO_CROSS_PATHS = false
  val WITH_CROSS_PATHS = true
  val MAVEN_STYLE = true
  val NOT_SBT_PLUGIN = false
  val SBT_PLUGIN = true

  feature("WebDav Make Collection") {
    scenario("Create artifact paths should not include sbt version for normal (non-sbt-plugin) projects") {
      // With cross paths
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, MAVEN_STYLE, NOT_SBT_PLUGIN)
      paths should contain ("/com/organization/name_2.9.2/1.0.1")
      paths should contain ("/com/organization/name_2.10/1.0.1")
    }

    scenario("Create artifact paths should not contain any version number for normal (non-sbt-plugin) projects when crossPaths = false") {
      // Without cross paths
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", NO_CROSS_PATHS, MAVEN_STYLE, NOT_SBT_PLUGIN)
      paths should contain ("/com/organization/name/1.0.1")
      paths should contain ("/com/organization/name/1.0.1")
    }

    scenario("Create artifact paths for ivy sbt-plugin project should include sbt-version") {
      // With cross paths
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, IVY_STYLE, SBT_PLUGIN)
      paths should contain ("/com/organization/name_2.9.2_0.12/1.0.1")
      paths should contain ("/com/organization/name_2.10_0.12/1.0.1")
    }

    scenario("Create artifact paths for ivy project should not include sbt-version") {
      // With cross paths
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, IVY_STYLE, NOT_SBT_PLUGIN)
      paths should contain ("/com/organization/name_2.9.2/1.0.1")
      paths should contain ("/com/organization/name_2.10/1.0.1")
    }

    scenario("Create artifact paths for ivy project should not contain any version number when crossPaths == false") {
      // Without cross paths
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", NO_CROSS_PATHS, IVY_STYLE, NOT_SBT_PLUGIN)
      paths should contain ("/com/organization/name/1.0.1")
      paths should contain ("/com/organization/name/1.0.1")
    }

    scenario("Create artifact paths for ivy sbt-plugin project should not contain any version number when crossPaths == false") {
      // Without cross paths
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", NO_CROSS_PATHS, IVY_STYLE, SBT_PLUGIN)
      paths should contain ("/com/organization/name/1.0.1")
      paths should contain ("/com/organization/name/1.0.1")
    }

    scenario("Create artifact paths for sbt-plugin all crossScalaVersions (crossPaths == true)") {
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, MAVEN_STYLE, SBT_PLUGIN)
      paths should contain ("/com/organization/name_2.9.2_0.12/1.0.1")
      paths should contain ("/com/organization/name_2.10_0.12/1.0.1") // for scala 2.10.* publish path is different!!
    }

    scenario("Create artifact paths for all crossScalaVersions (crossPaths == true)") {
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, MAVEN_STYLE, NOT_SBT_PLUGIN)
      paths should contain ("/com/organization/name_2.9.2/1.0.1")
      paths should contain ("/com/organization/name_2.10/1.0.1") // for scala 2.10.* publish path is different!!
    }

    scenario("Create artifact path for sbt-plugin when crossPaths == false") {
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2"), "0.12.2", NO_CROSS_PATHS, MAVEN_STYLE, SBT_PLUGIN)
      paths should have size (1)
      paths should contain ("/com/organization/name/1.0.1")
    }

    scenario("Create artifact path when crossPaths == false") {
      val paths = createPaths("com.organization", "name", "1.0.1", Seq("2.9.2"), "0.12.2", NO_CROSS_PATHS, MAVEN_STYLE, NOT_SBT_PLUGIN)
      paths should have size (1)
      paths should contain ("/com/organization/name/1.0.1")
    }

    scenario("pathCollections should return all collections for path") {
      pathCollections("/com/organization/name_2.10_0.12/1.0.1") should equal(List("com", "com/organization", "com/organization/name_2.10_0.12", "com/organization/name_2.10_0.12/1.0.1"))
      pathCollections("/com/organization/name_2.10_0.12/1.0.1") should equal(List("com", "com/organization", "com/organization/name_2.10_0.12", "com/organization/name_2.10_0.12/1.0.1"))
    }

    scenario("Update paths with 'publishTo' location") {
      val paths = Seq("/com/one", "/com/two")
      val resolver = Some(MavenRepository("releases", "http://some.url/"))

      val publishUrls = publishToUrls(paths, resolver)
      publishUrls should not be None
      publishUrls.get should contain ("http://some.url/com/one")
      publishUrls.get should contain ("http://some.url/com/two")
    }

    scenario("Exists should return true for existing urls") {

      val sardine = SardineFactory.begin(username, password)
      exists(sardine, webdavUrl / "de") shouldBe true
      exists(sardine, webdavUrl / "non-existing-folder") shouldBe false
    }

    scenario("Exists should return false for non existing urls (and not throw Exception)") {

      val sardine = SardineFactory.begin()
      exists(sardine, "https://fake.url/not-exist") shouldBe false
    }

    scenario("Maven root should return root from MavenRepository") {
      val resolver = Some(MavenRepository("releases", "http://some.url/"))
      mavenRoot(resolver) should be(Some("http://some.url/"))
    }

    scenario("Maven root should return None if resolver not Maven Repo") {
      mavenRoot(Some(JavaNet1Repository)) should be(None)
    }

    scenario("Make collection should throw exception when urlRoot does not exist") {
      val sardine = SardineFactory.begin(username, password)

      intercept[MkColException] {
        mkcol(sardine, "https://fake.url/not-exist", List(), testLogger)
      }
    }

    def getFileResource(resourceName: String) = {
      import java.io.File
      val resource: URL = getClass.getResource(resourceName)
      println("Found resource: "+resource)
      new File(resource.toURI)
    }

    scenario("getCredentialsForHost should return credentials for host") {
      import java.io.File
      val credentials = Seq(Credentials("realm", "host.name", "user", "pwd"),
        Credentials(getFileResource("/test-credentials.properties")),
        Credentials("realm2", "host2.name", "user", "pwd"))

      val resolver = Some(MavenRepository("releases", "http://host2.name/"))
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")

      val foundCredentials = getCredentialsForHost(resolver, credentials, streams)
      foundCredentials should not be(None)
      foundCredentials map {
        case c:DirectCredentials => c.host should be("host2.name")
        case _ => fail("Wrong credentials found")
      }
    }

    scenario("getCredentialsForHost should also use File based credentials") {
      import java.io.File
      val credentials = Seq(Credentials("realm", "host.name", "user", "pwd"),
        Credentials(getFileResource("/test-credentials.properties")), // this file contains the credentials for this testcase
        Credentials("realm2", "host2.name", "user", "pwd"))

      val resolver = Some(MavenRepository("releases", "http://some-repo.for.test/"))
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")

      val foundCredentials = getCredentialsForHost(resolver, credentials, streams)
      foundCredentials should not be(None)
      foundCredentials map {
        case c:DirectCredentials => c.host should be("some-repo.for.test")
        case _ => fail("Wrong credentials found")
      }
    }

    scenario("Make collection (single level) should create only one directory") {
      val sardine = SardineFactory.begin(username, password)

      mkcol(sardine, webdavUrl, List("testing"), testLogger)
      exists(sardine, webdavUrl / "testing") shouldBe true

      sardine.delete(webdavUrl / "testing/")
    }

    scenario("Make collection (multi level) should create only all directories") {
      val sardine = SardineFactory.begin(username, password)

      mkcol(sardine, webdavUrl, List("testing","testing/123","testing/123/456"), testLogger)
      exists(sardine, webdavUrl / "testing") shouldBe true
      exists(sardine, webdavUrl / "testing/123") shouldBe true
      exists(sardine, webdavUrl / "testing/123/456") shouldBe true

      sardine.delete(webdavUrl / "testing/")
    }

    scenario("mkcolAction should create folders for sbt-plugin with all crossScalaVersions") {
      import java.io.File
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")
      val credentials = Seq(Credentials("realm", host, username, password))
      mkcolAction("test.org.case", "testcase", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, Some(MavenRepository("releases", webdavUrl)), credentials, streams, MAVEN_STYLE, SBT_PLUGIN)

      val sardine = SardineFactory.begin()
      exists(sardine, webdavUrl / "test/org/case/testcase_2.9.2_0.12/1.0.1") shouldBe true
      exists(sardine, webdavUrl / "test/org/case/testcase_2.10_0.12/1.0.1") shouldBe true

      val sardine2 = SardineFactory.begin(username, password)
      sardine2.delete(webdavUrl / "test/")
    }

    scenario("mkcolAction should create folderswith all crossScalaVersions") {
      import java.io.File
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")
      val credentials = Seq(Credentials("realm", host, username, password))
      mkcolAction("test.org.case", "testcase", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, Some(MavenRepository("releases", webdavUrl)), credentials, streams, MAVEN_STYLE, NOT_SBT_PLUGIN)

      val sardine = SardineFactory.begin()
      exists(sardine, webdavUrl / "test/org/case/testcase_2.9.2/1.0.1") shouldBe true
      exists(sardine, webdavUrl / "test/org/case/testcase_2.10/1.0.1") shouldBe true

      val sardine2 = SardineFactory.begin(username, password)
      sardine2.delete(webdavUrl / "test/")
    }

    scenario("mkcolAction should create folder for java artifact of sbt-plugin (crossPaths == false)") {
      import java.io.File
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")
      val credentials = Seq(Credentials("realm", host, username, password))
      mkcolAction("test.org.case", "testcase", "1.0.1", Seq("2.9.2"), "0.12.2", NO_CROSS_PATHS, Some(MavenRepository("releases", webdavUrl)), credentials, streams, MAVEN_STYLE, SBT_PLUGIN)

      val sardine = SardineFactory.begin()
      exists(sardine, webdavUrl / "test/org/case/testcase/1.0.1") shouldBe true

      val sardine2 = SardineFactory.begin(username, password)
      sardine2.delete(webdavUrl / "test/")
    }

    scenario("mkcolAction should create folder for java artifact (crossPaths == false)") {
      import java.io.File
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")
      val credentials = Seq(Credentials("realm", host, username, password))
      mkcolAction("test.org.case", "testcase", "1.0.1", Seq("2.9.2"), "0.12.2", NO_CROSS_PATHS, Some(MavenRepository("releases", webdavUrl)), credentials, streams, MAVEN_STYLE, NOT_SBT_PLUGIN)

      val sardine = SardineFactory.begin()
      exists(sardine, webdavUrl / "test/org/case/testcase/1.0.1") shouldBe true

      val sardine2 = SardineFactory.begin(username, password)
      sardine2.delete(webdavUrl / "test/")
    }

    scenario("mkcolAction should create correct folder structure for Ivy sbt-plugin project (publishMavenStyle = false)") {
      import java.io.File
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")
      val credentials = Seq(Credentials("realm", host, username, password))
      mkcolAction("test.org.case", "testcase", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, Some(MavenRepository("releases", webdavUrl)), credentials, streams, IVY_STYLE, SBT_PLUGIN)

      val sardine = SardineFactory.begin()
      exists(sardine, webdavUrl / "test/org/case/testcase_2.9.2_0.12/1.0.1") shouldBe true
      exists(sardine, webdavUrl / "test/org/case/testcase_2.10_0.12/1.0.1") shouldBe true

      val sardine2 = SardineFactory.begin(username, password)
      sardine2.delete(webdavUrl / "test/")
    }

    scenario("mkcolAction should create correct folder structure for Ivy project (publishMavenStyle = false)") {
      import java.io.File
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")
      val credentials = Seq(Credentials("realm", host, username, password))
      mkcolAction("test.org.case", "testcase", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, Some(MavenRepository("releases", webdavUrl)), credentials, streams, IVY_STYLE, NOT_SBT_PLUGIN)

      val sardine = SardineFactory.begin()
      exists(sardine, webdavUrl / "test/org/case/testcase_2.9.2/1.0.1") shouldBe true
      exists(sardine, webdavUrl / "test/org/case/testcase_2.10/1.0.1") shouldBe true

      val sardine2 = SardineFactory.begin(username, password)
      sardine2.delete(webdavUrl / "test/")
    }

    scenario("mkcolAction should throw exception when no credentials available") {
      import java.io.File
      val streams = Streams[String]((_) => new File("target"), (_) => "Test", (_,_) => testLogger)("Test")
      val credentials = Seq(Credentials("realm", "dummy.url", "user", "pwd"))

      intercept[MkColException] {
        mkcolAction("test.org.case", "testcase", "1.0.1", Seq("2.9.2", "2.10.0"), "0.12.2", WITH_CROSS_PATHS, Some(MavenRepository("releases", webdavUrl)), credentials, streams, MAVEN_STYLE, NOT_SBT_PLUGIN)
      }
    }
  }
}

/**
 * Load WebDav config from file
 */
trait WebDavConfig {

  private val dummyConfig = ConfigFactory.load("test-dummy")
  private val config = ConfigFactory.parseFile(new java.io.File("/private/diversit/webdav4sbt/test.conf")).withFallback(dummyConfig).getConfig("webdav")
  val username = config.getString("username")
  val password = config.getString("password")
  val webdavUrl = config.getString("url")
  val host = config.getString("host")
}