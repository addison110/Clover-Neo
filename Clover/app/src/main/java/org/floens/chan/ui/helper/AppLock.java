/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.helper;

import android.content.Context;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.Logger;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;

/**
 * Covers the activity and asks for the fingerprint, face or screen lock when the app lock is on.
 * It locks when the process starts, and when the app comes back after being away for longer
 * than the chosen timeout.
 */
public class AppLock {
    private static final String TAG = "AppLock";

    private static final int AUTHENTICATORS = BIOMETRIC_WEAK | DEVICE_CREDENTIAL;

    // Kept for the whole process, so a recreated activity doesn't ask again.
    private static boolean unlocked = false;
    private static long leftAt = 0;
    // The prompt can outlive the activity, don't show a second one.
    private static boolean authenticating = false;

    private final FragmentActivity activity;
    private final BiometricPrompt prompt;
    private View lockView;

    // Added after the activity's own callback, so it is asked first.
    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            activity.moveTaskToBack(true);
        }
    };

    public static boolean isAvailable(Context context) {
        return BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS;
    }

    public AppLock(FragmentActivity activity) {
        this.activity = activity;

        // Must be made in onCreate, so the result of a prompt from before a recreate arrives here.
        prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        authenticating = false;
                        unlock();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        // Cancelled or failed too often, the lock screen stays with its unlock button.
                        Logger.d(TAG, "Authentication error " + errorCode + ": " + errString);
                        authenticating = false;
                    }
                });

        activity.getOnBackPressedDispatcher().addCallback(activity, backCallback);
    }

    public boolean isLocked() {
        return lockView != null;
    }

    public void onStart() {
        if (!ChanSettings.appLock.get()) {
            // Turning the lock on later shouldn't lock the app right away.
            unlocked = true;
            leftAt = 0;
            return;
        }

        if (unlocked && leftAt != 0) {
            long away = SystemClock.elapsedRealtime() - leftAt;
            if (away >= ChanSettings.appLockTimeout.get() * 1000L) {
                unlocked = false;
            }
        }
        leftAt = 0;

        if (!unlocked) {
            if (isAvailable(activity)) {
                showLockView();
            } else {
                // The screen lock was removed, don't lock the user out.
                Logger.w(TAG, "No screen lock available, not locking");
                unlocked = true;
            }
        }
    }

    public void onResume() {
        if (isLocked()) {
            authenticate();
        }
    }

    public void onStop() {
        // The prompt for the screen lock can be its own activity, that is not leaving the app.
        if (unlocked && !authenticating && !activity.isChangingConfigurations()) {
            leftAt = SystemClock.elapsedRealtime();
        }
    }

    private void authenticate() {
        if (!authenticating) {
            showPrompt();
        }
    }

    private void showPrompt() {
        authenticating = true;

        String appName = activity.getString(R.string.app_name);
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.app_lock_prompt_title, appName))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build();
        prompt.authenticate(info);
    }

    private void showLockView() {
        if (lockView == null) {
            lockView = LayoutInflater.from(activity).inflate(R.layout.layout_app_lock, null);
            TextView text = lockView.findViewById(R.id.app_lock_text);
            text.setText(activity.getString(R.string.app_lock_locked,
                    activity.getString(R.string.app_name)));
            lockView.findViewById(R.id.app_lock_unlock).setOnClickListener(v -> showPrompt());
        }

        // Re-add it so it is above views added to the content view since the last time.
        ViewGroup parent = (ViewGroup) lockView.getParent();
        if (parent != null) {
            parent.removeView(lockView);
        }
        activity.addContentView(lockView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        backCallback.setEnabled(true);
    }

    private void unlock() {
        unlocked = true;
        if (lockView != null) {
            ViewGroup parent = (ViewGroup) lockView.getParent();
            if (parent != null) {
                parent.removeView(lockView);
            }
            lockView = null;
        }
        backCallback.setEnabled(false);
    }
}
