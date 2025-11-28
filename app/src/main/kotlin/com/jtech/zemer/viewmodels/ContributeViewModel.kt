package com.jtech.zemer.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

data class ContributeArtist(
    val docId: String,
    val artistId: String,
    val artistName: String,
    val imageUrl: String?,
    val isFemale: Boolean,
    val isChasid: Boolean,
    val isGenZ: Boolean
)

data class ContributeUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val requireProfile: Boolean = false,
    val isBanned: Boolean = false,
    val nameInput: String = "",
    val phoneInput: String = "",
    val currentArtist: ContributeArtist? = null,
    val progress: Pair<Int, Int>? = null,
    val message: String? = null,
    val error: String? = null
)

@HiltViewModel
class ContributeViewModel @Inject constructor() : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(ContributeUiState())
    val uiState: StateFlow<ContributeUiState> = _uiState.asStateFlow()

    init {
        auth.addAuthStateListener {
            viewModelScope.launch(Dispatchers.IO) {
                handleAuthChange()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            handleAuthChange()
        }
    }

    private suspend fun handleAuthChange() {
        val user = auth.currentUser
        if (user == null) {
            _uiState.value = ContributeUiState()
            return
        }

        _uiState.update { it.copy(isSignedIn = true, isLoading = true, error = null, message = null) }

        val userDoc = firestore.collection("users").document(user.uid).get().await()
        val isBanned = userDoc.getBoolean("isBanned") == true
        if (isBanned) {
            auth.signOut()
            _uiState.value = ContributeUiState(
                isSignedIn = false,
                isBanned = true,
                error = "Your account is banned from contributing."
            )
            return
        }

        val name = userDoc.getString("name").orEmpty()
        val phone = userDoc.getString("phone").orEmpty()
        val requireProfile = name.isBlank() || phone.isBlank()

        _uiState.update {
            it.copy(
                isSignedIn = true,
                isLoading = false,
                requireProfile = requireProfile,
                nameInput = name,
                phoneInput = phone,
                error = null,
                isBanned = false
            )
        }

        if (!requireProfile) {
            loadProgress()
            loadNextArtist()
        }
    }

    fun signOut() {
        auth.signOut()
        _uiState.value = ContributeUiState()
    }

    fun setError(message: String?) {
        // Swallow common cancellation text to avoid scaring the user
        val clean = when (message?.trim()) {
            null, "", "Activity is cancelled by the user." -> null
            else -> message
        }
        _uiState.update { it.copy(error = clean, isLoading = false) }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
                handleAuthChange()
            } catch (e: Exception) {
                Timber.e(e, "Contribute: sign-in failed")
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Sign-in failed") }
            }
        }
    }

    fun saveProfile(name: String, phone: String) {
        val user = auth.currentUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                firestore.collection("users").document(user.uid).set(
                    mapOf(
                        "name" to name,
                        "phone" to phone,
                        "isBanned" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        requireProfile = false,
                        nameInput = name,
                        phoneInput = phone,
                        message = "Profile saved"
                    )
                }
                loadProgress()
                loadNextArtist()
            } catch (e: Exception) {
                Timber.e(e, "Contribute: save profile failed")
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to save profile") }
            }
        }
    }

    fun submitContribution(isFemale: Boolean, isChasid: Boolean, isGenZ: Boolean) {
        val user = auth.currentUser ?: return
        val artist = _uiState.value.currentArtist ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true, error = null, message = null) }

                val docRef = firestore.collection("artistsWhitelist").document(artist.docId)
                docRef.update(
                    mapOf(
                        "isFemale" to isFemale,
                        "isChasid" to isChasid,
                        "isGenZ" to isGenZ,
                        "isVerified" to true,
                        "verifiedBy" to user.uid,
                        "verifiedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                _uiState.update {
                    it.copy(
                        message = "Submitted for ${artist.artistName}",
                        currentArtist = null
                    )
                }
                loadProgress()
                loadNextArtist()
            } catch (e: Exception) {
                Timber.e(e, "Contribute: submit failed")
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Submit failed") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshTask() {
        viewModelScope.launch(Dispatchers.IO) {
            loadProgress()
            loadNextArtist()
        }
    }

    private suspend fun loadProgress() {
        try {
            val total = firestore.collection("artistsWhitelist").get().await().size()
            val verified = firestore.collection("artistsWhitelist")
                .whereEqualTo("isVerified", true)
                .get()
                .await()
                .size()
            _uiState.update { it.copy(progress = verified to total) }
        } catch (e: Exception) {
            Timber.w(e, "Contribute: progress load failed")
        }
    }

    private suspend fun loadNextArtist() {
        try {
            _uiState.update { it.copy(isLoading = true, currentArtist = null, error = null) }
            val baseQuery = firestore.collection("artistsWhitelist")
            val unverifiedSnapshot = baseQuery
                .whereEqualTo("isVerified", false)
                .whereEqualTo("whitelisted", true)
                .limit(50)
                .get()
                .await()
            val random = Random(System.currentTimeMillis())
            val shuffledUnverified = unverifiedSnapshot.documents.shuffled(random)

            var doc = shuffledUnverified.firstOrNull()
            if (doc == null) {
                // Fallback: pick any doc that is not explicitly verified
                val anySnapshot = baseQuery
                    .whereEqualTo("whitelisted", true)
                    .limit(50)
                    .get()
                    .await()
                doc = anySnapshot.documents.shuffled(random).firstOrNull { snap ->
                    snap.getBoolean("isVerified") != true
                }
            }
            if (doc == null) {
                _uiState.update { it.copy(isLoading = false, currentArtist = null, message = "All artists are verified") }
                return
            }

            val fields = doc.data.orEmpty()
            val isVerified = (fields["isVerified"] as? Boolean) ?: false
            val artist = ContributeArtist(
                docId = doc.id,
                artistId = (fields["id"] ?: fields["artistId"] ?: doc.id).toString(),
                artistName = (fields["name"] ?: fields["artistName"] ?: "Unknown") as String,
                imageUrl = (fields["thumbnail"] ?: fields["image"] ?: fields["imageUrl"])?.toString(),
                isFemale = (fields["isFemale"] as? Boolean) ?: false,
                isChasid = (fields["isChasid"] as? Boolean) ?: false,
                isGenZ = (fields["isGenZ"] as? Boolean) ?: false
            )
            if (isVerified) {
                _uiState.update { it.copy(isLoading = false, currentArtist = null, message = "All artists are verified") }
                return
            }
            _uiState.update { it.copy(isLoading = false, currentArtist = artist, message = null) }
        } catch (e: Exception) {
            Timber.e(e, "Contribute: load task failed")
            _uiState.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Failed to load task") }
        }
    }
}
