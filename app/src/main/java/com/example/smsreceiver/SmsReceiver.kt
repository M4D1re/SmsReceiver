package com.example.smscamera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.SmsMessage

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val bundle: Bundle? = intent.extras
        try {
            if (bundle != null) {
                val pdus = bundle.get("pdus") as Array<Any>
                for (pdu in pdus) {
                    val smsMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(pdu as ByteArray, bundle.getString("format"))
                    } else {
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }
                    val sender = smsMessage.displayOriginatingAddress
                    val message = smsMessage.displayMessageBody

                    // Отправить данные в MainActivity
                    val mainActivityIntent = Intent(context, MainActivity::class.java)
                    mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    mainActivityIntent.putExtra("sender", sender)
                    mainActivityIntent.putExtra("message", message)
                    context.startActivity(mainActivityIntent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}