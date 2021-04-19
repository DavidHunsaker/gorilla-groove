package com.gorilla.gorillagroove.ui.login

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings.Secure
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.gorilla.gorillagroove.BuildConfig
import com.gorilla.gorillagroove.R
import com.gorilla.gorillagroove.network.NetworkApi
import com.gorilla.gorillagroove.network.login.LoginRequest
import com.gorilla.gorillagroove.service.GGLog.logError
import com.gorilla.gorillagroove.service.GGLog.logInfo
import com.gorilla.gorillagroove.service.sync.ServerSynchronizer
import com.gorilla.gorillagroove.util.Constants
import com.gorilla.gorillagroove.util.CurrentDevice
import com.gorilla.gorillagroove.util.GGToast
import com.gorilla.gorillagroove.util.hideKeyboard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_login.*
import kotlinx.coroutines.*
import java.util.*
import javax.inject.Inject


@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    @Inject
    lateinit var sharedPref: SharedPreferences

    @Inject
    lateinit var serverSynchronizer: ServerSynchronizer

    @Inject
    lateinit var networkApi: NetworkApi

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logInfo("Loading Login view")

        // If we have an auth token then they launched the app while logged in. Redirect to the main screen.
        if (sharedPref.contains(Constants.KEY_USER_TOKEN)) {
            navigateToMainApp()

            return
        }

        edit_text_email.requestFocus()

        button_login.setOnClickListener {
            val deviceId = fetchDeviceUUID()

            val loginRequest = LoginRequest(
                email = edit_text_email.text.toString(),
                password = edit_text_password.text.toString(),
                deviceId = deviceId.toString(),
                preferredDeviceName = CurrentDevice.getDeviceName(context),
                version = BuildConfig.VERSION_NAME,
                deviceType = "ANDROID"
            )

            displayProgressBar(true)

            requireActivity().hideKeyboard()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = networkApi.login(loginRequest)

                    sharedPref.edit().putString(Constants.KEY_USER_TOKEN, response.token).apply()

                    val toast = GGToast.show("Syncing first time data ...", Toast.LENGTH_LONG)
                    // TODO This is currently blocking. Shouldn't be
                    serverSynchronizer.syncWithServer()

                    withContext(Dispatchers.Main) {
                        toast.cancel()
                        navigateToMainApp()
                    }
                } catch (e: Throwable) {
                    logError("Failed to log in!", e)
                    withContext(Dispatchers.Main) {
                        displayProgressBar(false)
                        GGToast.show("Failed to sign in")
                    }
                }
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun fetchDeviceUUID(): UUID {
        val androidId = Secure.getString(context?.contentResolver, Secure.ANDROID_ID)
        return UUID.nameUUIDFromBytes(androidId.toByteArray(charset("utf8")))
    }

    private fun displayProgressBar(isDisplayed: Boolean) {
        if (isDisplayed) {
            progress_bar_login.visibility = View.VISIBLE
            progress_bar_login.bringToFront()
            progress_bar_login.requestFocus()
        } else {
            progress_bar_login.visibility = View.GONE
        }
    }

    private fun navigateToMainApp() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.libraryTrackFragment, true)
            .build()

        findNavController().navigate(
            R.id.libraryTrackFragment,
            null,
            navOptions
        )
    }
}
