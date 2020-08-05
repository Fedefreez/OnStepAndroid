package com.fablab.onstep;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.fablab.onstep.ui.bluetooth.BluetoothFragment;
import com.fablab.onstep.ui.commands.SendCustomCommandsFragment;
import com.fablab.onstep.ui.home.HomeFragment;
import com.fablab.onstep.ui.options.OptionsFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "OnStepApp";

    public static ArrayList<String> applicationLogs = new ArrayList<>();

    private static boolean canCreateSnackBar = true;

    private AppBarConfiguration mAppBarConfiguration;
    private String currentFragmentTag = "HomeFragment";

    private View hostFragment;

    String currentPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("DarkTheme", false)) {
            setTheme(R.style.DarkTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        mAppBarConfiguration = new AppBarConfiguration.Builder(R.id.nav_home, R.id.nav_options, R.id.nav_bluetooth).setOpenableLayout(drawer).build(); // -> by default we use setDrawerLayout instead of steOpenableLayout, but it's deprecated.sit
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        //NavigationUI.setupWithNavController(navigationView, navController);     //-> this garbage overrides our onNavigationItemSelectedListener, see https://stackoverflow.com/a/61273125/9419048

        OptionsFragment.fetchSettings(this);
        if (OptionsFragment.isAppFirstRun(this)) {
            OptionsFragment.setPreferencesBoolean("isAppFirstRun", false, this);
        }

        hostFragment = findViewById(R.id.nav_host_fragment);

        MainActivity.applicationLogs.add("Checking permissions...");
        preInitPermissionCheck();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.openLogsMenuItem:
                openLogs(MainActivity.this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static void openLogs(Context callingContext) {
        AlertDialog.Builder builder = new AlertDialog.Builder(callingContext, R.style.LogsPopupTheme);

        TextView textView = new TextView(callingContext);
        textView.setText(TextUtils.join("\n", applicationLogs));
        textView.setPadding(20, 10, 15, 0);
        textView.setTextColor(callingContext.getColor(R.color.textColorPrimary));

        ScrollView container = new ScrollView(callingContext);
        container.addView(textView);

        AlertDialog alertDialog = builder.setTitle("App Logs").setView(container).setPositiveButton("Ok", (dialog, which) -> dialog.dismiss()).create();
        Objects.requireNonNull(alertDialog.getWindow()).getAttributes().windowAnimations = R.style.DialogAnimation;
        alertDialog.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        ActionBar actionBar = getSupportActionBar(); //getActionBar() returns always null, because NavigationUI uses SupportActionBar

        if (actionBar == null) {
            createAlert("Couldn't get the ActionBar. You can ignore this warning, but it's title won't change anymore.", hostFragment, true);
        }

        if (id == R.id.nav_home) {
            Fragment homeFragment = new HomeFragment();
            switchFragment(homeFragment, "HomeFragment");
            if (actionBar != null) actionBar.setTitle("Home");
        } else if (id == R.id.nav_options) {
            Fragment optionsFragment = new OptionsFragment();
            switchFragment(optionsFragment, "OptionsFragment");
            if (actionBar != null) actionBar.setTitle("Options");
        } else if (id == R.id.nav_bluetooth) {
            Fragment bluetoothFragment = new BluetoothFragment();
            switchFragment(bluetoothFragment, "BluetoothFragment");
            if (actionBar != null) actionBar.setTitle("Bluetooth");
        } else if (id == R.id.nav_send_custom_commands) {
            Fragment sendCustomCommandsFragment = new SendCustomCommandsFragment();
            switchFragment(sendCustomCommandsFragment, "SendCustomCommandFragment");
            if (actionBar != null) actionBar.setTitle("Send custom commands");
        } else {
            Log.i(TAG, "No fragment id match!");
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void switchFragment(Fragment fragment, String newFragmentTag) {
        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        FragmentManager fragmentManager = getSupportFragmentManager();

        Fragment fragmentFromStack = fragmentManager.findFragmentByTag(newFragmentTag);

        if (fragmentFromStack != null) {
            applicationLogs.add("Using fragment from back stack.");
            fragmentManager.beginTransaction().replace(R.id.nav_host_fragment, fragmentFromStack, newFragmentTag).addToBackStack(currentFragmentTag).commit();
        } else {
            applicationLogs.add("Creating new fragment instance.");
            fragmentManager.beginTransaction().replace(R.id.nav_host_fragment, fragment, newFragmentTag).addToBackStack(currentFragmentTag).commit();
        }

        currentFragmentTag = newFragmentTag;

        drawerLayout.closeDrawer(GravityCompat.START);
    }

    public static void createAlert(String message, View view, boolean wait) {
        if (canCreateSnackBar) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).setAction("Action", null).show();
            if (wait)
                waitForSnackBarClosure();
        }
    }

    private void preInitPermissionCheck() {
        if (!checkLocationPermission()) {
            MainActivity.applicationLogs.add("Requesting GPS permission...");
            currentPermissionRequest = "GPS";
            ActivityCompat.requestPermissions(this, new String[]{ //always add permission to manifest.xml
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, 1);
        } else {
            MainActivity.applicationLogs.add("GPS permission granted.");
        }
        if (!checkBluetoothPermission()) {
            MainActivity.applicationLogs.add("Requesting Bluetooth permission...");

            currentPermissionRequest = "BLUETOOTH";
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.BLUETOOTH
            }, 1);
            currentPermissionRequest = "BLUETOOTH_ADMIN";
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.BLUETOOTH_ADMIN
            }, 1);
        } else {
            MainActivity.applicationLogs.add("Bluetooth permission granted.");
        }
    }

    private boolean checkBluetoothPermission() {
        String permission = "android.permission.BLUETOOTH";
        int res = this.checkCallingOrSelfPermission(permission);
        String permission2 = "android.permission.BLUETOOTH_ADMIN";
        int res2 = this.checkCallingOrSelfPermission(permission2);
        return (res == PackageManager.PERMISSION_GRANTED && res2 == PackageManager.PERMISSION_GRANTED);
    }

    private boolean checkLocationPermission(){
        String permission = "android.permission.ACCESS_FINE_LOCATION";

        int res = this.checkCallingOrSelfPermission(permission);

        return (res == PackageManager.PERMISSION_GRANTED);
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                switch (currentPermissionRequest) {
                    case "GPS":
                        createCriticalErrorAlert("Error", "You need to grant GPS permission to the app to allow Bluetooth scanning. This is an Android restriction.", getApplicationContext());
                        break;
                    case "BLUETOOTH":
                    case "BLUETOOTH_ADMIN":
                        createCriticalErrorAlert("Error", "You need to grant Bluetooth permission to the app to allow Bluetooth scanning.", getApplicationContext());
                        break;
                    default:
                        createCriticalErrorAlert("Error", "Due to an unknown error in the permission request the app can't work. A restart is necessary.", getApplicationContext());
                        break;
                }

            }
        } else {
            MainActivity.applicationLogs.add("Request code in onRequestPermissionResult() is != 1!");
        }
    }

    private static void waitForSnackBarClosure() {
        //if we do thread.sleep the touch is disabled, but the snackBar doesn't pop out
        canCreateSnackBar = false;

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                canCreateSnackBar = true;
            }
        }, 1000);
    }

    public static void createOverlayAlert(String title, String message, Context applicationContext) {
        if (applicationContext != null) {
            new AlertDialog.Builder(applicationContext, OptionsFragment.getPreferencesBoolean("DarkTheme", applicationContext) ?  R.style.DialogTheme : R.style.Theme_AppCompat_Light_Dialog).setTitle(title).setMessage(message).setPositiveButton("Ok", null).show();
        }
    }

    public static void createCriticalErrorAlert(String title, String message, Context applicationContext) {
        AlertDialog.Builder builder = new AlertDialog.Builder(applicationContext, OptionsFragment.getPreferencesBoolean("DarkTheme", applicationContext) ?  R.style.Theme_AppCompat_Light_Dialog : R.style.DialogTheme).setTitle(title).setMessage(message).setPositiveButton("Close", (dialog, which) -> {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(1);
                }
        );
        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();
    }

    public static void createPreferencesErrorAlert(String title, String message, Context applicationContext) {
        new AlertDialog.Builder(applicationContext, R.style.DialogTheme).setTitle(title).setMessage(message).setPositiveButton("Attempt automatic fix", (dialog, which) -> {
                    OptionsFragment.preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext);
                    if (OptionsFragment.preferences != null) {
                        createOverlayAlert("Success", "The automatic fix worked! :D", applicationContext);
                    }
                }
        ).setNegativeButton("Restart the app", (dialog, which) -> {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        }).setNeutralButton("Ignore", null).show();
    }
}