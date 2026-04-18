package ax.nd.xposedutil

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.AccessibleObject

/**
 * Local stub replacing the unreachable ax.nd.xposedutil:xposedutil:1.1 Maven artifact.
 * Implements only the two extension points used by this module.
 */

/** Calls setAccessible(true) and returns the receiver for chaining. */
fun <T : AccessibleObject> T.asAccessible(): T {
    isAccessible = true
    return this
}

object XposedHelpersExt {
    /**
     * Runs [block] after every constructor of [clazz] completes.
     */
    fun runAfterClassConstructed(clazz: Class<*>, block: (XC_MethodHook.MethodHookParam) -> Unit) {
        XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                block(param)
            }
        })
    }
}
