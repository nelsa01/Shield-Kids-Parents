package com.shieldtechhub.shieldkids

import java.security.MessageDigest
import java.util.*

object SecurityUtils {
    fun generateRefNumber(): String {
        val random = Random()
        return (1000 + random.nextInt(9000)).toString()
    }

    fun hashRefNumber(refNumber: String): String {
        val bytes = refNumber.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun verifyRefNumber(input: String, storedHash: String): Boolean {
        return hashRefNumber(input) == storedHash
    }
}