package com.android.car.settings.search;

import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_CLASS;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEY;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEYWORDS;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_SCREEN_TITLE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_SUMMARY_ON;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_TITLE;
import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.SearchIndexablesProvider;

/**
 * AAOS-compatible search index provider. Settings Intelligence can consume these raw rows while
 * the Compose Search feature provides the complete in-app, per-setting destination experience.
 */
public final class CarSettingsSearchIndexablesProvider extends SearchIndexablesProvider {
    @Override
    public Cursor queryXmlResources(String[] projection) {
        return new MatrixCursor(INDEXABLES_XML_RES_COLUMNS);
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_RAW_COLUMNS);
        add(cursor, "brightness", "Brightness", "Display", "Screen brightness and adaptive brightness",
                "android.settings.DISPLAY_SETTINGS");
        add(cursor, "date_time", "Date & time", "Display", "Automatic time and time zone",
                "android.settings.DISPLAY_SETTINGS");
        add(cursor, "wifi", "Wi-Fi", "Wi-Fi", "Wireless networks hotspot tethering",
                "android.settings.WIFI_SETTINGS");
        add(cursor, "network_internet", "Network & internet", "Network & internet",
                "Wi-Fi hotspot mobile data roaming", "android.settings.WIFI_SETTINGS");
        add(cursor, "mobile_network", "Mobile network", "Network & internet",
                "SIM mobile data roaming usage", "android.settings.WIFI_SETTINGS");
        add(cursor, "bluetooth", "Bluetooth", "Bluetooth", "Pair connected discoverable devices",
                "android.settings.BLUETOOTH_SETTINGS");
        add(cursor, "sound", "Sound & vibration", "Sound & vibration", "Volume ringtone alarm Do Not Disturb",
                "android.settings.SOUND_SETTINGS");
        add(cursor, "climate", "Climate", "Climate", "HVAC temperature fan airflow defrost seat comfort",
                "android.settings.HVAC_SETTINGS");
        add(cursor, "vehicle", "Vehicle", "Vehicle", "Driver assistance seats doors windows mirrors lighting",
                "android.settings.SETTINGS");
        add(cursor, "apps", "Apps", "Apps", "Applications permissions special access unused apps",
                "android.settings.APPLICATION_SETTINGS");
        add(cursor, "notifications", "Notifications", "Notifications", "Recently sent app notifications",
                "android.settings.NOTIFICATION_SETTINGS");
        add(cursor, "privacy", "Privacy", "Privacy", "Microphone camera location permissions",
                "android.settings.PRIVACY_SETTINGS");
        add(cursor, "location", "Location", "Location", "Location access ADAS recent access",
                "android.settings.LOCATION_SOURCE_SETTINGS");
        add(cursor, "accessibility", "Accessibility", "Accessibility", "Screen reader captions accessibility services",
                "android.settings.ACCESSIBILITY_SETTINGS");
        add(cursor, "assistant_voice", "Assistant & Voice", "Assistant & Voice", "Default assistant screen context voice input",
                "android.settings.VOICE_INPUT_SETTINGS");
        add(cursor, "security", "Security", "Security", "Screen lock credentials device admin",
                "android.settings.SECURITY_SETTINGS");
        add(cursor, "profiles", "Profiles & accounts", "Profiles & accounts", "Users accounts synchronization",
                "android.settings.SYNC_SETTINGS");
        add(cursor, "system", "System", "System", "About storage language reset",
                "android.settings.SETTINGS");
        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        return new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private void add(MatrixCursor cursor, String key, String title, String screenTitle,
            String keywords, String action) {
        Object[] row = new Object[INDEXABLES_RAW_COLUMNS.length];
        row[COLUMN_INDEX_RAW_TITLE] = title;
        row[COLUMN_INDEX_RAW_SUMMARY_ON] = screenTitle;
        row[COLUMN_INDEX_RAW_KEYWORDS] = keywords;
        row[COLUMN_INDEX_RAW_SCREEN_TITLE] = screenTitle;
        row[COLUMN_INDEX_RAW_INTENT_ACTION] = action;
        row[COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE] = getContext().getPackageName();
        row[COLUMN_INDEX_RAW_INTENT_TARGET_CLASS] = "com.android.car.settings.MainActivity";
        row[COLUMN_INDEX_RAW_KEY] = key;
        cursor.addRow(row);
    }
}
