package com.jtech.zemer.utils

/**
 * Utility functions for Firestore operations
 */

/**
 * Sanitize email for use as Firestore document ID
 * Replaces special characters with Firestore-safe alternatives
 * Example: "user@example.com" -> "user_at_example_dot_com"
 */
fun sanitizeEmailForDocumentId(email: String): String {
    return email.lowercase()
        .replace("@", "_at_")
        .replace(".", "_dot_")
        .replace(" ", "_")
        .replace("#", "_hash_")
        .replace("$", "_dollar_")
        .replace("[", "_lbrack_")
        .replace("]", "_rbrack_")
        .replace("/", "_slash_")
        .replace("\\", "_backslash_")
        .replace("?", "_question_")
        .replace("&", "_amp_")
        .replace("=", "_equals_")
        .replace("+", "_plus_")
        .replace(",", "_comma_")
        .replace(";", "_semicolon_")
        .replace(":", "_colon_")
}

/**
 * Validate that a document ID meets Firestore requirements
 */
fun isValidFirestoreDocumentId(documentId: String): Boolean {
    return documentId.isNotEmpty() &&
           documentId.length <= 1500 &&
           !documentId.contains("..") &&
           !documentId.startsWith(".") &&
           !documentId.endsWith(".")
}