package com.fablab.onstep.ui.options;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.fablab.onstep.MainActivity;
import com.fablab.onstep.R;

public class OptionsFragment extends Fragment {

    public static SharedPreferences preferences;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_options, container, false);

        Switch darkThemeSwitch = root.findViewById(R.id.darkThemeSwitch);
        darkThemeSwitch.setChecked(getPreferencesBoolean("DarkTheme", requireContext()));
        darkThemeSwitch.setOnCheckedChangeListener((v, checked) -> {
            setPreferencesBoolean("DarkTheme", checked, requireContext());
            MainActivity.createCriticalErrorAlert("Warning", "You need to restart the app to apply any theme change.", requireContext());
        });

        return root;
    }

    public static void fetchSettings(Context applicationContext) {
        preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);

        if (isAppFirstRun(applicationContext)) {
            setPreferencesBoolean("debug", false, applicationContext);
        }
    }

    public static int getPreferencesInt(String key, Context applicationContext) {
        int obtainedValue;
        if (!preferences.contains(key)) {
            MainActivity.createOverlayAlert("Error", "Error while reading: " + key + ". It is recommended to restart the app.", applicationContext);
            obtainedValue = -1;
        } else {
            obtainedValue = preferences.getInt(key, -1);
        }
        return obtainedValue;
    }

    public static void setPreferencesInt(String key, int value, Context applicationContext) {
        if (preferences != null) {
            if (!preferences.edit().putInt(key, value).commit()) {
                MainActivity.createOverlayAlert("Error", "Error while writing: " + key + ". It is recommended to restart the app.", applicationContext);
            }
        }
    }

    public static boolean isAppFirstRun(Context applicationContext) {
        if (preferences != null) {
            return preferences.getBoolean("isAppFirstRun", true);
        } else {
            MainActivity.createOverlayAlert("Error", "Error while fetching preferences. It is recommended to restart the app.", applicationContext);
            return false;
        }
    }

    public static void setPreferencesBoolean(String key, boolean value, Context applicationContext) {
        if (preferences != null) {
            if (!preferences.edit().putBoolean(key, value).commit()) {
                MainActivity.createOverlayAlert("Error", "Error while writing: " + key + ". It is recommended to restart the app.", applicationContext);
            }
        } else {
            MainActivity.createPreferencesErrorAlert("Error", "It isn't possible for the app to save or read settings anymore due to an unknown error, that occurred during the app startup.", applicationContext);
        }
    }

    public static boolean getPreferencesBoolean(String key, Context applicationContext) {
        if (preferences == null) {
            MainActivity.createPreferencesErrorAlert("Error", "It isn't possible for the app to save or read settings anymore due to an unknown error, that occurred during the app startup.", applicationContext);
            return false;
        } else {
            return preferences.getBoolean(key, false);
        }

    }
}