package com.example.dailyworktracker.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.dailyworktracker.BuildConfig
import com.example.dailyworktracker.R
import com.example.dailyworktracker.data.sample.SampleDataSeeder
import com.example.dailyworktracker.databinding.FragmentSettingsBinding
import com.example.dailyworktracker.ui.common.viewBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the debug seeder and nothing else.
 *
 * The mockup defines no Settings content. Rather than invent features to fill the screen, it stays
 * sparse and gives the seeder a home now that the habit list has no overflow menu.
 */
@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val binding by viewBinding(FragmentSettingsBinding::bind)

    /** Only ever used behind a `BuildConfig.DEBUG` check; see [seedSampleData]. */
    @Inject
    lateinit var sampleDataSeeder: SampleDataSeeder

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        // Sample data is a development aid; it must never be reachable in a release build.
        binding.buttonSeedSampleData.visibility =
            if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.buttonSeedSampleData.setOnClickListener { seedSampleData() }
    }

    /**
     * Deliberately driven from the Fragment rather than a ViewModel: the seeder is not part of the
     * app's behaviour, and threading it through a production ViewModel would put a debug dependency
     * in its constructor and in every test that builds one.
     */
    private fun seedSampleData() {
        viewLifecycleOwner.lifecycleScope.launch {
            sampleDataSeeder.seed()
            Snackbar.make(
                binding.root,
                R.string.debug_sample_data_inserted,
                Snackbar.LENGTH_SHORT,
            ).show()
        }
    }
}
