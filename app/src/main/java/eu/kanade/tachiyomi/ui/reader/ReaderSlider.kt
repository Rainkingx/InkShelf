package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View.FOCUS_DOWN
import android.view.View.FOCUS_LEFT
import android.view.View.FOCUS_RIGHT
import android.view.View.FOCUS_UP
import com.google.android.material.slider.Slider

/**
 * Seekbar to show current chapter progress.
 */
class ReaderSlider
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : Slider(context, attrs) {
        /**
         * Whether the seekbar should draw from right to left.
         */
        var isRTL: Boolean
            set(value) {
                layoutDirection = if (value) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
            }
            get() = layoutDirection == LAYOUT_DIRECTION_RTL
        private val boundingBox: Rect = Rect()
        private val exclusions = listOf(boundingBox)

        /**
         * Whether the gamepad A button is currently held, letting a dpad press on the axis
         * matching this seekbar's own orientation adjust its value instead of just moving focus.
         */
        private var isAdjustModifierHeld = false

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.onLayout(changed, left, top, right, bottom)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && changed) {
                boundingBox.set(left, top, right, bottom)
                systemGestureExclusionRects = exclusions
            }
        }

        override fun onFocusChanged(
            gainFocus: Boolean,
            direction: Int,
            previouslyFocusedRect: Rect?,
        ) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            // Don't let a stale hold from a previous focus session silently re-enable adjustment.
            if (!gainFocus) {
                isAdjustModifierHeld = false
                isPressed = false
            }
        }

        /**
         * A focused slider normally swallows every dpad press in its axis to adjust its value,
         * which traps input focus.
         */
        override fun onKeyDown(
            keyCode: Int,
            event: KeyEvent,
        ): Boolean {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                isAdjustModifierHeld = true
                isPressed = true
                return true
            }
            val direction = FOCUS_DIRECTIONS[keyCode] ?: return super.onKeyDown(keyCode, event)
            if (isAdjustModifierHeld) {
                val isHorizontalKey = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                if (isHorizontalKey != isVertical) super.onKeyDown(keyCode, event)
                return true
            }
            val next = focusSearch(direction)
            return if (next != null && next !== this) next.requestFocus() else false
        }

        override fun onKeyUp(
            keyCode: Int,
            event: KeyEvent,
        ): Boolean {
            if (keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                isAdjustModifierHeld = false
                isPressed = false
                return true
            }
            return super.onKeyUp(keyCode, event)
        }

        private companion object {
            val FOCUS_DIRECTIONS =
                mapOf(
                    KeyEvent.KEYCODE_DPAD_LEFT to FOCUS_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT to FOCUS_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP to FOCUS_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN to FOCUS_DOWN,
                )
        }
    }
