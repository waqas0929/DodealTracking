package com.dodeal.updater.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import com.dodeal.updater.UpdaterLog
import com.dodeal.updater.config.Allowlist
import java.io.File
import java.security.MessageDigest

sealed interface VerifyResult {
    data object Ok : VerifyResult
    data class Rejected(val code: String, val message: String) : VerifyResult
}

/**
 * Verifies a downloaded APK before it is ever handed to PackageInstaller (spec §8):
 *  - package on the allowlist and equals the expected package
 *  - APK signing certificate equals the approved Dodeal certificate (SHA-256 of the cert)
 *  - SHA-256 of the file equals the server metadata
 *  - versionCode is valid and newer (unless rollback explicitly allowed)
 *  - file size within expected limits
 * Any failure => reject; nothing is installed.
 */
class PackageVerifier(private val context: Context) {

    fun verify(
        apk: File,
        expectedPackage: String,
        expectedVersionCode: Long,
        expectedSha256: String,
        expectedSizeBytes: Long?,
        allowRollback: Boolean,
    ): VerifyResult {
        if (!Allowlist.isAllowed(expectedPackage) || !ApprovedCertificates.isManaged(expectedPackage)) {
            return reject("PACKAGE_MISMATCH", "package not allowlisted: $expectedPackage")
        }
        if (!apk.exists() || apk.length() == 0L) {
            return reject("HASH_MISMATCH", "apk missing/empty")
        }
        // Size sanity
        expectedSizeBytes?.let {
            if (apk.length() != it) return reject("HASH_MISMATCH", "size ${apk.length()} != $it")
        }
        if (apk.length() > MAX_APK_BYTES) return reject("HASH_MISMATCH", "apk too large")

        // Hash
        val actualSha = sha256(apk)
        if (!actualSha.equals(expectedSha256, ignoreCase = true)) {
            return reject("HASH_MISMATCH", "sha256 mismatch")
        }
        UpdaterLog.i("HASH_VERIFIED")

        // Parse APK metadata (package + version + signatures)
        val info = parseApk(apk) ?: return reject("PACKAGE_MISMATCH", "cannot parse apk")
        if (info.packageName != expectedPackage) {
            return reject("PACKAGE_MISMATCH", "apk package ${info.packageName} != $expectedPackage")
        }
        UpdaterLog.i("PACKAGE_VERIFIED")

        val apkVersion = versionCodeOf(info)
        if (apkVersion != expectedVersionCode) {
            return reject("HASH_MISMATCH", "apk versionCode $apkVersion != $expectedVersionCode")
        }

        // Signature: the apk cert must match the certificate pinned for THIS package (each Dodeal
        // app has its own permanent production identity). If not yet pinned, fall back to requiring
        // the update cert to equal the currently-installed app's cert.
        val apkCerts = certSha256s(apk)
        if (apkCerts.isEmpty()) return reject("SIGNATURE_MISMATCH", "apk unsigned")

        val pinned = ApprovedCertificates.expectedCert(expectedPackage)
        if (pinned != null) {
            if (pinned !in apkCerts) {
                return reject("SIGNATURE_MISMATCH", "apk cert != pinned cert for $expectedPackage")
            }
        } else {
            // No pin configured yet — still block foreign APKs via installed-cert match.
            installedCertSha256(expectedPackage)?.let { installedCert ->
                if (installedCert !in apkCerts) {
                    return reject("SIGNATURE_MISMATCH", "apk cert != installed cert (would break update)")
                }
            }
        }

        // Defence in depth: even with a matching pin, an in-place update must match the installed
        // cert or Android would refuse it anyway.
        installedCertSha256(expectedPackage)?.let { installedCert ->
            if (installedCert !in apkCerts) {
                return reject("SIGNATURE_MISMATCH", "apk cert != installed cert (would break update)")
            }
        }
        UpdaterLog.i("SIGNATURE_VERIFIED")

        // Version rule
        val installedVersion = installedVersionCode(expectedPackage)
        if (installedVersion != null && apkVersion <= installedVersion && !allowRollback) {
            return reject("HASH_MISMATCH", "not newer ($apkVersion <= $installedVersion)")
        }
        return VerifyResult.Ok
    }

    // --- helpers ---

    private fun reject(code: String, message: String): VerifyResult {
        UpdaterLog.w("UPDATE_SECURITY_CHECK_FAILED $code: $message")
        UpdaterLog.w(code)
        return VerifyResult.Rejected(code, message)
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseApk(apk: File): PackageInfo? =
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun installedVersionCode(pkg: String): Long? = try {
        val pi = context.packageManager.getPackageInfo(pkg, 0)
        versionCodeOf(pi)
    } catch (_: PackageManager.NameNotFoundException) { null }

    @Suppress("DEPRECATION", "PackageManagerGetSignatures")
    private fun certSha256s(apk: File): Set<String> {
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return emptySet()
        val sigs: Array<Signature> = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pi.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            @Suppress("DEPRECATION") pi.signatures ?: emptyArray()
        }
        return sigs.map { hashSig(it) }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun installedCertSha256(pkg: String): String? = try {
        val flags = if (android.os.Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = context.packageManager.getPackageInfo(pkg, flags)
        val sigs = if (android.os.Build.VERSION.SDK_INT >= 28)
            pi.signingInfo?.apkContentsSigners ?: emptyArray()
        else @Suppress("DEPRECATION") pi.signatures ?: emptyArray()
        sigs.firstOrNull()?.let { hashSig(it) }
    } catch (_: Exception) { null }

    private fun hashSig(sig: Signature): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MAX_APK_BYTES = 300L * 1024 * 1024 // 300 MB ceiling
        // Per-package certificate pins live in ApprovedCertificates.APPROVED_PACKAGES.
    }
}
