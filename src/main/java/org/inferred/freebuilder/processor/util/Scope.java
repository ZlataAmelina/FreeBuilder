/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor.util;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public abstract class Scope {

  public enum Level {
    FILE, METHOD;
  }

  @SuppressWarnings("unused")
  public interface Element<T> {
    Level level();
  }

  private final Map<Element<?>, Object> elements = new LinkedHashMap<>();
  private final Scope parent;
  private final Level level;

  private Scope(Scope parent, Level level) {
    this.parent = parent;
    this.level = level;
  }

  public boolean contains(Element<?> element) {
    return get(element) != null;
  }

  public <T> T get(Element<T> element) {
    @SuppressWarnings("unchecked")
    T value = (T) elements.get(element);
    if (value != null) {
      return value;
    } else if (parent != null) {
      return parent.get(element);
    } else {
      return null;
    }
  }

  public <T> T computeIfAbsent(Element<T> element, Supplier<T> supplier) {
    T value = get(element);
    if (value != null) {
      return value;
    } else if (level == element.level()) {
      value = supplier.get();
      elements.put(element, value);
      return value;
    } else if (parent != null) {
      return parent.computeIfAbsent(element, supplier);
    } else {
      throw new IllegalStateException(
          "Not in " + element.level().toString().toLowerCase() + " scope");
    }
  }

  public <T> Set<T> keysOfType(Class<T> elementType) {
    ImmutableSet.Builder<T> keys = ImmutableSet.builder();
    if (parent != null) {
      keys.addAll(parent.keysOfType(elementType));
    }
    keys.addAll(FluentIterable.from(elements.keySet()).filter(elementType).toSet());
    return keys.build();
  }

  public <T> T putIfAbsent(Element<T> element, T value) {
    requireNonNull(element);
    requireNonNull(value);
    if (level == element.level()) {
      @SuppressWarnings("unchecked")
      T existingValue = (T) elements.get(element);
      if (existingValue == null) {
        elements.put(element, value);
      }
      return existingValue;
    } else if (parent != null) {
      return parent.putIfAbsent(element, value);
    }
    return null;
  }

  static class FileScope extends Scope {
    FileScope() {
      super(null, Level.FILE);
    }
  }

  static class MethodScope extends Scope {
    MethodScope(Scope parent) {
      super(parent, Level.METHOD);
    }
  }
}
