package trading.it.suite

import cats.effect.*
import cats.syntax.flatMap.*
import weaver.scalacheck.{ CheckConfig, Checkers }
import weaver.IOSuite

abstract class ResourceSuite extends IOSuite with Checkers:
  // For it:tests, one is enough
  override def checkConfig: CheckConfig = CheckConfig.default.copy(minimumSuccessful = 1)

  extension (res: Resource[IO, Res])
    def beforeAll(f: Res => IO[Unit]): Resource[IO, Res] =
      res.evalTap(f)

    def afterAll(f: Res => IO[Unit]): Resource[IO, Res] =
      res.flatTap(x => Resource.make(IO.unit)(_ => f(x)))
