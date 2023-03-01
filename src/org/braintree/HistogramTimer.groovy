package org.braintree
import java.time.Duration
import java.util.concurrent.TimeUnit

// TODO: can this class be refactored to leverage Groovy closures??
class HistogramTimer {
  Long startTime
  Long endTime

  def start() {
    startTime = System.currentTimeMillis()
    return startTime
  }

  def end() {
    endTime = System.currentTimeMillis()
    return endTime
  }

  def duration() {
    return TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
  }

  def durationMillis(){
    return endTime - startTime
  }
}
