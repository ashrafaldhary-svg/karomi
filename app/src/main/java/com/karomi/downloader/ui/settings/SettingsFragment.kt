package com.karomi.downloader.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.karomi.downloader.KaromiApp
import com.karomi.downloader.R
import com.karomi.downloader.databinding.FragmentSettingsBinding
import com.karomi.downloader.library.PlaylistManager
import com.karomi.downloader.util.UpdateChecker
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val playlistManager by lazy { PlaylistManager(requireContext()) }

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportBackup(it) } }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importBackup(it) } }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = requireContext().getSharedPreferences(KaromiApp.PREFS, 0)

        // الوضع الليلي
        binding.darkModeSwitch.isChecked = prefs.getBoolean(KaromiApp.KEY_DARK_MODE, false)
        binding.darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KaromiApp.KEY_DARK_MODE, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }

        binding.backupButton.setOnClickListener { backupLauncher.launch("karomi_backup.json") }
        binding.restoreButton.setOnClickListener { restoreLauncher.launch(arrayOf("application/json", "text/plain")) }
        binding.updateButton.setOnClickListener { checkUpdates() }
    }

    private fun exportBackup(target: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                playlistManager.exportBackup(requireContext(), target)
                toast(getString(R.string.backup_done))
            } catch (e: Exception) {
                toast(getString(R.string.backup_failed, e.message))
            }
        }
    }

    private fun importBackup(source: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val count = playlistManager.importBackup(requireContext(), source)
                toast(getString(R.string.restore_done, count))
            } catch (e: Exception) {
                toast(getString(R.string.restore_failed, e.message))
            }
        }
    }

    /** فحص التحديثات من الموقع الرسمي فقط */
    private fun checkUpdates() {
        binding.updateButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = UpdateChecker(OkHttpClient()).check()
                if (result.hasUpdate) {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.update_available)
                        .setMessage(getString(R.string.update_message, result.latestVersion))
                        .setPositiveButton(R.string.open_site) { _, _ ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl)))
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                } else {
                    toast(getString(R.string.no_updates))
                }
            } catch (e: Exception) {
                toast(getString(R.string.update_check_failed))
            } finally {
                binding.updateButton.isEnabled = true
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
