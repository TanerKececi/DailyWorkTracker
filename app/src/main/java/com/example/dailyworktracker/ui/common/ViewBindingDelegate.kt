package com.example.dailyworktracker.ui.common

import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
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
 * plain field leaks the whole view hierarchy.
 *
 * The cached binding is keyed to the exact [View] it was created from. That matters because
 * `Fragment.performDestroyView()` dispatches the view lifecycle's `ON_DESTROY` *before* calling
 * `onDestroyView()`: a Fragment cleaning up in `onDestroyView` re-reads this property, which would
 * otherwise cache a fresh binding over the dying view and hand that stale binding to the *next*
 * view. Everything set up in `onViewCreated` would then be applied to a detached hierarchy.
 *
 * Usage: `private val binding by viewBinding(FragmentHabitListBinding::bind)`
 */
class ViewBindingDelegate<T : ViewBinding>(
    fragment: Fragment,
    private val bind: (View) -> T,
) : ReadOnlyProperty<Fragment, T> {
    private var binding: T? = null
    private var boundView: View? = null

    init {
        fragment.viewLifecycleOwnerLiveData.observe(fragment) { viewLifecycleOwner ->
            viewLifecycleOwner?.lifecycle?.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        binding = null
                        boundView = null
                    }
                },
            )
        }
    }

    override fun getValue(
        thisRef: Fragment,
        property: KProperty<*>,
    ): T {
        val view = thisRef.requireView()
        binding?.let { cached -> if (boundView === view) return cached }

        // A data binding may be created only once per view: binding a second time throws
        // "view must have a tag". That happens on the path this class already guards - the cache is
        // cleared when the view lifecycle is destroyed, which is *before* `onDestroyView` runs, so a
        // Fragment cleaning up there asks for the binding again. Reuse whatever is already attached
        // to the view; plain ViewBinding layouts have nothing attached and fall through to bind().
        @Suppress("UNCHECKED_CAST")
        val attached = DataBindingUtil.getBinding<ViewDataBinding>(view) as T?

        return (attached ?: bind(view)).also {
            binding = it
            boundView = view
        }
    }
}

fun <T : ViewBinding> Fragment.viewBinding(bind: (View) -> T): ViewBindingDelegate<T> = ViewBindingDelegate(this, bind)
