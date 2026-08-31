package com.android.car.settings.qc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import timber.log.Timber

/**
 * Direct-boot aware Quick Controls ContentProvider required by CarSystemUI.
 *
 * SystemUI status bars, quick control panels, and navigation bars query authority
 * `com.android.car.settings.qc` during startup to resolve fast settings tiles.
 */
class SettingsQCProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        Timber.i("SettingsQCProvider initialized (directBootAware=true)")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        Timber.d("SettingsQCProvider.query uri=%s", uri)
        return null
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.com.android.car.settings.qc"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        Timber.d("SettingsQCProvider.call method=%s, arg=%s", method, arg)
        return Bundle.EMPTY
    }
}
