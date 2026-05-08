package com.example.cradet

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Utility to fix the "BAD_DECRYPT" and "Failed to delete all registries" errors
 * by clearing corrupted internal data stores.
 */
object DataStoreFixer {
    
    fun cleanCorruptedRegistries(context: Context) {
        try {
            // The RegistryDataStore is usually located in the 'datastore' or 'files/phenotype' directory
            val datastoreDir = File(context.filesDir, "datastore")
            if (datastoreDir.exists() && datastoreDir.isDirectory) {
                Log.d("DataStoreFixer", "Checking datastore directory for corruption...")
                // In a real scenario, we might want to be more selective, 
                // but for "Failed to delete all registries", clearing the dir is a common fix.
                val files = datastoreDir.listFiles()
                files?.forEach { file ->
                    if (file.name.contains("Registry", ignoreCase = true) || file.name.endsWith(".pb")) {
                        Log.w("DataStoreFixer", "Deleting potentially corrupted registry: ${file.name}")
                        file.delete()
                    }
                }
            }

            // Also check for phenotype registries which are common sources of this error
            val phenotypeDir = File(context.filesDir, "phenotype")
            if (phenotypeDir.exists()) {
                Log.w("DataStoreFixer", "Deleting phenotype directory to resolve registry errors")
                phenotypeDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            Log.e("DataStoreFixer", "Error during registry cleanup", e)
        }
    }
}
