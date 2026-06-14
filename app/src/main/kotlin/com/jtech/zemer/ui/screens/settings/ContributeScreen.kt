package com.jtech.zemer.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.jtech.zemer.LocalPlayerAwareWindowInsets
import com.jtech.zemer.R
import com.jtech.zemer.ui.component.focusBorder
import com.jtech.zemer.viewmodels.ContributeArtist
import com.jtech.zemer.viewmodels.ContributeUiState
import com.jtech.zemer.viewmodels.ContributeViewModel
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ContributeScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ContributeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun launchGoogleSignIn() {
        scope.launch {
            try {
                viewModel.setError("")
                val googleIdOption = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id))
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val response: GetCredentialResponse = credentialManager.getCredential(
                    request = request,
                    context = context
                )
                val credential = response.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    viewModel.signInWithGoogle(googleIdTokenCredential.idToken)
                } else {
                    viewModel.setError("Google sign-in was cancelled or returned no token.")
                }
            } catch (e: GetCredentialCancellationException) {
                // User cancelled; don't show an error, just stay idle
                viewModel.setError(null)
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                viewModel.setError(null)
            } catch (e: GetCredentialException) {
                viewModel.setError(e.localizedMessage ?: "Google sign-in failed.")
            } catch (e: Exception) {
                viewModel.setError(e.localizedMessage ?: "Google sign-in failed.")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contribute)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Only respect bottom insets so the top area stays tight to the app bar.
                .padding(
                    LocalPlayerAwareWindowInsets.current
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        .asPaddingValues()
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                uiState.isBanned -> Text(
                    text = stringResource(R.string.contribute_banned),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )

                !uiState.isSignedIn -> {
                    Text(
                        text = stringResource(R.string.contribute_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    uiState.error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (uiState.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    Button(
                        onClick = { launchGoogleSignIn() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(stringResource(R.string.contribute_sign_in))
                    }
                }

                uiState.requireProfile -> ProfilePrompt(
                    uiState,
                    onSave = { name, phone -> viewModel.saveProfile(name, phone) }
                )

                else -> {
                    ProgressRow(uiState)

                    uiState.message?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    uiState.error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (uiState.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.currentArtist?.let { artist ->
                        ArtistTaskCard(
                            artist = artist,
                            onOpenArtist = { navController.navigate("artist/${'$'}{artist.artistId}") },
                            onSubmit = { isFemale, isChasid, isGenZ ->
                                viewModel.submitContribution(isFemale, isChasid, isGenZ)
                            },
                            onSkip = { viewModel.refreshTask() }
                        )
                    } ?: Text(
                        text = stringResource(R.string.contribute_no_tasks),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = { viewModel.refreshTask() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.contribute_refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressRow(uiState: ContributeUiState) {
    uiState.progress?.let { (verified, total) ->
        Text(
            text = stringResource(R.string.contribute_progress, verified, total),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun ProfilePrompt(
    uiState: ContributeUiState,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(uiState.nameInput) }
    var phone by rememberSaveable { mutableStateOf(uiState.phoneInput) }
    LaunchedEffect(uiState.nameInput) { name = uiState.nameInput }
    LaunchedEffect(uiState.phoneInput) { phone = uiState.phoneInput }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.contribute_require_profile),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.contribute_name)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text(stringResource(R.string.contribute_phone)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSave(name.trim(), phone.trim()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && phone.isNotBlank()
        ) {
            Text(stringResource(R.string.contribute_save_profile))
        }
    }
}

@Composable
private fun ArtistTaskCard(
    artist: ContributeArtist,
    onOpenArtist: (String) -> Unit,
    onSubmit: (Boolean, Boolean, Boolean) -> Unit,
    onSkip: () -> Unit
) {
    var isFemale by rememberSaveable(artist.docId) { mutableStateOf(artist.isFemale) }
    var isChasid by rememberSaveable(artist.docId) { mutableStateOf(artist.isChasid) }
    var isGenZ by rememberSaveable(artist.docId) { mutableStateOf(artist.isGenZ) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusBorder()
                .clickable { onOpenArtist(artist.artistId) },
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artist.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = artist.artistName,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = MaterialTheme.shapes.small
                        )
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = artist.artistName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Text(
                text = stringResource(R.string.contribute_artist_prompt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ToggleRow(
            title = stringResource(R.string.contribute_is_chasid),
            checked = isChasid,
            onCheckedChange = { isChasid = it }
        )
        ToggleRow(
            title = stringResource(R.string.contribute_is_genz),
            checked = isGenZ,
            onCheckedChange = { isGenZ = it }
        )
        ToggleRow(
            title = stringResource(R.string.contribute_is_female),
            checked = isFemale,
            onCheckedChange = { isFemale = it }
        )
        Button(
            onClick = { onSubmit(isFemale, isChasid, isGenZ) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.contribute_submit))
        }
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.contribute_skip))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
