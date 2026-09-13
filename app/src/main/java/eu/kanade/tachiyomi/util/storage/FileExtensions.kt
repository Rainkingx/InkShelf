package eu.kanade.tachiyomi.util.storage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import eu.kanade.tachiyomi.BuildConfig
import java.io.File

/**
 * Returns the uri of a file
 *
 * @param context context of application
 */
fun File.getUriCompat(context: Context): Uri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", this)
