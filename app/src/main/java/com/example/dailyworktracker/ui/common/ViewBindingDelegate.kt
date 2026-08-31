package com.example.dailyworktracker.ui.common

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Holds a [ViewBinding] for the lifetime of a Fragment's *view*, not the Fragment itself.
 *
 * A Fragment outlives its view (for example while on the back stack), so keeping a binding in a
 * plain field leaks the whole view hierarchy. This delegate clears the reference in `onDestroyView`.
 *
 * Usage: `private val binding by viewBinding(FragmentHabitListBinding::bind)`
 */
class ViewBindingDelegate<T : ViewBinding>(
    private val fragment: Fragment,
    private val bind: (View) -> T,
) : ReadOnlyProperty<Fragment, T> {

    private var binding: T? = null

    init {
        fragment.viewLifecycleOwnerLiveData.observe(fragment) { viewLifecycleOwner ->
            viewLifecycleOwner?.lifecycle?.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        binding = null
                    }
                },
            )
        }
    }

    override fun getValue(thisRef: Fragment, property: KProperty<*>): T =
        binding ?: bind(thisRef.requireView()).also { binding = it }
}

fun <T : ViewBinding> Fragment.viewBinding(bind: (View) -> T): ViewBindingDelegate<T> =
    ViewBindingDelegate(this, bind)
