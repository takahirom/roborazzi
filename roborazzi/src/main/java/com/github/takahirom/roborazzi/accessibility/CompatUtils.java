/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.takahirom.roborazzi.accessibility;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.PolyNull;

public class CompatUtils {
  private static final String TAG = CompatUtils.class.getSimpleName();

  /** Whether to log debug output. */
  private static final boolean DEBUG = false;

  @Nullable
  public static Class<?> getClass(String className) {
    if (TextUtils.isEmpty(className)) {
      return null;
    }

    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      if (DEBUG) {
        e.printStackTrace();
      }
    }

    return null;
  }

  @Nullable
  public static Method getMethod(Class<?> targetClass, String name, Class<?>... parameterTypes) {
    if ((targetClass == null) || TextUtils.isEmpty(name)) {
      return null;
    }

    try {
      return targetClass.getDeclaredMethod(name, parameterTypes);
    } catch (Exception e) {
      if (DEBUG) {
        e.printStackTrace();
      }
    }

    return null;
  }

  @Nullable
  public static Field getField(Class<?> targetClass, String name) {
    if ((targetClass == null) || (TextUtils.isEmpty(name))) {
      return null;
    }

    try {
      return targetClass.getDeclaredField(name);
    } catch (Exception e) {
      if (DEBUG) {
        e.printStackTrace();
      }
    }

    return null;
  }

  public static @PolyNull Object invoke(
      Object receiver, @PolyNull Object defaultValue, Method method, Object... args) {
    if (method == null) {
      return defaultValue;
    }

    try {
      return method.invoke(receiver, args);
    } catch (Exception e) {
      Log.e(TAG, "Exception in invoke: " + e.getClass().getSimpleName());

      if (DEBUG) {
        e.printStackTrace();
      }
    }

    return defaultValue;
  }

  public static Object getFieldValue(Object receiver, Object defaultValue, Field field) {
    if (field == null) {
      return defaultValue;
    }

    try {
      return field.get(receiver);
    } catch (Exception e) {
      if (DEBUG) {
        e.printStackTrace();
      }
    }

    return defaultValue;
  }

  private CompatUtils() {
    // This class is non-instantiable.
  }
}