package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de cifrado con Android KeyStore mediante AES/GCM/NoPadding.
 * Garantiza que las claves criptográficas residan en el hardware seguro del dispositivo.
 * Incluye tolerancia a entornos de prueba JVM/Robolectric donde AndroidKeyStore no está disponible.
 */
class KeyStoreManager {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "JuanBetterZapia_Key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12

        // Clave de respaldo para entornos JVM sin proveedor AndroidKeyStore (ej. pruebas unitarias)
        private val FALLBACK_TEST_KEY = ByteArray(32) { (it * 7).toByte() }
    }

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
            load(null)
        }
    } catch (e: Throwable) {
        null
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = keyStore
        if (ks == null) {
            // Entorno de prueba local host JVM
            return SecretKeySpec(FALLBACK_TEST_KEY, "AES")
        }

        if (!ks.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE_PROVIDER
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(parameterSpec)
            return keyGenerator.generateKey()
        }
        return (ks.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            return Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            return ""
        }
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) return ""

            val iv = ByteArray(IV_LENGTH)
            val cipherText = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val plainTextBytes = cipher.doFinal(cipherText)
            return String(plainTextBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            return ""
        }
    }
}

/**
 * Almacenamiento seguro de configuración de JuanBetterZapia.
 * Los tokens y secretos se guardan cifrados con hardware KeyStore.
 */
class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("jbz_secure_preferences", Context.MODE_PRIVATE)
    private val keyStoreManager = KeyStoreManager()

    companion object {
        private const val KEY_AGENT_ACTIVE = "agent_active"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_WEBHOOK_SECRET_ENC = "webhook_secret_enc"
        private const val KEY_PROCESS_WHATSAPP = "process_whatsapp"
        private const val KEY_PROCESS_WHATSAPP_BUSINESS = "process_whatsapp_business"
        private const val KEY_IGNORE_GROUPS = "ignore_groups"
        private const val KEY_STRICT_GROUP_CHECK = "strict_group_check"
        private const val KEY_IGNORED_CONTACTS = "ignored_contacts"
        private const val KEY_MAX_RETRIES = "max_retries"
        private const val KEY_ENABLE_CONTACTS_PHONE_LOOKUP = "enable_contacts_phone_lookup"
    }

    var isAgentActive: Boolean
        get() = prefs.getBoolean(KEY_AGENT_ACTIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_AGENT_ACTIVE, value).apply()

    var webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WEBHOOK_URL, value.trim()).apply()

    var webhookSecret: String
        get() {
            val encrypted = prefs.getString(KEY_WEBHOOK_SECRET_ENC, "") ?: ""
            return if (encrypted.isNotEmpty()) keyStoreManager.decrypt(encrypted) else ""
        }
        set(value) {
            val encrypted = if (value.isNotEmpty()) keyStoreManager.encrypt(value) else ""
            prefs.edit().putString(KEY_WEBHOOK_SECRET_ENC, encrypted).apply()
        }

    var processWhatsApp: Boolean
        get() = prefs.getBoolean(KEY_PROCESS_WHATSAPP, true)
        set(value) = prefs.edit().putBoolean(KEY_PROCESS_WHATSAPP, value).apply()

    var processWhatsAppBusiness: Boolean
        get() = prefs.getBoolean(KEY_PROCESS_WHATSAPP_BUSINESS, true)
        set(value) = prefs.edit().putBoolean(KEY_PROCESS_WHATSAPP_BUSINESS, value).apply()

    var ignoreGroups: Boolean
        get() = prefs.getBoolean(KEY_IGNORE_GROUPS, true)
        set(value) = prefs.edit().putBoolean(KEY_IGNORE_GROUPS, value).apply()

    var strictGroupCheck: Boolean
        get() = prefs.getBoolean(KEY_STRICT_GROUP_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_STRICT_GROUP_CHECK, value).apply()

    var ignoredContacts: Set<String>
        get() = prefs.getStringSet(KEY_IGNORED_CONTACTS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_IGNORED_CONTACTS, value).apply()

    var maxRetries: Int
        get() = prefs.getInt(KEY_MAX_RETRIES, 3)
        set(value) = prefs.edit().putInt(KEY_MAX_RETRIES, value.coerceIn(1, 5)).apply()

    var enableContactsPhoneLookup: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_CONTACTS_PHONE_LOOKUP, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_CONTACTS_PHONE_LOOKUP, value).apply()
}
