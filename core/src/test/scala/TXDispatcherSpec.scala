package com.reebo.ethsync.core.test

import com.reebo.ethsync.core.Protocol.{FullTX, ShallowTX, TXData}
import com.reebo.ethsync.core._
import com.reebo.ethsync.core.persistence.InMemoryTXPersistence
import com.typesafe.scalalogging.Logger
import io.circe.syntax._
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.execution.atomic.Atomic
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * The TXDispatcher needs to fulfill the following requirements
  */
class TXDispatcherSpec extends FlatSpec with MockFactory with Matchers {

  case class TestParameters(dispatcher: Try[TXDispatcher], sink: RememberSink, persistence: TXPersistence)

  behavior of "TXDispatcher"

  it should "read all the transactions from the persistence when initialized" in successInit { p =>
    val d = p.dispatcher
    d.isSuccess.shouldEqual(true)
    d.get.txs.size.shouldEqual(1)
  }

  it should "remove tx from persistence when found the next time" in failLiftRetry { p =>
    p.dispatcher.isSuccess.shouldEqual(true)
    p.dispatcher.get.txs.size.shouldEqual(0)
    p.persistence.readAll.runSyncUnsafe(1.second).size.shouldEqual(0)
  }

  it should "throw exception when it cannot initialize" in failInit {
    _.dispatcher.isFailure.shouldEqual(true)
  }

  it should "send to sink when succeed" in successLift {
    _.sink.txs.get.size.shouldEqual(10)
  }

  it should "nothing in retry queue when succeed" in successLift {
    _.dispatcher.get.txs.size.shouldEqual(0)
  }

  it should "put to retry queue when fail" in failLift {
    _.dispatcher.get.txs.size.shouldEqual(10)
  }

  it should "nothing sent to sink when fail" in failLift {
    _.sink.txs.get.size.shouldEqual(0)
  }

  it should "sent shallow tx to persistence when fail" in failLift {
    _.persistence.readAll.runSyncUnsafe(1.second).size.shouldEqual(10)
  }

  it should "put to retry queue and persistence when writing to sink fails" in failSink {
    _.dispatcher.get.txs.size.shouldEqual(10)
  }

  it should "put to persistence when writing to sink fails" in failSink {
    _.persistence.readAll.runSyncUnsafe(1.second).size.shouldEqual(10)
  }

  it should "propagate error when persistence fails ultimately" in failPersistence {
    _.dispatcher.isFailure.shouldEqual(true)
  }

  val bypassRetry = new RetryFilter {
    // bypassing retry
    override def retry[A](logger: Logger, source: Task[A]): Task[A] = source
  }

  val failingPersistence: TXPersistence = new TXPersistence {
    private val error = Task.raiseError(new Exception("Something wrong"))

    override def add(txs: Seq[ShallowTX]): Task[Unit] = error

    override def remove(txs: Seq[ShallowTX]): Task[Unit] = error

    override def readAll: Task[Seq[ShallowTX]] = Task.now(Seq()) // make sure it can initialize
  }

  val initFailPersistence: TXPersistence = new TXPersistence {
    private val error = Task.raiseError(new Exception("Something wrong"))

    override def add(txs: Seq[ShallowTX]): Task[Unit] = error

    override def remove(txs: Seq[ShallowTX]): Task[Unit] = error

    override def readAll: Task[Seq[ShallowTX]] = error
  }

  private def genLifter(succeed: Boolean = true) = {
    new TXLifter {
      val success = succeed

      override def lift(tx: ShallowTX) =
        if (!success) fail(tx) else success(tx)

      def fail(tx: ShallowTX) =
        Task.now(Failure(new Exception("Could not lift")))

      def success(tx: ShallowTX) = Task(Success(FullTX(tx.data, "{}".asJson)))
    }
  }

  def scenarioLift(lifter: TXLifter): TestParameters = {
    val sink = RememberSink()
    val persistence = InMemoryTXPersistence()
    scenario(lifter, sink, persistence)
  }

  def scenarioSink(sink: RememberSink): TestParameters = {
    val lifter = genLifter()
    val persistence = InMemoryTXPersistence()
    scenario(lifter, sink, persistence)
  }

  def scenarioPersistence(persistence: TXPersistence): TestParameters = {
    val lifter = genLifter()
    val sink = RememberSink()
    scenario(lifter, sink, persistence, bypassRetry)
  }

  def scenario(lifter: TXLifter,
               sink: RememberSink,
               persistence: TXPersistence,
               retry: RetryFilter = BackoffRetry(1, 1.seconds)): TestParameters = {

    val txs = Range(0, 10, 1).map { id => this.genShallowTX(id.toString) }

    val initialized =
      TXDispatcher("test", lifter, sink, persistence, retry)
        .init
        .materialize

    val dispatcher = initialized.flatMap {
      case Success(d) => d.schedule(txs).dispatch.materialize
      case Failure(e) => Task.raiseError(new Exception("Could not initialize"))
    }.runSyncUnsafe(1.seconds)

    TestParameters(dispatcher, sink, persistence)
  }

  def failPersistence(testCode: TestParameters => Any): Unit = {
    testCode(scenarioPersistence(failingPersistence))
  }

  def failSink(testCode: TestParameters => Any): Unit = {
    val sink = RememberSink(fail = true)
    testCode(scenarioSink(sink))
  }

  def failInit(testCode: TestParameters => Any): Unit = {
    val lifter = mock[TXLifter]
    val sink = mock[RememberSink]

    val dispatcher = TXDispatcher("test", lifter, sink, initFailPersistence, bypassRetry)
      .init
      .materialize
      .runSyncUnsafe(1.seconds)

    testCode(TestParameters(dispatcher, sink, initFailPersistence))
  }

  def successInit(testCode: TestParameters => Any): Unit = {
    val lifter = mock[TXLifter]
    val sink = mock[RememberSink]
    val persistence: TXPersistence = new TXPersistence {
      override def add(txs: Seq[ShallowTX]): Task[Unit] = ???

      override def remove(txs: Seq[ShallowTX]): Task[Unit] = ???

      override def readAll: Task[Seq[ShallowTX]] = Task.now(Seq(genShallowTX("1")))
    }

    val dispatcher = TXDispatcher("test", lifter, sink, persistence, bypassRetry)
      .init
      .materialize
      .runSyncUnsafe(1.seconds)

    testCode(TestParameters(dispatcher, sink, persistence))
  }

  def successLift(testCode: TestParameters => Any): Unit = {
    val successLifter = genLifter()
    testCode(scenarioLift(successLifter))
  }

  def failLiftRetry(testCode: TestParameters => Any): Unit = {
    val lifter = genLifter(false)
    val sink = RememberSink()
    val persistence = InMemoryTXPersistence()
    val retry: RetryFilter = BackoffRetry(1, 1.seconds)

    val txs = Range(0, 10, 1).map { id => this.genShallowTX(id.toString) }

    val task: Task[Try[TXDispatcher]] = for {
      initialized <- TXDispatcher("test", lifter, sink, persistence, retry).init.materialize
      newDis <- initialized match {
        case Success(d) => d.schedule(txs).dispatch.materialize
        case Failure(e) => Task.raiseError(new Exception("Could not initialize"))
      }
      newDis2 <- newDis match {
        case Success(d) => d.copy(lifter = genLifter()).schedule(txs).dispatch.materialize
        case Failure(e) => Task.raiseError(new Exception("Could not dispatch"))
      }
    } yield newDis2

    val dispatcher = task.runSyncUnsafe(1.second)
    testCode(TestParameters(dispatcher, sink, persistence))
  }

  def failLift(testCode: TestParameters => Any): Unit = {
    val faultyLifter = new TXLifter {
      override def lift(tx: ShallowTX): Task[Try[Protocol.FullTX]] =
        Task.now(Failure(new Exception("Could not lift")))
    }
    testCode(scenarioLift(faultyLifter))
  }

  case class RememberSink(fail: Boolean = false) extends TXSink {
    var txs = Atomic(Seq[Protocol.FullTX]())

    def sink(tx: FullTX): Task[Unit] = {
      if (fail) Task.raiseError(new Exception("failing"))
      else Task {
        this.txs.getAndTransform { old =>
          old ++ Seq(tx)
        }
      }
    }
  }

  def genShallowTX(hash: String): ShallowTX = {
    val data = TXData(
      hash,
      "{}".asJson
    )
    ShallowTX(data)
  }

}
