package com.shieldtechhub.shieldkids.common.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

/**
 * Centralized helpers for Firestore real-time listeners and offline-aware updates.
 * Minimal surface to keep implementation localized and reusable.
 */
object FirestoreSyncManager {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    data class ChildSummary(
        val id: String,
        val name: String,
        val yearOfBirth: Long,
        val profileImageUri: String
    )

    /**
     * Listen to all children belonging to the given parent, emitting updates in real-time.
     * onUpdate receives: (children, isFromCache)
     */
    fun listenParentChildren(
        parentUid: String,
        onUpdate: (List<ChildSummary>, Boolean) -> Unit
    ): ListenerRegistration {
        return firestore
            .collection("children")
            .whereEqualTo("parent.docID", parentUid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshots, error ->
                if (error != null) {
                    // In case of error, surface empty list but preserve cache state if possible
                    val isFromCache = snapshots?.metadata?.isFromCache ?: false
                    onUpdate(emptyList(), isFromCache)
                    return@addSnapshotListener
                }

                val isFromCache = snapshots?.metadata?.isFromCache ?: false
                val children = snapshots?.documents?.map { doc ->
                    val data = doc.data ?: emptyMap<String, Any>()
                    val name = data["name"] as? String ?: ""
                    val birthYear = (data["birthYear"] as? Number)?.toLong() ?: 0L
                    val profileImageUri = data["profileImageUri"] as? String ?: ""
                    ChildSummary(
                        id = doc.id,
                        name = name,
                        yearOfBirth = birthYear,
                        profileImageUri = profileImageUri
                    )
                } ?: emptyList()

                onUpdate(children, isFromCache)
            }
    }

    /**
     * Listen to a specific child's document for device map changes.
     * onUpdate receives: (devicesMap, isFromCache)
     */
    fun listenChildDevices(
        childId: String,
        onUpdate: (HashMap<String, Any>, Boolean) -> Unit
    ): ListenerRegistration {
        return firestore
            .collection("children")
            .document(childId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    val isFromCache = snapshot?.metadata?.isFromCache ?: false
                    onUpdate(HashMap(), isFromCache)
                    return@addSnapshotListener
                }

                val isFromCache = snapshot?.metadata?.isFromCache ?: false
                val devices = (snapshot?.get("devices") as? HashMap<String, Any>) ?: HashMap()
                onUpdate(devices, isFromCache)
            }
    }
}


