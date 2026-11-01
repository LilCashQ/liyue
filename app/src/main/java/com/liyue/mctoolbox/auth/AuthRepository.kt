package com.liyue.mctoolbox.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class AuthUser(val email: String, val nickname: String)

/**
 * Device-local account store. Passwords are salted and hashed with PBKDF2;
 * nothing leaves the device. Swap this class for a server-backed implementation
 * to get cross-device accounts.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("liyue_auth", Context.MODE_PRIVATE)
    private val _user = MutableStateFlow(loadSession())
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    fun register(email: String, password: String, nickname: String): Result<AuthUser> {
        val normalized = email.trim().lowercase()
        if (!isValidEmail(normalized)) return Result.failure(IllegalArgumentException("邮箱格式不正确"))
        if (password.length < 6) return Result.failure(IllegalArgumentException("密码至少 6 位"))
        if (nickname.isBlank()) return Result.failure(IllegalArgumentException("昵称不能为空"))

        val accounts = accounts()
        if (accounts.has(normalized)) return Result.failure(IllegalArgumentException("该邮箱已注册"))

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = hash(password, salt)
        accounts.put(
            normalized,
            JSONObject()
                .put("salt", Base64.getEncoder().encodeToString(salt))
                .put("hash", Base64.getEncoder().encodeToString(hash))
                .put("nickname", nickname.trim()),
        )
        prefs.edit().putString(KEY_ACCOUNTS, accounts.toString()).apply()
        return login(normalized, password)
    }

    fun login(email: String, password: String): Result<AuthUser> {
        val normalized = email.trim().lowercase()
        val accounts = accounts()
        val record = accounts.optJSONObject(normalized)
            ?: return Result.failure(IllegalArgumentException("账号不存在"))
        val salt = Base64.getDecoder().decode(record.getString("salt"))
        val expected = Base64.getDecoder().decode(record.getString("hash"))
        val actual = hash(password, salt)
        if (!MessageDigest.isEqual(expected, actual)) {
            return Result.failure(IllegalArgumentException("密码错误"))
        }
        val user = AuthUser(normalized, record.optString("nickname", normalized))
        prefs.edit().putString(KEY_SESSION, normalized).apply()
        _user.value = user
        return Result.success(user)
    }

    fun logout() {
        prefs.edit().remove(KEY_SESSION).apply()
        _user.value = null
    }

    private fun loadSession(): AuthUser? {
        val email = prefs.getString(KEY_SESSION, null) ?: return null
        val record = accounts().optJSONObject(email) ?: return null
        return AuthUser(email, record.optString("nickname", email))
    }

    private fun accounts(): JSONObject =
        prefs.getString(KEY_ACCOUNTS, null)?.let { JSONObject(it) } ?: JSONObject()

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    private fun isValidEmail(value: String): Boolean =
        value.length >= 5 && value.contains('@') && value.substringAfter('@').contains('.')

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_SESSION = "session"
    }
}
