package com.evolution.hashira

import org.scalactic.source
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.reflect.ClassTag

/**
 * Base class for a Hashira unit spec - free-spec style with scalacheck
 */
abstract class BaseUnitSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(
      // increase from the default 10 to have a better coverage for cases with high cardinality
      minSuccessful = 500,
    )

  /**
   * Like scalatest `intercept`, but allows non-matching exceptions to escape the block,
   * so their stacktrace is visible in the test failure.
   */
  protected def interceptOnly[T <: Throwable](
    body: => Any,
  )(implicit
    classTag: ClassTag[T],
    pos: source.Position,
  ): T = {
    val outcome: Either[T, Any] = try {
      Right(body)
    } catch {
      case expectedException: T =>
        Left(expectedException)
    }

    outcome.fold(
      identity,
      result => fail(s"expected exception of type ${ classTag.runtimeClass.getName }, got success: $result"),
    )
  }
}
