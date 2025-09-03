package alienkrishn.currentactivity.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvPackage, tvActivity, btnClose;
    private static boolean isRunning = false;

    private String currentPackageName = "";
    private String currentActivityName = "";

    private static final String CHANNEL_ID = "FloatingServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private BroadcastReceiver activityUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("CURRENT_ACTIVITY_UPDATE".equals(intent.getAction())) {
                currentPackageName = intent.getStringExtra("package");
                currentActivityName = intent.getStringExtra("activity");

                if (tvPackage != null && tvActivity != null) {
                    tvPackage.setText(currentPackageName);
                    tvActivity.setText(currentActivityName);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;

        // Create notification channel
        createNotificationChannel();

        // Start foreground service - this will work without POST_NOTIFICATIONS permission
        // on older Android versions, and on newer versions it will still work but might
        // show a default notification
        try {
            startForeground(NOTIFICATION_ID, createNotification());
        } catch (Exception e) {
            // If foreground service fails, continue as normal service
            Toast.makeText(this, "Service started without notification", Toast.LENGTH_SHORT).show();
        }

        // Register broadcast receiver
        IntentFilter filter = new IntentFilter("CURRENT_ACTIVITY_UPDATE");
        registerReceiver(activityUpdateReceiver, filter);

        createFloatingWindow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Return START_STICKY to keep service running even if system kills it
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        try {
            unregisterReceiver(activityUpdateReceiver);
        } catch (Exception e) {
            // Receiver was not registered
        }
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                // View was not attached
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Service Channel",
                    NotificationManager.IMPORTANCE_LOW
                );
                serviceChannel.setDescription("Channel for Floating Service");
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(serviceChannel);
                }
            } catch (Exception e) {
                // Channel creation failed, but service can still run
            }
        }
    }

    private Notification createNotification() {
        try {
            Intent notificationIntent = new Intent(this, MainActivity.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("Current Activity Detector")
                    .setContentText("Floating window is active")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
            } else {
                return new Notification.Builder(this)
                    .setContentTitle("Current Activity Detector")
                    .setContentText("Floating window is active")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
            }
        } catch (Exception e) {
            // Return a basic notification if anything fails
            return new Notification();
        }
    }

    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        floatingView = inflater.inflate(R.layout.floating_window, null);

        tvPackage = floatingView.findViewById(R.id.tvPackage);
        tvActivity = floatingView.findViewById(R.id.tvActivity);
        btnClose = floatingView.findViewById(R.id.btnClose);

        // Set initial text
        tvPackage.setText("Unknown");
        tvActivity.setText("Unknown");

        // Set up window parameters - make it fully movable
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 300;

        // Add view to window manager
        windowManager.addView(floatingView, params);

        // Set up close button
        btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopSelf();
                    // Also update the main activity switch
                    Intent intent = new Intent("FLOATING_WINDOW_CLOSED");
                    sendBroadcast(intent);
                }
            });

        // Set up click listeners for copy functionality
        tvPackage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(currentPackageName, "Package name copied");
                }
            });

        tvActivity.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(currentActivityName, "Activity name copied");
                }
            });

        // Set up drag functionality for the entire floating view
        floatingView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;

                        case MotionEvent.ACTION_UP:
                            // Check if it was a click (not drag)
                            if (Math.abs(event.getRawX() - initialTouchX) < 10 && 
                                Math.abs(event.getRawY() - initialTouchY) < 10) {
                                // It was a click, do nothing (let individual click listeners handle it)
                            }
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);

                            // Allow movement anywhere on screen
                            windowManager.updateViewLayout(floatingView, params);
                            return true;
                    }
                    return false;
                }
            });
    }

    private void copyToClipboard(String text, String message) {
        if (text == null || text.equals("Unknown")) {
            Toast.makeText(this, "No content to copy", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Current Activity", text);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static boolean isRunning() {
        return isRunning;
    }
}
