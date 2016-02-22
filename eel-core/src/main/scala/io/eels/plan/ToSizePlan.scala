package io.eels.plan

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.typesafe.scalalogging.slf4j.StrictLogging
import io.eels.Frame

import scala.concurrent.{ExecutionContext, Future}

object ToSizePlan extends Plan with StrictLogging {

  def apply(frame: Frame)(implicit executor: ExecutionContext): Long = {

    val count = new AtomicLong(0)
    val buffer = frame.buffer
    val latch = new CountDownLatch(slices)
    val running = new AtomicBoolean(true)
    for (k <- 1 to slices) {
      Future {
        try {
          buffer.iterator.takeWhile(_ => running.get).foreach(_ => count.incrementAndGet)
        } catch {
          case e: Throwable =>
            logger.error("Error writing; aborting tasks", e)
            running.set(false)
            throw e
        } finally {
          latch.countDown()
        }
      }
    }

    latch.await(timeout.toNanos, TimeUnit.NANOSECONDS)
    logger.debug("Closing buffer")
    buffer.close()
    logger.debug("Buffer closed")

    count.get()
  }
}
