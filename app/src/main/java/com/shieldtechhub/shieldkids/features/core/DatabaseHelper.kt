package com.shieldtechhub.shieldkids.features.core

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot

/**
 * Helper class for working with the ShieldKids database structure
 */
class DatabaseHelper {
    companion object {
        private val auth: FirebaseAuth = FirebaseAuth.getInstance()
        private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

        /**
         * Get current user's data from the users collection
         */
        fun getCurrentUserData(onSuccess: (DocumentSnapshot) -> Unit, onFailure: (Exception) -> Unit) {
            val user = auth.currentUser
            if (user != null) {
                firestore.collection("users").document(user.uid)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            onSuccess(document)
                        } else {
                            onFailure(Exception("User document not found"))
                        }
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception)
                    }
            } else {
                onFailure(Exception("No user logged in"))
            }
        }

        /**
         * Get current user's parent profile from the parents collection
         */
        fun getCurrentParentProfile(onSuccess: (DocumentSnapshot) -> Unit, onFailure: (Exception) -> Unit) {
            val user = auth.currentUser
            if (user != null) {
                // Find parent document where name field equals user.uid
                firestore.collection("parents")
                    .whereEqualTo("name", user.uid)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!querySnapshot.isEmpty) {
                            val parentDoc = querySnapshot.documents[0]
                            onSuccess(parentDoc)
                        } else {
                            onFailure(Exception("Parent profile not found"))
                        }
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception)
                    }
            } else {
                onFailure(Exception("No user logged in"))
            }
        }

        /**
         * Add a child to the current parent's children array
         */
        fun addChildToParent(childId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
            val user = auth.currentUser
            if (user != null) {
                firestore.collection("parents")
                    .whereEqualTo("name", user.uid)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!querySnapshot.isEmpty) {
                            val parentDoc = querySnapshot.documents[0]
                            val currentChildren = parentDoc.get("children") as? List<String> ?: listOf()
                            val updatedChildren = currentChildren + childId
                            
                            parentDoc.reference.update("children", updatedChildren)
                                .addOnSuccessListener {
                                    onSuccess()
                                }
                                .addOnFailureListener { exception ->
                                    onFailure(exception)
                                }
                        } else {
                            onFailure(Exception("Parent profile not found"))
                        }
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception)
                    }
            } else {
                onFailure(Exception("No user logged in"))
            }
        }

        /**
         * Remove a child from the current parent's children array
         */
        fun removeChildFromParent(childId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
            val user = auth.currentUser
            if (user != null) {
                firestore.collection("parents")
                    .whereEqualTo("name", user.uid)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!querySnapshot.isEmpty) {
                            val parentDoc = querySnapshot.documents[0]
                            val currentChildren = parentDoc.get("children") as? List<String> ?: listOf()
                            val updatedChildren = currentChildren.filter { it != childId }
                            
                            parentDoc.reference.update("children", updatedChildren)
                                .addOnSuccessListener {
                                    onSuccess()
                                }
                                .addOnFailureListener { exception ->
                                    onFailure(exception)
                                }
                        } else {
                            onFailure(Exception("Parent profile not found"))
                        }
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception)
                    }
            } else {
                onFailure(Exception("No user logged in"))
            }
        }

        /**
         * Get all children for the current parent
         */
        fun getParentChildren(onSuccess: (List<String>) -> Unit, onFailure: (Exception) -> Unit) {
            getCurrentParentProfile(
                onSuccess = { parentDoc ->
                    val children = parentDoc.get("children") as? List<String> ?: listOf()
                    onSuccess(children)
                },
                onFailure = onFailure
            )
        }

        /**
         * Update user's name in the users collection
         */
        fun updateUserName(newName: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
            val user = auth.currentUser
            if (user != null) {
                firestore.collection("users").document(user.uid)
                    .update("name", newName)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener { exception ->
                        onFailure(exception)
                    }
            } else {
                onFailure(Exception("No user logged in"))
            }
        }
    }
}
