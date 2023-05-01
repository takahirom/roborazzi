/*
 * FROM TalkBack: https://github.com/google/talkback/blob/bdead86e21beae508fa1fd7a24a06608485e1c29/utils/src/main/java/com/google/android/accessibility/utils/TreeDebug.java
 *
 * Copyright (C) 2014 The Android Open Source Project
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

import android.graphics.Rect;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashSet;
import java.util.List;

/** Util class to help debug Node trees. */
public class TreeDebug {

  public static final String TAG = "TreeDebug";


  private static void appendSimpleName(StringBuilder sb, CharSequence fullName) {
    int dotIndex = TextUtils.lastIndexOf(fullName, '.');
    if (dotIndex < 0) {
      dotIndex = 0;
    }

    sb.append(fullName, dotIndex, fullName.length());
  }

  // FocusActionRecord.getUniqueIdViaReflection() is used temporarily. TODO: call
  // AccessibilityNodeInfoCompat.getUniqueId() instead.
  private static String getUniqueIdViaReflection(AccessibilityNodeInfoCompat nodeInfo) {
    if (nodeInfo == null) {
      return null;
    }
    return (String)
        CompatUtils.invoke(
            nodeInfo.unwrap(),
            /* defaultValue= */ null,
            CompatUtils.getMethod(AccessibilityNodeInfo.class, "getUniqueId"));
  }

  /** Gets a description of the properties of a node. */
  public static CharSequence nodeDebugDescription(AccessibilityNodeInfoCompat node) {
    StringBuilder sb = new StringBuilder();
    sb.append(node.getWindowId());

    if (node.getClassName() != null) {
      appendSimpleName(sb, node.getClassName());
    } else {
      sb.append("??");
    }

    String uniqueId = getUniqueIdViaReflection(node);
    if (uniqueId != null) {
      sb.append(":U(");
      sb.append(uniqueId);
      sb.append(")");
    }

    if (!node.isVisibleToUser()) {
      sb.append(":invisible");
    }

    Rect rect = new Rect();
    node.getBoundsInScreen(rect);
    sb.append(":");
    sb.append("(")
        .append(rect.left)
        .append(", ")
        .append(rect.top)
        .append(" - ")
        .append(rect.right)
        .append(", ")
        .append(rect.bottom)
        .append(")");

    if (!TextUtils.isEmpty(node.getPaneTitle())) {
      sb.append(":PANE{");
      sb.append(node.getPaneTitle());
      sb.append("}");
    }

    @Nullable CharSequence nodeText = getText(node);
    if (nodeText != null) {
      sb.append(":TEXT{");
      sb.append(nodeText.toString().trim());
      sb.append("}");
    }

    if (node.getContentDescription() != null) {
      sb.append(":CONTENT{");
      sb.append(node.getContentDescription().toString().trim());
      sb.append("}");
    }

    if (getState(node) != null) {
      sb.append(":STATE{");
      sb.append(getState(node).toString().trim());
      sb.append("}");
    }
    // Views that inherit Checkable can have its own state description and the log already covered
    // by above SD, but for some views that are not Checkable but have checked status, like
    // overriding by AccessibilityDelegate, we should also log it.
    if (node.isCheckable()) {
      sb.append(":");
      if (node.isChecked()) {
        sb.append("checked");
      } else {
        sb.append("not checked");
      }
    }

    int actions = node.getActions();
    if (actions != 0) {
      sb.append("(action:");
      if ((actions & AccessibilityNodeInfoCompat.ACTION_FOCUS) != 0) {
        sb.append("FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS) != 0) {
        sb.append("A11Y_FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS) != 0) {
        sb.append("CLEAR_A11Y_FOCUS/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) != 0) {
        sb.append("SCROLL_BACKWARD/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) != 0) {
        sb.append("SCROLL_FORWARD/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_CLICK) != 0) {
        sb.append("CLICK/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_LONG_CLICK) != 0) {
        sb.append("LONG_CLICK/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_EXPAND) != 0) {
        sb.append("EXPAND/");
      }
      if ((actions & AccessibilityNodeInfoCompat.ACTION_COLLAPSE) != 0) {
        sb.append("COLLAPSE/");
      }
      sb.setLength(sb.length() - 1);
      sb.append(")");
    }

    if (node.isFocusable()) {
      sb.append(":focusable");
    }
    if (node.isScreenReaderFocusable()) {
      sb.append(":screenReaderfocusable");
    }

    if (node.isFocused()) {
      sb.append(":focused");
    }

    if (node.isSelected()) {
      sb.append(":selected");
    }

    if (node.isScrollable()) {
      sb.append(":scrollable");
    }

    if (node.isClickable()) {
      sb.append(":clickable");
    }

    if (node.isLongClickable()) {
      sb.append(":longClickable");
    }

    if (node.isAccessibilityFocused()) {
      sb.append(":accessibilityFocused");
    }
    if (supportsTextLocation(node)) {
      sb.append(":supportsTextLocation");
    }
    if (!node.isEnabled()) {
      sb.append(":disabled");
    }

    if (node.getCollectionInfo() != null) {
      sb.append(":collection");
      sb.append("#R");
      sb.append(node.getCollectionInfo().getRowCount());
      sb.append("C");
      sb.append(node.getCollectionInfo().getColumnCount());
    }

    // Roborazzi: it has a lot of dependencies for now, it's not supported
//    if (AccessibilityNodeInfoUtils.isHeading(node)) {
//      sb.append(":heading");
//    } else if (node.getCollectionItemInfo() != null) {
//      sb.append(":item");
//    }
    if (node.getCollectionItemInfo() != null) {
      sb.append("#r");
      sb.append(node.getCollectionItemInfo().getRowIndex());
      sb.append("c");
      sb.append(node.getCollectionItemInfo().getColumnIndex());
    }

    return sb.toString();
  }

  public static @Nullable CharSequence getText(@Nullable AccessibilityNodeInfoCompat node) {
    return (node == null) ? null : node.getText();
  }

  public static @Nullable CharSequence getState(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    final CharSequence state = node.getStateDescription();
    if (!TextUtils.isEmpty(state) && (TextUtils.getTrimmedLength(state) > 0)) {
      return state;
    }

    return null;
  }

  public static boolean supportsTextLocation(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    AccessibilityNodeInfo info = node.unwrap();
    if (info == null) {
      return false;
    }
    List<String> extraData = null;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      extraData = info.getAvailableExtraData();
    }
    return extraData != null
      && extraData.contains(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);
  }
}
