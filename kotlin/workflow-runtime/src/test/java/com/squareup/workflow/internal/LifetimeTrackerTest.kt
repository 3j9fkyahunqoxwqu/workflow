/*
 * Copyright 2019 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.internal

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifetimeTrackerTest {

  private inner class TestDisposable(val name: String) {
    var disposed = false

    init {
      disposableCount++
    }

    fun dispose() {
      if (!disposed) {
        disposableCount--
        disposed = true
      }
    }
  }

  private var disposableCount = 0
  private val tracker = LifetimeTracker(
      getKey = { it },
      start = ::TestDisposable,
      dispose = { factory, disposable ->
        assertEquals(factory, disposable.name)
        disposable.dispose()
      }
  )

  @BeforeTest fun setUp() {
    assertEquals(0, disposableCount)
  }

  @AfterTest fun tearDown() {
    tracker.track(emptyList())
    assertEquals(0, disposableCount)
  }

  @Test fun `ensure starts new factory`() {
    tracker.ensure("foo")
    assertEquals(1, disposableCount)
    assertEquals("foo", tracker.lifetimes.getValue("foo").name)

    tracker.ensure("bar")
    assertEquals(2, disposableCount)
    assertEquals("foo", tracker.lifetimes.getValue("foo").name)
    assertEquals("bar", tracker.lifetimes.getValue("bar").name)
  }

  @Test fun `track starts new factories`() {
    tracker.track(listOf("foo"))
    assertEquals(1, disposableCount)
    assertEquals("foo", tracker.lifetimes.getValue("foo").name)

    tracker.track(listOf("foo", "bar", "baz"))
    assertEquals(3, disposableCount)
    assertEquals("foo", tracker.lifetimes.getValue("foo").name)
    assertEquals("bar", tracker.lifetimes.getValue("bar").name)
    assertEquals("baz", tracker.lifetimes.getValue("baz").name)
  }

  @Test fun `track disposes missing factories`() {
    tracker.track(listOf("foo"))
    assertEquals(1, disposableCount)
    assertTrue("foo" in tracker.lifetimes)

    tracker.track(listOf("bar"))
    assertEquals(1, disposableCount)
    assertFalse("foo" in tracker.lifetimes)
  }

  @Test fun `track throws on two duplicate keys`() {
    val error = assertFailsWith<IllegalArgumentException> {
      tracker.track(listOf("dup", "dup"))
    }
    assertTrue("Expected all keys to be unique. Duplicates:" in error.message!!)
    assertTrue("2×dup" in error.message!!)
  }

  @Test fun `track throws on three duplicate keys`() {
    val error = assertFailsWith<IllegalArgumentException> {
      tracker.track(listOf("dup", "dup", "dup"))
    }
    assertTrue("3×dup" in error.message!!)
  }

  @Test fun `track throws on multiple sets of duplicate keys`() {
    val error = assertFailsWith<IllegalArgumentException> {
      tracker.track(listOf("dup1", "dup2", "dup1", "dup2"))
    }
    assertTrue("2×dup1" in error.message!!)
    assertTrue("2×dup2" in error.message!!)
  }
}
