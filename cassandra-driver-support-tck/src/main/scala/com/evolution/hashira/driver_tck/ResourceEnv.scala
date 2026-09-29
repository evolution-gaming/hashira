package com.evolution.hashira.driver_tck

import com.typesafe.scalalogging.Logger

import scala.util.Using.Releasable
import scala.util.control.NonFatal

/**
 * Holds a value built from resources that have to be released together, e.g. shared test
 * suite fixtures: initialized once with [[initialize]] (e.g. in `beforeAll`), accessed
 * with [[requireInitialized]] and released with [[close]] (e.g. in `afterAll`).
 *
 * Not thread-safe: meant to be used from a single thread in tests.
 *
 * Example:
 * {{{
 * class MySpec extends AnyFreeSpec with BeforeAndAfterAll {
 *   private val env = ResourceEnv[Fixtures]()
 *
 *   override protected def beforeAll(): Unit = {
 *     super.beforeAll()
 *     env.initialize { arena =>
 *       val db = arena(new DbContainer())
 *       db.start()
 *       val client = arena(DbClient.connect(db.address))
 *       Fixtures(db, client)
 *     }
 *   }
 *
 *   override protected def afterAll(): Unit = {
 *     env.close() // releases client, then db
 *     super.afterAll()
 *   }
 *
 *   "selects users" in {
 *     env.requireInitialized().client.execute("SELECT * FROM users")
 *   }
 * }
 * }}}
 *
 * @tparam T
 *   type of the value built from the resources
 */
private[driver_tck] final class ResourceEnv[T] extends AutoCloseable {
  import ResourceEnv.log

  private[this] var valueOpt: Option[T] = None
  private[this] var closed = false
  private[this] var releaseCallbacks: List[() => Unit] = Nil

  /**
   * Returns the value built by [[initialize]].
   *
   * @throws java.lang.IllegalStateException
   *   if the env has not been initialized yet or has already been closed
   */
  def requireInitialized(): T = {
    requireNonClosed()
    valueOpt.getOrElse(
      throw new IllegalStateException("resource env has not been initialized yet"),
    )
  }

  /**
   * Builds the env value with `body`, which registers the resources to release on
   * [[close]] in the arena.
   *
   * If `body` throws a non-fatal exception, the env is closed, releasing the resources
   * registered so far, and the exception is rethrown.
   *
   * @throws java.lang.IllegalStateException
   *   if the env has already been initialized or closed
   */
  def initialize(body: ResourceEnv.InitArena => T): Unit = {
    requireNonInitialized()

    val arena = new ResourceEnv.InitArena {
      override def apply[R: Releasable](resource: R): resource.type = {
        this.acquire(resource)
        resource
      }

      override def acquire[R: Releasable](resource: R): Unit = {
        requireNonInitialized() // guarding in case user captured the arena reference
        registerRelease(resource)
      }
    }
    try {
      this.valueOpt = Some(body(arena))
    } catch {
      case NonFatal(t) =>
        close()
        throw t
    }
  }

  private def requireNonInitialized(): Unit = {
    requireNonClosed()
    if (valueOpt.isDefined) {
      throw new IllegalStateException("resource env has already been initialized")
    }
  }

  private def registerRelease[R](resource: R)(implicit releasable: Releasable[R]): Unit = {
    this.releaseCallbacks = (() => releasable.release(resource)) :: this.releaseCallbacks
  }

  private def requireNonClosed(): Unit = {
    if (this.closed) throw new IllegalStateException("resource env has already been closed")
  }

  /**
   * Releases the registered resources in the reverse order of their registration and
   * closes the env. Calling it on an already closed env does nothing.
   *
   * Non-fatal release errors are logged and don't stop releasing the remaining resources.
   * A fatal error is propagated, leaving the env open with the not yet released
   * resources, so a repeated call continues with them.
   */
  override def close(): Unit = {
    if (!this.closed) {
      while (this.releaseCallbacks != Nil) {
        val releaseCb = this.releaseCallbacks.head
        // dequeue before releasing, so a fatal error can't cause a double release on a retry
        this.releaseCallbacks = this.releaseCallbacks.tail
        try {
          releaseCb()
        } catch {
          case NonFatal(t) =>
            log.warn(s"Error on resource release: ${ t.getMessage }")
        }
      }
      this.closed = true
    }
  }
}
private[driver_tck] object ResourceEnv {
  private val log: Logger = Logger[ResourceEnv[?]]

  /**
   * Creates a new uninitialized [[ResourceEnv]].
   */
  def apply[T](): ResourceEnv[T] = new ResourceEnv

  /**
   * Registers resources for release on [[ResourceEnv#close]], available only while
   * [[ResourceEnv#initialize]] runs.
   */
  trait InitArena {

    /**
     * Registers the resource for release and returns it, so a resource can be registered
     * inline where it is created.
     *
     * @throws java.lang.IllegalStateException
     *   if called after [[ResourceEnv#initialize]] has finished
     */
    def apply[R: Releasable](resource: R): resource.type

    /**
     * Registers the resource for release.
     *
     * @throws java.lang.IllegalStateException
     *   if called after [[ResourceEnv#initialize]] has finished
     */
    def acquire[R: Releasable](resource: R): Unit
  }
}
