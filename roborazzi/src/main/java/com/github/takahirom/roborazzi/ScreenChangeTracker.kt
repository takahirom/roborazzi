package com.github.takahirom.roborazzi

import android.view.ViewTreeObserver

internal sealed interface ScreenChangeTracker {
  fun interface OnScreenChangeListener {
    fun onScreenChange()
  }

  abstract fun addListener(onScreenChangeListener: OnScreenChangeListener)

  abstract fun removeListener(onScreenChangeListener: OnScreenChangeListener)

  private class GlobalLayoutChange(private val viewTreeObserver: ViewTreeObserver) :
    ScreenChangeTracker {

    private val savedObserverMap = hashMapOf<OnScreenChangeListener, ViewTreeObserver.OnGlobalLayoutListener>()
    override fun addListener(onScreenChangeListener: OnScreenChangeListener) {
      val rawListener =
        ViewTreeObserver.OnGlobalLayoutListener { onScreenChangeListener.onScreenChange() }
      savedObserverMap[onScreenChangeListener] = rawListener
      viewTreeObserver.addOnGlobalLayoutListener(rawListener)
    }

    override fun removeListener(onScreenChangeListener: OnScreenChangeListener) {
      savedObserverMap[onScreenChangeListener]?.let {
        viewTreeObserver.removeOnGlobalLayoutListener(it)
      }
    }
  }


  private class DrawChange(private val viewTreeObserver: ViewTreeObserver) :
    ScreenChangeTracker {

    private val savedObserverMap =
      hashMapOf<OnScreenChangeListener, ViewTreeObserver.OnDrawListener>()

    override fun addListener(onScreenChangeListener: OnScreenChangeListener) {
      val rawListener =
        ViewTreeObserver.OnDrawListener {
          onScreenChangeListener.onScreenChange()
        }
      savedObserverMap[onScreenChangeListener] = rawListener
      viewTreeObserver.addOnDrawListener(rawListener)
    }

    override fun removeListener(onScreenChangeListener: OnScreenChangeListener) {
      savedObserverMap[onScreenChangeListener]?.let {
        viewTreeObserver.removeOnDrawListener(it)
      }
    }
  }

  companion object {
    operator fun invoke(viewTreeObserver: ViewTreeObserver): ScreenChangeTracker {
      if (isNativeGraphicsEnabled()) {
        return DrawChange(viewTreeObserver)
      }
      return GlobalLayoutChange(viewTreeObserver)
    }
  }
}