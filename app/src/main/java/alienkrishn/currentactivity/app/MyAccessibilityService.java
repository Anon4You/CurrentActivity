package alienkrishn.currentactivity.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;

public class MyAccessibilityService extends AccessibilityService {

    private static boolean isRunning = false;
    private String lastPackageName = "";
    private String lastClassName = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (event.getPackageName() != null && event.getClassName() != null) {
                    String packageName = event.getPackageName().toString();
                    String className = event.getClassName().toString();

                    // Filter out system windows and irrelevant events
                    if (!packageName.equals("android") && 
                        !className.contains("PopupWindow") &&
                        !className.contains("Toast") &&
                        !className.contains("DecorView")) {

                        // Only send update if the activity actually changed
                        if (!packageName.equals(lastPackageName) || !className.equals(lastClassName)) {
                            lastPackageName = packageName;
                            lastClassName = className;

                            // Send broadcast with package and activity info
                            Intent intent = new Intent("CURRENT_ACTIVITY_UPDATE");
                            intent.putExtra("package", packageName);
                            intent.putExtra("activity", className);
                            sendBroadcast(intent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("AccessibilityService", "Error handling accessibility event", e);
        }
    }

    @Override
    public void onInterrupt() {
        // Service was interrupted - but will restart automatically
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isRunning = true;

        // Configure the accessibility service
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.DEFAULT |
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;

        setServiceInfo(info);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // This will be called when service is disabled by user
        isRunning = false;
        lastPackageName = "";
        lastClassName = "";
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        isRunning = true;
    }

    public static boolean isRunning() {
        return isRunning;
    }
}
