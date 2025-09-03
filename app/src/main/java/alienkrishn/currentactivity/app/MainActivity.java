package alienkrishn.currentactivity.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int OVERLAY_PERMISSION_REQUEST = 1001;
    private static final String PREFS_NAME = "CurrentActivityPrefs";
    private static final String PREF_SWITCH_STATE = "switchState";
    private static final String PREF_ACCESSIBILITY_ENABLED = "accessibilityEnabled";

    private Switch toggleSwitch;
    private Button btnOverlayPermission;
    private Button btnAccessibility;

    private BroadcastReceiver floatingWindowClosedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("FLOATING_WINDOW_CLOSED".equals(intent.getAction())) {
                toggleSwitch.setChecked(false);
                saveSwitchState(false);
                Toast.makeText(MainActivity.this, "Floating window closed", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleSwitch = findViewById(R.id.toggleSwitch);
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission);
        btnAccessibility = findViewById(R.id.btnAccessibility);

        // Register receiver for floating window close events
        IntentFilter filter = new IntentFilter("FLOATING_WINDOW_CLOSED");
        registerReceiver(floatingWindowClosedReceiver, filter);

        // Load saved switch state
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean switchState = prefs.getBoolean(PREF_SWITCH_STATE, false);
        toggleSwitch.setChecked(switchState);

        // Check if accessibility was previously enabled
        boolean accessibilityWasEnabled = prefs.getBoolean(PREF_ACCESSIBILITY_ENABLED, false);

        // Check permissions and update UI
        checkPermissions();

        // If accessibility was enabled before, check if it's still enabled
        if (accessibilityWasEnabled && !MyAccessibilityService.isRunning()) {
            // Accessibility service was stopped, need to guide user to re-enable
            Toast.makeText(this, "Accessibility service was disabled. Please re-enable it.", Toast.LENGTH_LONG).show();
            btnAccessibility.setVisibility(View.VISIBLE);
        }

        // Set up switch listener to enable/disable floating window
        toggleSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        // Enable floating window
                        if (checkPermissions()) {
                            startFloatingService();
                            Toast.makeText(MainActivity.this, "Floating window enabled", Toast.LENGTH_SHORT).show();
                        } else {
                            toggleSwitch.setChecked(false);
                            Toast.makeText(MainActivity.this, "Please enable all permissions first", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // Disable floating window
                        stopFloatingService();
                        Toast.makeText(MainActivity.this, "Floating window disabled", Toast.LENGTH_SHORT).show();
                    }
                    saveSwitchState(isChecked);
                }
            });

        // Set up button listeners
        btnOverlayPermission.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestOverlayPermission();
                }
            });

        btnAccessibility.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openAccessibilitySettings();
                }
            });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_contact) {
            openTelegram();
            return true;
        } else if (id == R.id.menu_source) {
            openGitHub();
            return true;
        } else if (id == R.id.menu_about) {
            showAboutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openTelegram() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://t.me/nullxvoid"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open Telegram link", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGitHub() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/Anon4You/CurrentActivity"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open GitHub link", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("About Current Activity");
        builder.setMessage("This app displays the current foreground app's package name and activity name in a floating window.\n\nYou can:\n• See real-time app/activity info\n• Drag the floating window anywhere\n• Click to copy package/activity names\n• Toggle on/off with the switch\n\nMade with 💗 By Alienkrishn");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        builder.setCancelable(true);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(floatingWindowClosedReceiver);
        } catch (Exception e) {
            // Receiver was not registered
        }
    }

    private boolean checkPermissions() {
        boolean hasOverlayPermission = Settings.canDrawOverlays(this);
        boolean hasAccessibility = MyAccessibilityService.isRunning();

        btnOverlayPermission.setVisibility(hasOverlayPermission ? View.GONE : View.VISIBLE);
        btnAccessibility.setVisibility(hasAccessibility ? View.GONE : View.VISIBLE);

        // Save accessibility state
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_ACCESSIBILITY_ENABLED, hasAccessibility);
        editor.apply();

        // Enable/disable switch based on permissions
        toggleSwitch.setEnabled(hasOverlayPermission && hasAccessibility);

        return hasOverlayPermission && hasAccessibility;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                       Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST);
        } else {
            Toast.makeText(this, "Overlay permission not required on this Android version", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Please enable 'Current Activity' in accessibility services", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open accessibility settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFloatingService() {
        try {
            Intent serviceIntent = new Intent(this, FloatingService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot start service", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopFloatingService() {
        try {
            Intent serviceIntent = new Intent(this, FloatingService.class);
            stopService(serviceIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot stop service", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSwitchState(boolean state) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_SWITCH_STATE, state);
        editor.apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            checkPermissions();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();

        // Update switch state based on current service status
        boolean isServiceRunning = FloatingService.isRunning();
        if (toggleSwitch.isChecked() != isServiceRunning) {
            toggleSwitch.setChecked(isServiceRunning);
            saveSwitchState(isServiceRunning);
        }
    }
}
