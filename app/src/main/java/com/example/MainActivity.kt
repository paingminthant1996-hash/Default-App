package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.auth0.android.Auth0
import com.auth0.android.authentication.AuthenticationException
import com.auth0.android.callback.Callback
import com.auth0.android.provider.WebAuthProvider
import com.auth0.android.result.Credentials
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// JWT Helper to extract profile claims safely from identity token without dynamic JSON parsers
fun parseJwtClaim(jwt: String?, claim: String): String? {
    if (jwt == null) return null
    try {
        val parts = jwt.split(".")
        if (parts.size >= 2) {
            val payloadBytes = android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT)
            val payload = String(payloadBytes, Charsets.UTF_8)
            val regex = "\"$claim\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            return regex.find(payload)?.groupValues?.get(1)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

// Auth State holder
data class AuthState(
    val isAuthenticated: Boolean = false,
    val userName: String? = null,
    val userEmail: String? = null,
    val userPicture: String? = null,
    val accessToken: String? = null,
    val idToken: String? = null,
    val refreshToken: String? = null,
    val error: String? = null
)

// Main ViewModel
class MainViewModel : ViewModel() {
    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun updateCredentials(credentials: Credentials) {
        val idToken = credentials.idToken
        val name = parseJwtClaim(idToken, "name") ?: "Authenticated User"
        val email = parseJwtClaim(idToken, "email") ?: "No email associated"
        val picture = parseJwtClaim(idToken, "picture")

        _authState.update {
            AuthState(
                isAuthenticated = true,
                userName = name,
                userEmail = email,
                userPicture = picture,
                accessToken = credentials.accessToken,
                idToken = idToken,
                refreshToken = credentials.refreshToken,
                error = null
            )
        }
    }

    fun setError(errorMessage: String) {
        _authState.update {
            it.copy(error = errorMessage)
        }
    }

    fun clearError() {
        _authState.update {
            it.copy(error = null)
        }
    }

    fun logout() {
        _authState.value = AuthState()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Auth0ShowcaseScreen(
                        modifier = Modifier.padding(innerPadding),
                        activity = this
                    )
                }
            }
        }
    }
}

@Composable
fun Auth0VisualLogo() {
    Canvas(modifier = Modifier.size(68.dp)) {
        val width = size.width
        val height = size.height
        // Draws precise Auth0 abstract modular orange-red badge
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(width / 2f, 4f)
            lineTo(width - 4f, height * 0.25f)
            lineTo(width - 4f, height * 0.65f)
            quadraticTo(width / 2f, height - 2f, width / 2f, height - 2f)
            quadraticTo(4f, height * 0.65f, 4f, height * 0.65f)
            lineTo(4f, height * 0.25f)
            close()
        }
        drawPath(
            path = path,
            color = Color(0xFFEB5424),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
        // Inner security line details
        val innerPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(width / 2f, height * 0.28f)
            lineTo(width * 0.72f, height * 0.38f)
            lineTo(width * 0.72f, height * 0.6f)
            quadraticTo(width / 2f, height * 0.78f, width / 2f, height * 0.78f)
            quadraticTo(width * 0.28f, height * 0.6f, width * 0.28f, height * 0.6f)
            lineTo(width * 0.28f, height * 0.38f)
            close()
        }
        drawPath(
            path = innerPath,
            color = Color.White,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun Auth0ShowcaseScreen(
    modifier: Modifier = Modifier,
    activity: ComponentActivity,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Config parameters from string resources
    val resourceDomain = stringResource(id = R.string.com_auth0_domain)
    val resourceClientId = stringResource(id = R.string.com_auth0_client_id)
    val resourceScheme = stringResource(id = R.string.com_auth0_scheme)
    var isConfigExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(authState.error) {
        authState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Auth0VisualLogo()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Auth0 Secure Login",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Single Sign-On and Token Exchange Showcase",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedContent(
            targetState = authState.isAuthenticated,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 3 }).togetherWith(fadeOut() + slideOutVertically { -it / 3 })
            },
            label = "view_state_transit"
        ) { isAuthenticated ->
            if (!isAuthenticated) {
                // Unauthenticated block
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = "Security Badge",
                                    tint = Color(0xFFEB5424),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Ready to Authenticate",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Launches dynamic Chrome Custom Tabs inside the Android environment to securely authenticate through Auth0's Universal Login. Configured for PKCE verification.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Credential detail area
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isConfigExpanded = !isConfigExpanded }
                                    .padding(vertical = 4.dp)
                                    .testTag("btn_toggle_config"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "App ParametersInfo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Active Tenant Credentials",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isConfigExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Expand/Collapse parameters",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = isConfigExpanded) {
                                Column(
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .fillMaxWidth()
                                ) {
                                    StaticPropCard(label = "Auth0 Domain Connection", value = resourceDomain, icon = Icons.Default.Language)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    StaticPropCard(label = "Application Client ID", value = resourceClientId, icon = Icons.Default.Key)
                                    
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Note: Parameters are loaded directly from '/app/src/main/res/values/strings.xml'. To test your own active tenant, simply edit those fields in the strings resource.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Solid connection button
                    Button(
                        onClick = {
                            if (resourceDomain.contains("dev-example.us.auth0.com") || resourceClientId.contains("YOUR_CLIENT_ID")) {
                                viewModel.setError("Authentication placeholder detected. Please update strings.xml with your authentic tenant domain and clientId to test live connections.")
                                isConfigExpanded = true
                                return@Button
                            }

                            try {
                                val auth0Account = Auth0.getInstance(resourceClientId, resourceDomain)
                                WebAuthProvider.login(auth0Account)
                                    .withScheme(resourceScheme)
                                    .withScope("openid profile email offline_access")
                                    .start(activity, object : Callback<Credentials, AuthenticationException> {
                                        override fun onSuccess(result: Credentials) {
                                            viewModel.updateCredentials(result)
                                        }

                                        override fun onFailure(error: AuthenticationException) {
                                            val reason = if (error.isCanceled) {
                                                "Login process was cancelled by the user."
                                            } else {
                                                error.getDescription()
                                            }
                                            viewModel.setError(reason)
                                        }
                                    })
                            } catch (e: Exception) {
                                e.printStackTrace()
                                viewModel.setError("Unable to launch browser login: ${e.localizedMessage ?: "Please ensure a secure browser is installed."}")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEB5424),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(4.dp, RoundedCornerShape(28.dp))
                            .testTag("btn_lock_login"),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LockOpen,
                                contentDescription = "Lock icon",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Launch Universal Login",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            } else {
                // Authenticated layout
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(24.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .shadow(2.dp, CircleShape)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarUrl = authState.userPicture
                                if (!avatarUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "User profile picture",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    val firstInitial = authState.userName?.firstOrNull()?.uppercase() ?: "?"
                                    Text(
                                        text = firstInitial,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Text(
                                text = authState.userName ?: "OAuth Authenticated",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = authState.userEmail ?: "No email parsed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            AssistChip(
                                onClick = {},
                                label = { Text("Securely Authenticated") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Authenticated Badge",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Token details block
                    var isTokensVisible by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isTokensVisible = !isTokensVisible }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Code,
                                        contentDescription = "Token Code",
                                        tint = Color(0xFFEB5424),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Cryptographic Payload Tokens",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (isTokensVisible) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Expand Token Payload details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = isTokensVisible) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    TokenRow(
                                        label = "Subject Authority ID",
                                        token = authState.idToken?.let { parseJwtClaim(it, "sub") } ?: "None",
                                        context = context
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TokenRow(
                                        label = "ID Token (JWT)",
                                        token = authState.idToken ?: "",
                                        context = context
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TokenRow(
                                        label = "Access Token",
                                        token = authState.accessToken ?: "",
                                        context = context
                                    )
                                    if (!authState.refreshToken.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        TokenRow(
                                            label = "Refresh Token (Offline)",
                                            token = authState.refreshToken ?: "",
                                            context = context
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Log out button
                    OutlinedButton(
                        onClick = {
                            try {
                                val auth0Account = Auth0.getInstance(resourceClientId, resourceDomain)
                                WebAuthProvider.logout(auth0Account)
                                    .withScheme(resourceScheme)
                                    .start(activity, object : Callback<Void?, AuthenticationException> {
                                        override fun onSuccess(result: Void?) {
                                            viewModel.logout()
                                            Toast.makeText(context, "Local Session and Cookies Cleared", Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onFailure(error: AuthenticationException) {
                                            viewModel.logout()
                                            Toast.makeText(context, "Session cleared locally: ${error.getDescription()}", Toast.LENGTH_SHORT).show()
                                        }
                                    })
                            } catch (e: Exception) {
                                e.printStackTrace()
                                viewModel.logout()
                                Toast.makeText(context, "Session cleared locally: browser not found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(28.dp))
                            .testTag("btn_lock_logout"),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Log Out",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign Out & End Session",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun StaticPropCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TokenRow(
    label: String,
    token: String,
    context: Context
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isExpanded) token else if (token.length > 40) token.take(38) + "..." else token,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    maxLines = if (isExpanded) 12 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(label, token)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy text snippet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (token.length > 40) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Expand visibility",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
