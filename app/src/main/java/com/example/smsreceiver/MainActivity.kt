package com.example.smscamera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.telephony.SmsMessage
import android.widget.EditText
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class MainActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var switchEnabled: Switch

    private val REQUEST_CODE_CAMERA = 1
    private val REQUEST_ACCOUNT_PICKER = 2
    private val REQUEST_AUTHORIZATION = 3

    private var currentPhotoPath: String? = null
    private lateinit var credential: GoogleAccountCredential

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emailEditText = findViewById(R.id.emailEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        switchEnabled = findViewById(R.id.switchEnabled)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.CAMERA), 1)
        }

        credential = GoogleAccountCredential.usingOAuth2(
            this,
            listOf("https://www.googleapis.com/auth/gmail.send")
        )
        startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER)

        val sender = intent.getStringExtra("sender")
        val message = intent.getStringExtra("message")
        if (sender != null && message != null) {
            handleSmsReceived(sender, message)
        }
    }

    private fun handleSmsReceived(sender: String, message: String) {
        val phone = phoneEditText.text.toString()
        if (switchEnabled.isChecked && sender == phone && message.contains("...")) {
            openCamera()
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_CODE_CAMERA)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_CAMERA -> {
                if (resultCode == Activity.RESULT_OK) {
                    val imageUri: Uri? = data?.data
                    imageUri?.let {
                        currentPhotoPath = it.path
                        sendEmail(it)
                    }
                }
            }
            REQUEST_ACCOUNT_PICKER -> {
                if (resultCode == Activity.RESULT_OK && data != null && data.extras != null) {
                    val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if (accountName != null) {
                        credential.selectedAccountName = accountName
                    }
                }
            }
            REQUEST_AUTHORIZATION -> {
                if (resultCode == Activity.RESULT_OK) {
                    sendEmail(Uri.parse(currentPhotoPath))
                }
            }
        }
    }

    private fun sendEmail(imageUri: Uri) {
        val email = emailEditText.text.toString()
        val thread = Thread {
            try {
                val service = Gmail.Builder(NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential)
                    .setApplicationName("SMS Camera")
                    .build()

                val mimeMessage = createEmail(email, "me", "Photo from Android App", "Here is the photo you requested.", imageUri)
                val message = createMessageWithEmail(mimeMessage)
                service.users().messages().send("me", message).execute()
            } catch (e: UserRecoverableAuthIOException) {
                startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        thread.start()
    }

    @Throws(Exception::class)
    private fun createEmail(to: String, from: String, subject: String, bodyText: String, fileUri: Uri): MimeMessage {
        val props = Properties()
        val session = javax.mail.Session.getDefaultInstance(props, null)

        val email = MimeMessage(session)
        email.setFrom(InternetAddress(from))
        email.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
        email.subject = subject

        val multipart = MimeMultipart()

        val textBodyPart = MimeBodyPart()
        textBodyPart.setText(bodyText)
        multipart.addBodyPart(textBodyPart)

        val fileBodyPart = MimeBodyPart()
        fileBodyPart.dataHandler = DataHandler(FileDataSource(File(fileUri.path!!)))
        fileBodyPart.fileName = "photo.jpg"
        multipart.addBodyPart(fileBodyPart)

        email.setContent(multipart)
        return email
    }

    @Throws(Exception::class)
    private fun createMessageWithEmail(emailContent: MimeMessage): Message {
        val buffer = ByteArrayOutputStream()
        emailContent.writeTo(buffer)
        val bytes = buffer.toByteArray()
        val encodedEmail = Base64.getEncoder().encodeToString(bytes)
        val message = Message()
        message.raw = encodedEmail
        return message
    }
}