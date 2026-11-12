package com.example.tracker.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward. */
public class MutableClock extends Clock {

  private Instant now = Instant.now();

  public synchronized void advance(Duration d) {
    now = now.plus(d);
  }

  public void reset() {
    now = Instant.now();
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  /** Ticks 1 ms per read, like a real clock, so events keep a stable order. */
  @Override
  public synchronized Instant instant() {
    now = now.plusMillis(1);
    return now;
  }
}
