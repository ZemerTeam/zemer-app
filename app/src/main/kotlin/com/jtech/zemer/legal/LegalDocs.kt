package com.jtech.zemer.legal

import androidx.datastore.preferences.core.MutablePreferences
import com.jtech.zemer.constants.AcceptedPrivacyAtKey
import com.jtech.zemer.constants.AcceptedPrivacyDocKey
import com.jtech.zemer.constants.AcceptedTermsDocKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One legal document as zemer.io publishes it; [body] is the markdown the app renders. */
@Serializable
data class LegalDoc(val version: Int, val updated: String = "", val title: String, val body: String)

/**
 * The privacy policy and terms of service, from content.zemer.io `/legal` (signed, generated from zemer.io so the
 * site and the app never drift), cached, with a built-in copy as the fallback. A document's [LegalDoc.version] is
 * bumped only for a change users must re-agree to.
 */
@Serializable
data class LegalDocs(val privacy: LegalDoc, val terms: LegalDoc) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** A `/legal` body, or null unless both documents have a version and a non-blank title and body. */
        fun parse(text: String): LegalDocs? =
            runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
                ?.takeIf { docs -> listOf(docs.privacy, docs.terms).all { it.version >= 1 && it.title.isNotBlank() && it.body.isNotBlank() } }
    }
}

/**
 * Agreement to the legal documents. A new install agrees on onboarding's Welcome step; an install whose agreed
 * versions are below the current ones is blocked by the update notice until it taps Agree (owner decision: no
 * decline). Both versions and the time reach the server in `info.settings`.
 */
object LegalAgreement {
    /** What this install has agreed to (0 = never), and whether onboarding (which asks itself) is done. */
    data class Agreed(val onboardingComplete: Boolean, val privacy: Int, val terms: Int)

    /** The documents this install has not agreed to yet, in display order; empty when nothing is owed. */
    fun pending(agreed: Agreed, docs: LegalDocs): List<LegalDoc> {
        if (!agreed.onboardingComplete) return emptyList() // onboarding asks instead
        return buildList {
            if (agreed.privacy < docs.privacy.version) add(docs.privacy)
            if (agreed.terms < docs.terms.version) add(docs.terms)
        }
    }

    fun record(prefs: MutablePreferences, docs: LegalDocs, now: Long = System.currentTimeMillis()) {
        prefs[AcceptedPrivacyDocKey] = docs.privacy.version
        prefs[AcceptedTermsDocKey] = docs.terms.version
        prefs[AcceptedPrivacyAtKey] = now
    }
}
