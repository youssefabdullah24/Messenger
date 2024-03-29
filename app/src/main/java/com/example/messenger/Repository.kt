package com.example.messenger

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioRecord
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.example.messenger.model.Message
import com.example.messenger.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject


@OptIn(ExperimentalCoroutinesApi::class)
class Repository @Inject constructor(
    private val auth: FirebaseAuth,
    private val fireStore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private val TAG = "Repository"
    suspend fun observeDocChanges(friendUid: String) = callbackFlow<DataState<Int>> {
        fireStore.collection(Constants.USER_COLLECTION).document(auth.uid!!)
            .collection(Constants.USER_INBOX_COLLECTION)
            .document(friendUid)
            .collection(Constants.USER_CONVERSATION_COLLECTION)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    trySend(DataState.Error(error))
                    close()
                }
                for (change in value?.documentChanges!!) {
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            trySend(DataState.Success(Constants.DOCUMENT_ADDED))
                        }
                        DocumentChange.Type.MODIFIED -> {
                            trySend(DataState.Success(Constants.DOCUMENT_MODIFIED))
                        }
                        DocumentChange.Type.REMOVED -> {
                            trySend(DataState.Success(Constants.DOCUMENT_REMOVED))
                        }
                    }
                }

            }
        awaitClose { cancel() }
    }

    suspend fun register(userProfile: UserProfile, password: String) =
        callbackFlow<DataState<UserProfile>?> {
            trySend(DataState.Loading)
            auth.createUserWithEmailAndPassword(userProfile.email!!, password)
                .addOnCompleteListener {
                    if (it.isComplete) {
                        when {
                            it.isSuccessful -> {
                                if (userProfile.fname != null && userProfile.lname != null) {
                                    val fullName = userProfile.fname + " " + userProfile.lname
                                    val fullNameArr = fullName.split(' ')
                                    var holder = ""
                                    for (name in fullNameArr) {
                                        for (c in name) {
                                            holder += c
                                            userProfile.searchList.add(holder)
                                        }
                                        userProfile.searchList.add("$holder ")
                                        holder = ""
                                    }
                                    var fullNameHolder = ""
                                    for (fullNameChar in fullName) {
                                        fullNameHolder += fullNameChar
                                        userProfile.searchList.add(fullNameHolder)
                                    }
                                }
                                if (userProfile.email != null) {
                                    userProfile.searchList.add(userProfile.email!!)
                                }


                                val doc = fireStore.collection(Constants.USER_COLLECTION)
                                    .document(auth.currentUser?.uid!!)
                                doc.set(userProfile).addOnCompleteListener { docTask ->
                                    when {
                                        docTask.isSuccessful -> {
                                            trySend(DataState.Success(userProfile))
                                            close()

                                        }
                                        docTask.isCanceled -> {
                                            trySend(DataState.Canceled)
                                            close()

                                        }
                                        docTask.exception != null -> {
                                            trySend(DataState.Error(it.exception!!))
                                            close()

                                        }
                                    }
                                }
                            }
                            it.isCanceled -> {
                                trySend(DataState.Canceled)
                                close()

                            }
                            it.exception != null -> {
                                trySend(DataState.Error(it.exception!!))
                                close()

                            }
                        }
                    }
                }
            awaitClose { cancel() }
        }.flowOn(Dispatchers.IO)


    fun getCurrentUser(): FirebaseUser? {
        auth.currentUser?.reload()
        return auth.currentUser
    }

    suspend fun login(email: String, password: String) = callbackFlow<DataState<Int>> {
        trySend(DataState.Loading)
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { signInTask ->
            when {
                signInTask.isSuccessful -> {
                    trySend(DataState.Success(Constants.LOGIN_SUCCESSFUL))
                    close()

                }
                signInTask.isCanceled -> {
                    trySend(DataState.Canceled)
                    close()

                }
                signInTask.exception != null -> {
                    when (signInTask.exception) {
                        is FirebaseAuthInvalidCredentialsException -> {
                            trySend(DataState.Invalid(Constants.LOGIN_INVALID_CREDENTIALS))
                            close()

                        }
                        is FirebaseAuthInvalidUserException -> {
                            trySend(DataState.Invalid(Constants.LOGIN_NO_USER))
                            close()

                        }
                        else -> {
                            trySend(DataState.Error(signInTask.exception!!))
                            close()

                        }
                    }
                }
            }

        }
        awaitClose { cancel() }

    }.flowOn(Dispatchers.IO)


    suspend fun userProfileByUId(uId: String) = callbackFlow<DataState<UserProfile?>> {
        fireStore.collection(Constants.USER_COLLECTION).document(uId).get()
            .addOnCompleteListener { documentSnapshot ->
                trySend(DataState.Loading)
                when {
                    documentSnapshot.isSuccessful -> {
                        val profile = documentSnapshot.result?.toObject(UserProfile::class.java)
                        trySend(DataState.Success(profile))
                        close()

                    }
                    documentSnapshot.isCanceled -> {
                        trySend(DataState.Canceled)
                        close()

                    }
                    documentSnapshot.exception != null -> {
                        trySend(DataState.Error(documentSnapshot.exception!!))
                        close()

                    }
                }
            }
        awaitClose { cancel() }
    }.flowOn(Dispatchers.IO)

    fun getAuth() = auth

    suspend fun sendVerificationEmail(currentUser: FirebaseUser?) = callbackFlow<DataState<Int>> {
        currentUser?.let {
            currentUser.sendEmailVerification().addOnCompleteListener {
                when {
                    it.isSuccessful -> {
                        trySend(DataState.Success(Constants.VERIFICATION_EMAIL_SENT_SUCCESS))
                        close()

                    }
                    it.exception != null -> {
                        trySend(DataState.Error(it.exception!!))
                        close()

                    }
                }
            }
        }
        awaitClose { cancel() }
    }.flowOn(Dispatchers.IO)

    fun getDocRef(collectionName: String, documentName: String) =
        fireStore.collection(collectionName).document(documentName)


    suspend fun addToFriendList(collectionName: String, email: String) =
        callbackFlow<DataState<Int>> {
            fireStore.collection(collectionName)
                .whereEqualTo(Constants.FIELD_EMAIL, email)
                .get()
                .addOnCompleteListener { task ->
                    when {
                        task.isSuccessful -> {
                            if (task.result?.documents?.size == 1) {
                                task.result?.documents?.forEach { documentSnapshot ->
                                    if (documentSnapshot.exists()) {
                                        val uId = documentSnapshot.id
                                        val currentUserId = auth.currentUser?.uid!!

                                        fireStore.collection(Constants.USER_COLLECTION)
                                            .document(currentUserId).get()
                                            .addOnCompleteListener { currentUserDocSnapShot ->

                                                when {
                                                    currentUserDocSnapShot.isSuccessful -> {
                                                        val currentUserProfile =
                                                            currentUserDocSnapShot.result?.toObject(
                                                                UserProfile::class.java
                                                            )

                                                        if (currentUserProfile != null) {
                                                            if (!currentUserProfile.friendsList.contains(
                                                                    uId
                                                                )
                                                            ) {
                                                                currentUserDocSnapShot.result?.reference?.update(
                                                                    Constants.FIELD_FRIENDS_COUNT,
                                                                    currentUserProfile.friendsCount?.plus(
                                                                        1
                                                                    )
                                                                )
                                                                    ?.addOnCompleteListener { friendsCountTask ->
                                                                        when {
                                                                            friendsCountTask.isSuccessful -> {
                                                                                currentUserProfile.friendsList.add(
                                                                                    uId
                                                                                )
                                                                                currentUserDocSnapShot.result?.reference?.update(
                                                                                    Constants.FIELD_FRIENDS_LIST,
                                                                                    currentUserProfile.friendsList
                                                                                )
                                                                                    ?.addOnCompleteListener { friendsListTask ->
                                                                                        when {
                                                                                            friendsListTask.isSuccessful -> {
                                                                                                trySend(
                                                                                                    DataState.Success(
                                                                                                        Constants.FRIEND_ADDITION_SUCCESS
                                                                                                    )
                                                                                                )
                                                                                                close()
                                                                                            }
                                                                                            friendsListTask.isCanceled -> {
                                                                                                trySend(
                                                                                                    DataState.Canceled
                                                                                                )
                                                                                                close()

                                                                                            }
                                                                                            friendsListTask.exception != null -> {
                                                                                                trySend(
                                                                                                    DataState.Error(
                                                                                                        friendsCountTask.exception!!
                                                                                                    )
                                                                                                )
                                                                                                close()

                                                                                            }
                                                                                        }
                                                                                    }
                                                                            }
                                                                            friendsCountTask.isCanceled -> {
                                                                                trySend(DataState.Canceled)
                                                                                close()

                                                                            }
                                                                            friendsCountTask.exception != null -> {
                                                                                trySend(
                                                                                    DataState.Error(
                                                                                        friendsCountTask.exception!!
                                                                                    )
                                                                                )
                                                                                close()

                                                                            }
                                                                        }
                                                                    }
                                                            }
                                                        }
                                                    }
                                                    currentUserDocSnapShot.isCanceled -> {
                                                        trySend(DataState.Canceled)
                                                        close()

                                                    }
                                                    currentUserDocSnapShot.exception != null -> {
                                                        trySend(
                                                            DataState.Error(
                                                                currentUserDocSnapShot.exception!!
                                                            )
                                                        )
                                                        close()

                                                    }
                                                }
                                            }
                                    } else {
                                        trySend(DataState.Empty)
                                        close()

                                    }
                                }
                            }

                        }
                        task.isCanceled -> {
                            trySend(DataState.Canceled)
                            close()

                        }
                        task.exception != null -> {
                            trySend(DataState.Error(task.exception!!))
                            close()

                        }

                    }
                }
            awaitClose { cancel() }
        }.flowOn(Dispatchers.IO)


    suspend fun uploadImg(uri: Uri, uId: String) = callbackFlow<DataState<Int>> {
        trySend(DataState.Loading)
        val fileRef =
            storage.reference.child(Constants.STORAGE_PROFILE_PICTURE_FOLDER).child(uId)
        val uploadTask = fileRef.putFile(uri)
        uploadTask.addOnProgressListener {
            trySend(DataState.Loading)
        }
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let {
                    throw it
                }
            }
            fileRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                task.addOnCompleteListener { downloadUri ->
                    if (downloadUri.isSuccessful) {
                        val docRef =
                            fireStore.collection(Constants.USER_COLLECTION).document(uId)
                        docRef.get().addOnCompleteListener {
                            docRef.update(
                                Constants.FIELD_PROFILE_PICTURE_URL,
                                downloadUri.result.toString()
                            )
                            docRef.update(
                                Constants.FIELD_PROFILE_CREATION_COMPLETED,
                                true
                            )
                            trySend(DataState.Success(Constants.IMAGE_UPLOAD_SUCCESSFUL))
                            close()

                        }
                    } else if (downloadUri.isCanceled) {
                        trySend(DataState.Canceled)
                        close()

                    } else if (downloadUri.exception != null) {
                        trySend(DataState.Error(downloadUri.exception!!))
                        close()

                    }

                }

            } else if (task.isCanceled) {
                trySend(DataState.Canceled)
                close()

            } else if (task.exception != null) {
                trySend(DataState.Error(task.exception!!))
                close()

            }
        }
        awaitClose { cancel() }
    }.flowOn(Dispatchers.IO)

    fun signOut() {
        auth.signOut()
    }

    fun getDefaultMessageQuery(friendUid: String) =
        fireStore.collection(Constants.USER_COLLECTION)
            .document(auth.currentUser?.uid!!)
            .collection(Constants.USER_INBOX_COLLECTION)
            .document(friendUid)
            .collection(Constants.USER_CONVERSATION_COLLECTION)
            .orderBy(Constants.FIELD_TIMESTAMP, Query.Direction.DESCENDING)


    suspend fun defaultFriendsQuery() = callbackFlow<DataState<List<UserProfile>>> {
        if (auth.uid != null) {
            fireStore.collection(Constants.USER_COLLECTION).document(auth.uid!!).get()
                .addOnCompleteListener {
                    when {
                        it.isSuccessful -> {
                            val currentUser = it.result?.toObject(UserProfile::class.java)
                            if (currentUser != null) {
                                val friendList = currentUser.friendsList
                                if (friendList.size > 0) {
                                    fireStore.collection(Constants.USER_COLLECTION).whereIn(
                                        FieldPath.documentId(),
                                        friendList
                                    ).get().addOnCompleteListener {
                                        val list = it.result?.toObjects(UserProfile::class.java)
                                        trySend(DataState.Success(list!!))
                                        close()

                                    }
                                }
                            }
                        }
                        it.exception != null -> {
                            trySend(DataState.Error(it.exception!!))
                            close()

                        }
                    }
                }
        }
        awaitClose { cancel() }
    }.flowOn(Dispatchers.IO)


    suspend fun defaultMessageQuery() = flow<DataState<Query>> {
        emit(DataState.Loading)
        try {
            // TODO : Edit later
            val query = fireStore.collection(Constants.MESSAGE_COLLECTION).limit(15)
            emit(DataState.Success(query))
        } catch (e: Exception) {
            emit(DataState.Error(e))

        }
    }.flowOn(Dispatchers.IO)

    fun getDefaultUserQuery(): Query =
        fireStore.collection(Constants.USER_COLLECTION)
            .whereNotEqualTo(Constants.FIELD_EMAIL, auth.currentUser?.email)
            .orderBy(Constants.FIELD_EMAIL, Query.Direction.ASCENDING)


    suspend fun defaultUserQuery() = flow<DataState<Query>> {
        emit(DataState.Loading)
        try {
            val query = fireStore.collection(Constants.USER_COLLECTION)
                .whereNotEqualTo(Constants.FIELD_EMAIL, auth.currentUser?.email)
                .orderBy(Constants.FIELD_EMAIL, Query.Direction.ASCENDING)
            emit(DataState.Success(query))
        } catch (e: Exception) {
            emit(DataState.Error(e))
        }
    }.flowOn(Dispatchers.IO)


    suspend fun filterUserQuery(name: String) = flow<DataState<Query>> {
        emit(DataState.Loading)
        try {
            val query = fireStore.collection(Constants.USER_COLLECTION)
                .whereNotEqualTo(Constants.FIELD_EMAIL, auth.currentUser?.email)
                .whereArrayContains(Constants.FIELD_SEARCH_LIST, name)
                .orderBy(Constants.FIELD_EMAIL, Query.Direction.ASCENDING)
            emit(DataState.Success(query))

        } catch (e: Exception) {
            emit(DataState.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun uIdByEmail(email: String) = callbackFlow<DataState<String>> {
        trySend(DataState.Loading)
        fireStore.collection(Constants.USER_COLLECTION)
            .whereEqualTo(Constants.FIELD_EMAIL, email)
            .get()
            .addOnCompleteListener {
                when {
                    it.isSuccessful -> {
                        if (it.result?.size() == 1) {
                            it.result?.documents?.forEach { documentSnapshot ->
                                trySend(DataState.Success(documentSnapshot.id))
                                close()
                            }
                        }
                    }
                    it.isCanceled -> {
                        trySend(DataState.Canceled)
                        close()

                    }
                    it.exception != null -> {
                        trySend(DataState.Error(it.exception!!))
                        close()

                    }
                }


            }
        awaitClose { cancel() }
    }.flowOn(Dispatchers.IO)

    suspend fun sendMessage(message: Message, toUid: String) = callbackFlow<DataState<Message>> {
        trySend(DataState.Loading)
        val myUid = auth.currentUser?.uid
        if (myUid != null) {
            getDocRef(Constants.USER_COLLECTION, myUid)
                .collection(Constants.USER_INBOX_COLLECTION)
                .document(toUid)
                .collection(Constants.USER_CONVERSATION_COLLECTION)
                .document()
                .set(message)
                .addOnCompleteListener { myInboxTask ->
                    when {
                        myInboxTask.isSuccessful -> {
                            getDocRef(Constants.USER_COLLECTION, toUid)
                                .collection(Constants.USER_INBOX_COLLECTION)
                                .document(myUid)
                                .collection(Constants.USER_CONVERSATION_COLLECTION)
                                .document()
                                .set(message)
                                .addOnCompleteListener { friendInboxTask ->
                                    when {
                                        friendInboxTask.isSuccessful -> {
                                            trySend(DataState.Success(message))
                                            close()

                                        }
                                        friendInboxTask.isCanceled -> {
                                            trySend(DataState.Canceled)
                                            close()

                                        }
                                        friendInboxTask.exception != null -> {
                                            trySend(DataState.Error(friendInboxTask.exception!!))
                                            close()

                                        }
                                    }
                                }

                        }
                        myInboxTask.isCanceled -> {
                            trySend(DataState.Canceled)
                            close()

                        }
                        myInboxTask.exception != null -> {
                            trySend(DataState.Error(myInboxTask.exception!!))
                            close()

                        }
                    }
                }.addOnFailureListener { sendMessageException ->
                    trySend(DataState.Error(sendMessageException))
                    close()

                }.addOnCanceledListener {
                    trySend(DataState.Canceled)
                    close()

                }
        }
        awaitClose { cancel() }
    }.flowOn(Dispatchers.IO)

    // TODO : set document, upload image, then set url to document
    suspend fun sendImage(data: Message, imageUri: Uri, friendUid: String) =
        callbackFlow<DataState<Int>> {
            trySend(DataState.Loading)
            val fileNameRandom = System.currentTimeMillis().toString()
            if (auth.uid != null) {
                val fileRef = storage.getReference(Constants.STORAGE_CHAT_UPLOADS_FOLDER)
                    .child(auth.uid!!)
                    .child(friendUid)
                    .child(fileNameRandom)
                fileRef.putFile(imageUri).addOnProgressListener {
                    val progress = (100.0 * it.bytesTransferred) / it.totalByteCount
                    Log.i(TAG, "sendImage: ${progress.toInt()}")
                    trySend(DataState.Progress(progress.toInt()))
                }.continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let {
                            trySend(DataState.Error(it))
                            close(it)
                        }
                    }
                    fileRef.downloadUrl
                }.addOnCompleteListener {
                    when {
                        it.isSuccessful -> {
                            val downloadUri = it.result
                            if (downloadUri != null) {
                                data.imageMessageUrl = downloadUri.toString()
                                getDocRef(Constants.USER_COLLECTION, auth.uid!!)
                                    .collection(Constants.USER_INBOX_COLLECTION)
                                    .document(friendUid)
                                    .collection(Constants.USER_CONVERSATION_COLLECTION)
                                    .document()
                                    .set(data)
                                    .addOnCompleteListener { myInboxTask ->
                                        when {
                                            myInboxTask.isSuccessful -> {
                                                getDocRef(Constants.USER_COLLECTION, friendUid)
                                                    .collection(Constants.USER_INBOX_COLLECTION)
                                                    .document(auth.uid!!)
                                                    .collection(Constants.USER_CONVERSATION_COLLECTION)
                                                    .document()
                                                    .set(data)
                                                    .addOnCompleteListener { friendInboxTask ->
                                                        when {
                                                            friendInboxTask.isSuccessful -> {
                                                                // TODO : separate upload task from document, when upload finishes, set url
                                                                trySend(DataState.Success(Constants.IMAGE_MESSAGE_SUCCESS))
                                                                close()
                                                            }
                                                            it.isCanceled -> {
                                                                trySend(DataState.Canceled)
                                                                close()

                                                            }
                                                            it.exception != null -> {
                                                                trySend(DataState.Error(it.exception!!))
                                                                close(it.exception)

                                                            }
                                                        }
                                                    }

                                            }
                                        }

                                    }
                            }
                        }
                    }
                }
                awaitClose { cancel() }
            }
        }.flowOn(Dispatchers.IO)


    // TODO : 5leha zy el voice...surround with if else SDK Version..
    suspend fun saveImage(imageUrl: String, context: Context) = callbackFlow<DataState<String>> {
        val fileRef = storage.getReferenceFromUrl(imageUrl)

        val ONE_MEGABYTE: Long = 1024L * 1024L

        trySend(DataState.Loading)
        fileRef.getBytes(ONE_MEGABYTE)
            .addOnCanceledListener {
                trySend(DataState.Canceled)
                close()
            }.addOnFailureListener {
                trySend(DataState.Error(it))
                close(it)
            }.addOnSuccessListener {
                val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)

                val imageCollection = Utils.isSdkVer29Up {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileRef.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.WIDTH, bitmap.width)
                    put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                }
                try {
                    context.contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                        context.contentResolver.openOutputStream(uri).use { outputStream ->
                            try {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                                trySend(
                                    DataState.Success(
                                        imageCollection.path!! +
                                                "/" + fileRef.name + ".jpg"
                                    )
                                )
                                close()

                            } catch (e: Exception) {
                                trySend(DataState.Error(e))
                                close(e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    trySend(DataState.Error(e))
                    close(e)
                }
            }

        awaitClose { cancel() }
    }.flowOn(Dispatchers.IO)


    private val isRecordingAtomic = AtomicBoolean(false)
    private val recordingTimeMillis = AtomicLong(0)


    /*   suspend fun downloadRecordingLegacy(
           audioRecord: AudioRecord,
           fileName: String
       ) = flow<DataState<String>> {
           try {
               val file = File(
                   Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                   fileName
               )
               file.outputStream().use { fileOutputStream ->
                   Log.d(TAG, "downloadRecordingLegacy: recording...")
                   emit(DataState.Loading)
                   isRecordingAtomic.set(true)
                   audioRecord.startRecording()
                   val sData = ShortArray(Constants.BufferElements2Rec)
                   while (isRecordingAtomic.get()) {
                       audioRecord.read(sData, 0, Constants.BufferElements2Rec)
                       val bData = Utils.shortArrayToByteArray(sData)
                       fileOutputStream.write(
                           bData,
                           0,
                           Constants.BufferElements2Rec * Constants.BytesPerElement
                       )
                   }
                   audioRecord.stop()
                   audioRecord.release()
               }
               Log.d(TAG, "downloadRecordingLegacy: stopped recording")
               //TODO : now upload it..
               val uploadTask =
                   storage.reference.child(Constants.RECORDINGS_FOLDER).putFile(Uri.fromFile(file))


           } catch (e: Exception) {
               emit(DataState.Error(e))
           }


       }.flowOn(Dispatchers.IO)*/


    suspend fun downloadRecording(
        context: Context,
        audioRecord: AudioRecord,
        fileName: String,
        toUid: String,
        voiceRecordingMessage: Message
    ) =
        callbackFlow<DataState<Any>> {
            var fileUri: Uri? = null
            val externalContentUri = Utils.isSdkVer29Up {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                }
                try {
                    context.contentResolver.insert(externalContentUri, contentValues)
                        ?.also { uri ->
                            context.contentResolver.openOutputStream(uri).use { outputStream ->
                                Log.d(TAG, "downloadRecordingQ: recording...")
                                trySend(DataState.Loading)
                                isRecordingAtomic.set(true)
                                audioRecord.startRecording()
                                val sData = ShortArray(Constants.BufferElements2Rec)
                                var bData: ByteArray
                                while (isRecordingAtomic.get()) {
                                    audioRecord.read(sData, 0, Constants.BufferElements2Rec)
                                    bData = Utils.shortArrayToByteArray(sData)
                                    outputStream?.write(
                                        bData,
                                        0,
                                        Constants.BufferElements2Rec * Constants.BytesPerElement
                                    )
                                }
                            }
                            fileUri = uri
                        }

                } catch (e: Exception) {
                    trySend(DataState.Error(e))
                }
            } else {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    fileName
                )
                try {
                    file.outputStream().use { fileOutputStream ->
                        Log.d(TAG, "downloadRecordingLegacy: recording...")
                        trySend(DataState.Loading)
                        isRecordingAtomic.set(true)
                        audioRecord.startRecording()
                        val sData = ShortArray(Constants.BufferElements2Rec)
                        while (isRecordingAtomic.get()) {
                            audioRecord.read(sData, 0, Constants.BufferElements2Rec)
                            val bData = Utils.shortArrayToByteArray(sData)
                            fileOutputStream.write(
                                bData,
                                0,
                                Constants.BufferElements2Rec * Constants.BytesPerElement
                            )
                        }
                    }
                    fileUri = Uri.fromFile(file)
                } catch (e: Exception) {
                    trySend(DataState.Error(e))
                }

            }
            audioRecord.stop()
            audioRecord.release()


            if (fileUri != null) {
                val fileRef = storage.reference.child(Constants.RECORDINGS_FOLDER)
                    .child(auth.uid!!)
                    .child(toUid)
                    .child(fileName)
                fileRef.putFile(fileUri!!).addOnProgressListener {
                    val progress =
                        ((it.bytesTransferred / it.totalByteCount) * 100).toInt()
                    trySend(DataState.Progress(progress))
                }.continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let {
                            trySend(DataState.Error(it))
                            close(it)
                        }
                    }
                    fileRef.downloadUrl
                }.addOnCompleteListener { uploadRecordingTask ->
                    val downloadUrl = uploadRecordingTask.result
                    if (downloadUrl != null) {
                        when {
                            uploadRecordingTask.isSuccessful -> {
                                voiceRecordingMessage.voiceMessageUrl =
                                    downloadUrl.toString()
                                fireStore.collection(Constants.USER_COLLECTION)
                                    .document(auth.uid!!)
                                    .collection(Constants.USER_INBOX_COLLECTION)
                                    .document(toUid)
                                    .collection(Constants.USER_CONVERSATION_COLLECTION)
                                    .add(voiceRecordingMessage)
                                    .addOnCompleteListener { fromVoiceDoc ->
                                        when {
                                            fromVoiceDoc.isSuccessful -> {
                                                fireStore.collection(Constants.USER_COLLECTION)
                                                    .document(toUid)
                                                    .collection(Constants.USER_INBOX_COLLECTION)
                                                    .document(auth.uid!!)
                                                    .collection(Constants.USER_CONVERSATION_COLLECTION)
                                                    .add(voiceRecordingMessage)
                                                    .addOnCompleteListener { toVoiceDoc ->
                                                        when {
                                                            toVoiceDoc.isSuccessful -> {
                                                                trySend(
                                                                    DataState.Success(
                                                                        voiceRecordingMessage
                                                                    )
                                                                )
                                                                close()
                                                            }
                                                            toVoiceDoc.isCanceled -> {
                                                                trySend(DataState.Canceled)
                                                                close()
                                                            }
                                                            toVoiceDoc.exception != null -> {
                                                                trySend(
                                                                    DataState.Error(
                                                                        toVoiceDoc.exception!!
                                                                    )
                                                                )
                                                                close(toVoiceDoc.exception)
                                                            }
                                                        }

                                                    }
                                            }
                                            fromVoiceDoc.isCanceled -> {
                                                trySend(DataState.Canceled)
                                                close()
                                            }
                                            fromVoiceDoc.exception != null -> {
                                                trySend(DataState.Error(fromVoiceDoc.exception!!))
                                                close(fromVoiceDoc.exception)
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            } else {
                val e = IOException("File Uri is null")
                trySend(DataState.Error(e))
                close(e)
            }
            awaitClose { cancel() }
        }.flowOn(Dispatchers.IO)


    suspend fun downloadRecording2(
        context: Context,
        audioRecord: AudioRecord,
        fileName: String,
        toUid: String,
        voiceRecordingMessage: Message
    ) =
        callbackFlow<DataState<Any>> {
            var fileUri: Uri? = null
            val externalContentUri = Utils.isSdkVer29Up {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.TITLE, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.DURATION, recordingTimeMillis.get())
                }
                try {
                    val arr: ByteArray?
                    Log.d(TAG, "downloadRecordingQ: recording...")
                    trySend(DataState.Loading)
                    isRecordingAtomic.set(true)
                    audioRecord.startRecording()
                    val sData = ShortArray(Constants.BufferElements2Rec)
                    var bData: ByteArray? = null
                    while (isRecordingAtomic.get()) {
                        audioRecord.read(sData, 0, Constants.BufferElements2Rec)
                        bData = Utils.shortArrayToByteArray(sData)
                    }
                    arr = bData


                    // TODO : header is written successfully, but data is not, not a mic problem
                    context.contentResolver.insert(externalContentUri, contentValues)
                        ?.also { uri ->
                            context.contentResolver.openOutputStream(uri).use { encoded ->
                                writeToOutput(encoded!!, "RIFF") // chunk id
                                writeToOutput(
                                    encoded,
                                    36 + arr?.size!!
                                ) // chunk size
                                writeToOutput(encoded, "WAVE") // format

                                // SUB CHUNK 1 (FORMAT)
                                writeToOutput(encoded, "fmt ") // subchunk 1 id
                                writeToOutput(encoded, 16) // subchunk 1 size
                                writeToOutput(encoded, 1.toShort()) // audio format (1 = PCM)
                                writeToOutput(encoded, 1) // number of channelCount
                                writeToOutput(
                                    encoded,
                                    Constants.SAMPLING_RATE_IN_HZ
                                ) // sample rate
                                writeToOutput(
                                    encoded,
                                    Constants.SAMPLING_RATE_IN_HZ * 1 * Constants.BytesPerElement * 8 / 8
                                ) // byte rate
                                writeToOutput(
                                    encoded,
                                    (Constants.BytesPerElement).toShort()
                                ) // block align
                                writeToOutput(
                                    encoded,
                                    (Constants.BytesPerElement * 8).toShort()
                                ) // bits per sample

                                // SUB CHUNK 2 (AUDIO DATA)

                                writeToOutput(encoded, "data") // subchunk 2 id
                                writeToOutput(encoded, arr.size)// subchunk 2 size
                                copy(arr, encoded)
                                // copy(file2.inputStream(), encoded)
                            }
                            // TODO : fix later, convert to wav...

                            fileUri = uri
                        }

                } catch (e: Exception) {
                    trySend(DataState.Error(e))
                }
            } else {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    fileName + "_tmp"
                )
                try {
                    file.outputStream().use { fileOutputStream ->
                        Log.d(TAG, "downloadRecordingLegacy: recording...")
                        trySend(DataState.Loading)
                        isRecordingAtomic.set(true)
                        audioRecord.startRecording()
                        val sData = ShortArray(Constants.BufferElements2Rec)
                        while (isRecordingAtomic.get()) {
                            audioRecord.read(sData, 0, Constants.BufferElements2Rec)
                            val bData = Utils.shortArrayToByteArray(sData)
                            fileOutputStream.write(
                                bData,
                                0,
                                Constants.BufferElements2Rec * Constants.BytesPerElement
                            )
                        }
                    }


                    val file2 = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                        fileName
                    )
                    // TODO : find another method instead of creating two files...
                    convertPCMToWAV(
                        file,
                        file2,
                        1,
                        Constants.SAMPLING_RATE_IN_HZ,
                        Constants.BytesPerElement * 8
                    )
                    file.delete()
                    if (recordingTimeMillis.get() < 1) {
                        fileUri = null
                        file2.delete()
                    } else {
                        fileUri = file2.toUri()
                    }
                } catch (e: Exception) {
                    trySend(DataState.Error(e))
                }

            }
            audioRecord.stop()
            audioRecord.release()


            if (fileUri != null) {
                val fileRef = storage.reference.child(Constants.RECORDINGS_FOLDER)
                    .child(auth.uid!!)
                    .child(toUid)
                    .child(fileName)
                fileRef.putFile(fileUri!!).addOnProgressListener {
                    val progress =
                        ((it.bytesTransferred / it.totalByteCount) * 100).toInt()
                    trySend(DataState.Progress(progress))
                }.continueWithTask { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let {
                            trySend(DataState.Error(it))
                            close(it)
                        }
                    }
                    fileRef.downloadUrl
                }.addOnCompleteListener { uploadRecordingTask ->
                    val downloadUrl = uploadRecordingTask.result
                    if (downloadUrl != null) {
                        when {
                            uploadRecordingTask.isSuccessful -> {
                                voiceRecordingMessage.voiceMessageUrl =
                                    downloadUrl.toString()
                                voiceRecordingMessage.voiceMessageLengthMillis =
                                    recordingTimeMillis.get()
                                fireStore.collection(Constants.USER_COLLECTION)
                                    .document(auth.uid!!)
                                    .collection(Constants.USER_INBOX_COLLECTION)
                                    .document(toUid)
                                    .collection(Constants.USER_CONVERSATION_COLLECTION)
                                    .add(voiceRecordingMessage)
                                    .addOnCompleteListener { fromVoiceDoc ->
                                        when {
                                            fromVoiceDoc.isSuccessful -> {
                                                fireStore.collection(Constants.USER_COLLECTION)
                                                    .document(toUid)
                                                    .collection(Constants.USER_INBOX_COLLECTION)
                                                    .document(auth.uid!!)
                                                    .collection(Constants.USER_CONVERSATION_COLLECTION)
                                                    .add(voiceRecordingMessage)
                                                    .addOnCompleteListener { toVoiceDoc ->
                                                        when {
                                                            toVoiceDoc.isSuccessful -> {
                                                                trySend(
                                                                    DataState.Success(
                                                                        voiceRecordingMessage
                                                                    )
                                                                )
                                                                close()
                                                            }
                                                            toVoiceDoc.isCanceled -> {
                                                                trySend(DataState.Canceled)
                                                                close()
                                                            }
                                                            toVoiceDoc.exception != null -> {
                                                                trySend(
                                                                    DataState.Error(
                                                                        toVoiceDoc.exception!!
                                                                    )
                                                                )
                                                                close(toVoiceDoc.exception)
                                                            }
                                                        }

                                                    }
                                            }
                                            fromVoiceDoc.isCanceled -> {
                                                trySend(DataState.Canceled)
                                                close()
                                            }
                                            fromVoiceDoc.exception != null -> {
                                                trySend(DataState.Error(fromVoiceDoc.exception!!))
                                                close(fromVoiceDoc.exception)
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            } else {
                if (recordingTimeMillis.get() < 1) {
                    trySend(DataState.Canceled)
                    close()

                } else {
                    val e = IOException("File Uri is null")
                    trySend(DataState.Error(e))
                    close()
                }

            }
            awaitClose { cancel() }
        }.flowOn(Dispatchers.IO)

    fun playAudioFile(message: Message) = callbackFlow<DataState<Int>> {
        if (message.voiceMessageUrl != null) {
            // play it directly, hold -> download..
            storage.getReferenceFromUrl(message.voiceMessageUrl!!).downloadUrl
                .addOnCompleteListener { downloadTask ->
                    when {
                        downloadTask.exception != null -> {
                            trySend(DataState.Error(downloadTask.exception!!))
                            close()
                        }
                        downloadTask.isCanceled -> {
                            trySend(DataState.Canceled)
                            close()
                        }
                        downloadTask.isSuccessful -> {
                            if (downloadTask.result != null && downloadTask.result?.toString() == message.voiceMessageUrl!!) {
                                // it is present in storage, play
                                // TODO : inject the player directly

                                try {
                                    trySend(DataState.Loading)
                                    mediaPlayer = MediaPlayer()
                                    mediaPlayer?.setDataSource(message.voiceMessageUrl!!)
                                    mediaPlayer?.prepareAsync()
                                } catch (e: Exception) {
                                    isPlaying.set(false)
                                    mediaPlayer?.reset()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    Log.d(TAG, "saveAudioFile: player released")
                                    trySend(DataState.Error(e))
                                    close()
                                }
                                mediaPlayer?.setOnPreparedListener {
                                    isPlaying.set(true)
                                    it.start()
                                    trySend(DataState.Success(Constants.RECORDING_START))
                                }

                                mediaPlayer?.setOnCompletionListener {
                                    isPlaying.set(false)
                                    it.reset()
                                    it.release()
                                    mediaPlayer = null
                                    Log.d(TAG, "saveAudioFile: player released")
                                    trySend(DataState.Success(Constants.RECORDING_STOP))


                                }
                            }
                        }
                    }


                }
        }


        awaitClose { cancel() }
    }.flowOn(Dispatchers.IO)

    fun emitProgress() = flow<DataState<Int>> {
        while (mediaPlayer != null && isPlaying.get()) {
            try {
                emit(DataState.Success(mediaPlayer?.currentPosition!!))
                Thread.sleep(200)
            } catch (e: Exception) {
                emit(DataState.Canceled)
                isPlaying.set(false)

            }

        }
    }.flowOn(Dispatchers.IO)


    @Throws(IOException::class)
    fun convertPCMToWAV(
        input: File,
        output: File?,
        channelCount: Int,
        sampleRate: Int,
        bitsPerSample: Int
    ) {
        val inputSize = input.length().toInt()
        FileOutputStream(output).use { encoded ->
            // WAVE RIFF header
            writeToOutput(encoded, "RIFF") // chunk id
            writeToOutput(encoded, 36 + inputSize) // chunk size
            writeToOutput(encoded, "WAVE") // format

            // SUB CHUNK 1 (FORMAT)
            writeToOutput(encoded, "fmt ") // subchunk 1 id
            writeToOutput(encoded, 16) // subchunk 1 size
            writeToOutput(encoded, 1.toShort()) // audio format (1 = PCM)
            writeToOutput(encoded, channelCount.toShort()) // number of channelCount
            writeToOutput(encoded, sampleRate) // sample rate
            writeToOutput(encoded, sampleRate * channelCount * bitsPerSample / 8) // byte rate
            writeToOutput(encoded, (channelCount * bitsPerSample / 8).toShort()) // block align
            writeToOutput(encoded, bitsPerSample.toShort()) // bits per sample

            // SUB CHUNK 2 (AUDIO DATA)
            writeToOutput(encoded, "data") // subchunk 2 id
            writeToOutput(encoded, inputSize) // subchunk 2 size
            copy(FileInputStream(input), encoded)
        }
    }


    /**
     * Size of buffer used for transfer, by default
     */
    private val TRANSFER_BUFFER_SIZE = 10 * 1024

    /**
     * Writes string in big endian form to an output stream
     *
     * @param output stream
     * @param data   string
     * @throws IOException
     */
    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: String) {
        for (element in data) output.write(element.code)
    }

    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: Int) {
        output.write(data shr 0)
        output.write(data shr 8)
        output.write(data shr 16)
        output.write(data shr 24)
    }

    @Throws(IOException::class)
    fun writeToOutput(output: OutputStream, data: Short) {
        output.write(data.toInt() shr 0)
        output.write(data.toInt() shr 8)
    }

    @Throws(IOException::class)
    fun copy(source: InputStream, output: OutputStream): Long {
        return copy(source, output, TRANSFER_BUFFER_SIZE)
    }

    @Throws(IOException::class)
    fun copy(source: InputStream, output: OutputStream, bufferSize: Int): Long {
        var read = 0L
        val buffer = ByteArray(bufferSize)
        var n: Int
        while (source.read(buffer).also { n = it } != -1) {
            output.write(buffer, 0, n)
            read += n.toLong()
        }
        return read
    }

    @Throws(IOException::class)
    fun copy(source: ByteArray, output: OutputStream) {
        output.write(source, 0, source.size)
    }

    fun stopRecording(time: Long) {
        recordingTimeMillis.set(time)
        isRecordingAtomic.set(false)
    }

    private var mediaPlayer: MediaPlayer? = null
    private val isPlaying = AtomicBoolean(false)
    fun play(value: Boolean) {
        isPlaying.set(value)
    }
}