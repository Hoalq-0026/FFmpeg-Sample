package com.quanghoa.apps.changeresolutionvideo

import android.app.Activity
import android.widget.Toast

fun Activity.showToast(message: String?) {
    message?.let { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}
