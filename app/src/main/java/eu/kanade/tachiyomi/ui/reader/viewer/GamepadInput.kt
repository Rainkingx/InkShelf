package eu.kanade.tachiyomi.ui.reader.viewer

import android.view.MotionEvent
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/** Ignore small unintentional deflection on analog sticks. */
const val JOYSTICK_DEADZONE = 0.2f

/** Ignore small unintentional deflection on the L2/R2 analog triggers. */
const val TRIGGER_DEADZONE = 0.1f

/** Fraction of the page/recycler's width/height panned per dpad/keyboard press while zoomed in. */
const val PAN_STEP = 0.15f

/** Fraction of the page/recycler's width/height panned per joystick tick at full deflection. */
const val JOYSTICK_PAN_STEP = 0.05f

/** Delay between pan/scroll steps while the joystick is held away from center. */
const val JOYSTICK_PAN_INTERVAL_MS = 16L

/** Delay between zoom steps while a trigger/right stick input is held. */
const val ZOOM_HOLD_INTERVAL_MS = 16L

/**
 * Whether this motion event represents the d-pad reported as a hat switch (AXIS_HAT_X/Y),
 * common on many gamepads, rather than KEYCODE_DPAD_* key presses. Consuming these breaks
 * Android's SyntheticJoystickHandler, which turns unconsumed hat motion into the DPAD KeyEvent
 * page-turning relies on, so callers should bail out early when this is true.
 */
fun MotionEvent.isDpadHatMotion(): Boolean = getAxisValue(MotionEvent.AXIS_HAT_X) != 0f || getAxisValue(MotionEvent.AXIS_HAT_Y) != 0f

/**
 * Combined zoom rate in [-1, 1] from the L2/R2 analog triggers (falling back to AXIS_BRAKE/GAS
 * for controllers that report those instead) and the right stick's Y axis (pushed up zooms in).
 */
fun MotionEvent.gamepadZoomRate(): Float {
    val leftTrigger = maxOf(getAxisValue(MotionEvent.AXIS_LTRIGGER), getAxisValue(MotionEvent.AXIS_BRAKE))
    val rightTrigger = maxOf(getAxisValue(MotionEvent.AXIS_RTRIGGER), getAxisValue(MotionEvent.AXIS_GAS))
    val triggerRate = if (abs(rightTrigger - leftTrigger) > TRIGGER_DEADZONE) rightTrigger - leftTrigger else 0f
    val rightStickY = getAxisValue(MotionEvent.AXIS_RZ)
    val rightStickRate = if (abs(rightStickY) > JOYSTICK_DEADZONE) -rightStickY else 0f
    return (triggerRate + rightStickRate).coerceIn(-1f, 1f)
}

/**
 * Runs [action] on a fixed interval for as long as [rate] returns non-zero and the reader's
 * menu isn't open, e.g. driving continuous zoom from a held trigger. Call [update] whenever the
 * underlying input changes - it starts the loop if needed and lets it self-terminate otherwise.
 */
class GamepadHoldLoop(
    private val scope: CoroutineScope,
    private val activity: ReaderActivity,
    private val intervalMs: Long,
    private val rate: () -> Float,
    private val action: (Float) -> Unit,
) {
    private var job: Job? = null

    fun update() {
        if (rate() == 0f || activity.menuVisible) {
            job?.cancel()
            return
        }
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (isActive) {
                    val current = rate()
                    if (activity.menuVisible || current == 0f) break
                    action(current)
                    delay(intervalMs.milliseconds)
                }
            }
    }
}
