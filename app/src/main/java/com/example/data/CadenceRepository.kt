package com.example.data

import android.content.Context
import com.example.R
import com.example.model.FocusSession
import com.example.model.TaskCategory
import com.example.model.TaskItem
import com.example.model.TaskPriority
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CadenceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSignedInUid(expectedUserId: String): String {
        val currentUid = auth.currentUser?.uid
        check(!currentUid.isNullOrBlank()) {
            "User must be signed in with Google before accessing Firestore."
        }
        check(currentUid == expectedUserId) {
            "Signed-in user changed; refusing cross-user Firestore access."
        }
        return currentUid
    }

    fun observeTasks(userId: String): Flow<List<TaskItem>> = flow {
        val uid = requireSignedInUid(userId)
        val path = "users/$uid/tasks"
        emitAll(
            db.collection("users").document(uid).collection("tasks")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TaskItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    fun observeFocusSessions(userId: String): Flow<List<FocusSession>> = flow {
        val uid = requireSignedInUid(userId)
        val path = "users/$uid/focusSessions"
        emitAll(
            db.collection("users").document(uid).collection("focusSessions")
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FocusSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    suspend fun createTask(
        userId: String,
        title: String,
        notes: String,
        category: TaskCategory,
        priority: TaskPriority
    ): Result<Unit> {
        var docPath = "users/$userId/tasks"
        return try {
            val uid = requireSignedInUid(userId)
            val cleanTitle = title.trim()
            val cleanNotes = notes.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 120) {
                "Title must be between 1 and 120 characters."
            }
            require(cleanNotes.length <= 500) {
                "Notes must be 500 characters or fewer."
            }

            val docRef = db.collection("users").document(uid).collection("tasks").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "notes" to cleanNotes,
                "category" to category.name,
                "priority" to priority.name,
                "completed" to false,
                "focusMinutes" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        userId: String,
        taskId: String,
        title: String,
        notes: String,
        category: TaskCategory,
        priority: TaskPriority,
        completed: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireSignedInUid(userId)
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            val cleanTitle = title.trim()
            val cleanNotes = notes.trim()
            require(cleanTitle.isNotEmpty() && cleanTitle.length <= 120) {
                "Title must be between 1 and 120 characters."
            }
            require(cleanNotes.length <= 500) {
                "Notes must be 500 characters or fewer."
            }

            db.collection("users").document(uid).collection("tasks").document(taskId)
                .update(
                    mapOf(
                        "title" to cleanTitle,
                        "notes" to cleanNotes,
                        "category" to category.name,
                        "priority" to priority.name,
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTaskCompleted(userId: String, task: TaskItem): Result<Unit> {
        val docPath = "users/$userId/tasks/${task.id}"
        return try {
            val uid = requireSignedInUid(userId)
            require(task.id.isNotBlank()) { "Task ID must not be blank." }
            db.collection("users").document(uid).collection("tasks").document(task.id)
                .update(
                    mapOf(
                        "completed" to !task.completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(userId: String, taskId: String): Result<Unit> {
        val docPath = "users/$userId/tasks/$taskId"
        return try {
            val uid = requireSignedInUid(userId)
            require(taskId.isNotBlank()) { "Task ID must not be blank." }
            db.collection("users").document(uid).collection("tasks").document(taskId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordFocusSession(
        userId: String,
        task: TaskItem?,
        durationMinutes: Int,
        fallbackTitle: String = "General Focus"
    ): Result<Unit> {
        var logPath = "users/$userId/focusSessions"
        return try {
            val uid = requireSignedInUid(userId)
            require(durationMinutes in 1..180) {
                "Focus duration must be between 1 and 180 minutes."
            }
            val sessionRef = db.collection("users").document(uid).collection("focusSessions").document()
            logPath = sessionRef.path

            val safeTitle = (task?.title?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackTitle).take(120)
            val category = task?.categoryEnum ?: TaskCategory.WORK
            val linkedTaskId = task?.id?.takeIf { it.isNotBlank() } ?: ""

            val sessionPayload = mapOf(
                "userId" to uid,
                "taskId" to linkedTaskId,
                "taskTitle" to safeTitle,
                "durationMinutes" to durationMinutes.toLong(),
                "category" to category.name,
                "completedAt" to FieldValue.serverTimestamp()
            )

            if (linkedTaskId.isNotEmpty()) {
                val taskRef = db.collection("users").document(uid).collection("tasks").document(linkedTaskId)
                val batch = db.batch()
                batch.set(sessionRef, sessionPayload)
                batch.update(
                    taskRef,
                    mapOf(
                        "focusMinutes" to FieldValue.increment(durationMinutes.toLong()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            } else {
                sessionRef.set(sessionPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, logPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFocusSession(userId: String, sessionId: String): Result<Unit> {
        val docPath = "users/$userId/focusSessions/$sessionId"
        return try {
            val uid = requireSignedInUid(userId)
            require(sessionId.isNotBlank()) { "Session ID must not be blank." }
            db.collection("users").document(uid).collection("focusSessions").document(sessionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
