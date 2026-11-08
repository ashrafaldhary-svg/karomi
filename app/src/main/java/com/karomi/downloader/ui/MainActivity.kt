package com.karomi.downloader.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.karomi.downloader.R
import com.karomi.downloader.databinding.ActivityMainBinding
import com.karomi.downloader.ui.home.HomeFragment
import com.karomi.downloader.ui.library.LibraryFragment
import com.karomi.downloader.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> show(HomeFragment())
                R.id.nav_library -> show(LibraryFragment())
                R.id.nav_settings -> show(SettingsFragment())
            }
            true
        }

        if (savedInstanceState == null) {
            show(HomeFragment())
            handleShareIntent(intent)
        }
        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** استقبال رابط مشاركة من تطبيق آخر وتمريره للشاشة الرئيسية */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val url = URL_REGEX.find(text)?.value ?: return
            binding.bottomNav.selectedItemId = R.id.nav_home
            show(HomeFragment.newInstance(url))
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host, fragment)
            .commit()
    }

    /** إذن الإشعارات مطلوب فقط على Android 13+ لعرض تقدم التحميل */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS
            )
        }
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 42
        private val URL_REGEX = Regex("https?://\\S+")
    }
}
