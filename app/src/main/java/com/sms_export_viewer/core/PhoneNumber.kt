package com.sms_export_viewer.core

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

private val util = PhoneNumberUtil.getInstance()

fun normalizeToE164(raw: String, defaultCountry: String = "US"): String {
    if (raw.isBlank()) return raw
    return try {
        val parsed = util.parse(raw, defaultCountry)
        if (util.isPossibleNumber(parsed)) {
            util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
        } else raw
    } catch (e: NumberParseException) {
        raw
    }
}
