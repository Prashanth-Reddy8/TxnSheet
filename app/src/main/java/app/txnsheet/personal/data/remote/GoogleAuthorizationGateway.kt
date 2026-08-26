package app.txnsheet.personal.data.remote

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.txnsheet.personal.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * An access token whose lifetime is deliberately limited to the current operation.
 *
 * The raw value is module-internal and [toString] is redacted so it cannot accidentally
 * end up in WorkManager input, Room, or diagnostics.
 */
class EphemeralAccessToken internal constructor(internal val raw: String) {
    override fun toString(): String = "EphemeralAccessToken([REDACTED])"
}

sealed interface AuthorizationOutcome {
    data class Authorized(
        val token: EphemeralAccessToken,
        val account: Account?,
        val grantedScopes: Set<String>,
    ) : AuthorizationOutcome

    /** Must be launched by an Activity. A Worker maps this to AUTH_REQUIRED. */
    data class ResolutionRequired(val pendingIntent: PendingIntent) : AuthorizationOutcome

    data class Unavailable(
        val code: String,
        val isTransient: Boolean,
    ) : AuthorizationOutcome
}

interface GoogleAuthorizationGateway {
    /**
     * Uses application Context and returns immediately when the existing grant is usable.
     * This method never launches UI and never persists the returned token.
     */
    suspend fun authorizeSilently(): AuthorizationOutcome

    /** Converts the Activity result from [AuthorizationOutcome.ResolutionRequired]. */
    fun finishAuthorization(data: Intent): AuthorizationOutcome

    /** Evicts an invalid short-lived token so the next authorization reaches the server. */
    suspend fun clearCachedToken(token: EphemeralAccessToken): Boolean

    /** Revokes the app's Google-data grant for the selected account. */
    suspend fun revoke(account: Account): Boolean
}

class PlayServicesGoogleAuthorizationGateway(
    context: Context,
    private val client: AuthorizationClient =
        Identity.getAuthorizationClient(context.applicationContext),
) : GoogleAuthorizationGateway {
    private val driveFileScope = Scope(BuildConfig.GOOGLE_DRIVE_SCOPE)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(driveFileScope))
        .build()

    override suspend fun authorizeSilently(): AuthorizationOutcome =
        try {
            client.authorize(request).await().toOutcome()
        } catch (error: ApiException) {
            AuthorizationOutcome.Unavailable(
                code = "GOOGLE_AUTH_${error.statusCode}",
                isTransient = error.statusCode in TRANSIENT_GOOGLE_STATUS_CODES,
            )
        } catch (_: IOException) {
            AuthorizationOutcome.Unavailable(
                code = "GOOGLE_AUTH_NETWORK",
                isTransient = true,
            )
        } catch (_: RuntimeException) {
            AuthorizationOutcome.Unavailable(
                code = "GOOGLE_AUTH_UNAVAILABLE",
                isTransient = false,
            )
        }

    override fun finishAuthorization(data: Intent): AuthorizationOutcome =
        try {
            client.getAuthorizationResultFromIntent(data).toOutcome()
        } catch (error: ApiException) {
            AuthorizationOutcome.Unavailable(
                code = "GOOGLE_AUTH_${error.statusCode}",
                isTransient = error.statusCode in TRANSIENT_GOOGLE_STATUS_CODES,
            )
        } catch (_: RuntimeException) {
            AuthorizationOutcome.Unavailable(
                code = "GOOGLE_AUTH_RESULT_INVALID",
                isTransient = false,
            )
        }

    override suspend fun clearCachedToken(token: EphemeralAccessToken): Boolean =
        try {
            client.clearToken(
                ClearTokenRequest.builder()
                    .setToken(token.raw)
                    .build(),
            ).await()
            true
        } catch (_: Exception) {
            false
        }

    override suspend fun revoke(account: Account): Boolean =
        try {
            client.revokeAccess(
                RevokeAccessRequest.builder()
                    .setAccount(account)
                    .setScopes(listOf(driveFileScope))
                    .build(),
            ).await()
            true
        } catch (_: Exception) {
            false
        }

    private fun AuthorizationResult.toOutcome(): AuthorizationOutcome {
        if (hasResolution()) {
            val resolution = pendingIntent
                ?: return AuthorizationOutcome.Unavailable(
                    code = "GOOGLE_AUTH_MISSING_RESOLUTION",
                    isTransient = false,
                )
            return AuthorizationOutcome.ResolutionRequired(resolution)
        }

        val granted = grantedScopes.orEmpty().toSet()
        if (BuildConfig.GOOGLE_DRIVE_SCOPE !in granted) {
            return AuthorizationOutcome.Unavailable(
                code = "DRIVE_FILE_SCOPE_NOT_GRANTED",
                isTransient = false,
            )
        }

        val value = accessToken
        if (value.isNullOrBlank()) {
            return AuthorizationOutcome.Unavailable(
                code = "GOOGLE_AUTH_TOKEN_MISSING",
                isTransient = false,
            )
        }

        return AuthorizationOutcome.Authorized(
            token = EphemeralAccessToken(value),
            account = toGoogleSignInAccount()?.account,
            grantedScopes = granted,
        )
    }

    private companion object {
        // CommonStatusCodes.NETWORK_ERROR, INTERNAL_ERROR, and TIMEOUT. Keeping the values
        // local avoids coupling the domain result to Play services constants.
        val TRANSIENT_GOOGLE_STATUS_CODES = setOf(7, 8, 15)
    }
}
