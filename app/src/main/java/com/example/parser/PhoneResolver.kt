package com.example.parser

import android.Manifest
import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.regex.Pattern

/**
 * Estrategia legítima y no invasiva para la resolución del número de teléfono.
 * Cumple estrictamente con las políticas de privacidad y seguridad de Android:
 * 1. Inspección de URIs 'tel:' en Notification extras (android.people, EXTRA_MESSAGING_PERSON).
 * 2. Detección directa si el chat_name es un número telefónico (común en WhatsApp cuando el contacto no está guardado).
 * 3. Búsqueda legítima en la libreta de direcciones de Android (ContactsContract) si el usuario otorgó READ_CONTACTS.
 * 4. Si no se puede obtener con certeza, retorna null limpiamente.
 */
class PhoneResolver(private val context: Context) {

    companion object {
        // Expresión regular para números internacionales con formato E.164 o con espacios/guiones habituales
        private val PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\s\\-().]{7,25}$")
    }

    /**
     * Intenta resolver el número de teléfono por vías legítimas.
     * Retorna el número normalizado o null.
     */
    fun resolvePhone(
        chatName: String,
        extras: Bundle,
        allowContactsLookup: Boolean = true
    ): PhoneResolutionResult {
        // Estrategia 1: ¿El chat_name es ya un número de teléfono?
        val cleanedChatName = chatName.trim()
        if (isPhoneNumber(cleanedChatName)) {
            val normalized = normalizePhoneNumber(cleanedChatName)
            return PhoneResolutionResult(
                phone = normalized,
                strategy = "chat_name_is_phone",
                detail = "El remitente no está en la agenda y WhatsApp mostró su número directamente."
            )
        }

        // Estrategia 2: Extraer de Notification.EXTRA_PEOPLE_LIST (android.people)
        val peopleList = extras.getStringArrayList(Notification.EXTRA_PEOPLE_LIST)
        if (!peopleList.isNullOrEmpty()) {
            for (personUriStr in peopleList) {
                val phone = extractPhoneFromUri(personUriStr)
                if (phone != null) {
                    return PhoneResolutionResult(
                        phone = phone,
                        strategy = "notification_people_list",
                        detail = "Obtenido de android.people list en la notificación."
                    )
                }
            }
        }

        // Estrategia 3: Extraer de Notification.EXTRA_MESSAGING_PERSON (Android P+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val person = extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)
            if (person != null) {
                val uri = person.uri
                if (!uri.isNullOrEmpty()) {
                    val phone = extractPhoneFromUri(uri)
                    if (phone != null) {
                        return PhoneResolutionResult(
                            phone = phone,
                            strategy = "messaging_person_uri",
                            detail = "Obtenido del URI de Person en MessagingStyle."
                        )
                    }
                }
                val key = person.key
                if (!key.isNullOrEmpty() && isPhoneNumber(key)) {
                    return PhoneResolutionResult(
                        phone = normalizePhoneNumber(key),
                        strategy = "messaging_person_key",
                        detail = "Obtenido de la clave de Person en MessagingStyle."
                    )
                }
            }
        }

        // Estrategia 4: Consulta legítima a ContactsContract (si el permiso READ_CONTACTS está concedido)
        if (allowContactsLookup && hasContactsPermission()) {
            val phoneFromContacts = queryContactsByName(cleanedChatName)
            if (phoneFromContacts != null) {
                return PhoneResolutionResult(
                    phone = phoneFromContacts,
                    strategy = "android_contacts_contract",
                    detail = "Resuelto legítimamente desde los contactos del sistema mediante el nombre de chat."
                )
            }
        }

        // Si no se encuentra de forma fiable, retornar null
        return PhoneResolutionResult(
            phone = null,
            strategy = "none",
            detail = if (hasContactsPermission()) {
                "El contacto no tiene número asociado en la libreta o no coincide exactamente."
            } else {
                "No disponible en la notificación (WhatsApp usa el nombre de agenda). Permiso de Contactos no otorgado."
            }
        )
    }

    private fun isPhoneNumber(text: String): Boolean {
        if (text.length < 7 || text.length > 25) return false
        val digitsCount = text.count { it.isDigit() }
        if (digitsCount < 7) return false
        return PHONE_PATTERN.matcher(text).matches()
    }

    private fun normalizePhoneNumber(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "")
    }

    private fun extractPhoneFromUri(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme.equals("tel", ignoreCase = true)) {
                val schemeSpecific = uri.schemeSpecificPart
                if (!schemeSpecific.isNullOrEmpty() && isPhoneNumber(schemeSpecific)) {
                    normalizePhoneNumber(schemeSpecific)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun queryContactsByName(displayName: String): String? {
        if (displayName.isBlank()) return null
        var cursor: Cursor? = null
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? COLLATE NOCASE"
            val selectionArgs = arrayOf(displayName)

            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )

            if (cursor != null && cursor.moveToFirst()) {
                val normalizedIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                val normalized = if (normalizedIndex >= 0) cursor.getString(normalizedIndex) else null
                if (!normalized.isNullOrEmpty()) {
                    return normalized
                }
                val rawNumber = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                if (!rawNumber.isNullOrEmpty()) {
                    return normalizePhoneNumber(rawNumber)
                }
            }
        } catch (e: Exception) {
            // Ignorar y retornar null
        } finally {
            cursor?.close()
        }
        return null
    }
}

data class PhoneResolutionResult(
    val phone: String?,
    val strategy: String,
    val detail: String
)
