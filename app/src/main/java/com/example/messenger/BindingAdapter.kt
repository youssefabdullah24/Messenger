package com.example.messenger

import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide

class BindingAdapter {
    companion object {
        @JvmStatic
        @BindingAdapter(value = ["verifyFirstName", "verifyLastName"], requireAll = false)
        fun TextView.verifyEmail(verifyFirstName: String?, verifyLastName: String?) {
            text = when {
                verifyFirstName != null && verifyLastName != null -> {
                    "Hello $verifyFirstName $verifyLastName\nPlease verify your Email Address"
                }

                else -> {
                    ""
                }
            }
        }

        @JvmStatic
        @BindingAdapter(value = ["firstName", "lastName"], requireAll = false)
        fun TextView.showName(firstName: String?, lastName: String?) {
            text = when {
                firstName != null && lastName != null -> {
                    "$firstName $lastName"
                }

                else -> {
                    "Unknown"
                }
            }
        }

        @JvmStatic
        @BindingAdapter("completeFirstName")
        fun TextView.completeProfile(completeFirstName: String?) {
            text = when {
                completeFirstName != null -> {
                    "$completeFirstName\nPlease complete your profile by adding a profile picture"
                }
                else -> {
                    ""
                }
            }
        }

        @JvmStatic
        @BindingAdapter("bindImage")
        fun ImageView.bindImage(url: String?) {
            when {
                url != null -> {
                    Glide.with(context)
                        .load(url)
                        .override(400, 400)
                        .into(this)
                }
                else -> {
                    Glide.with(context)
                        .load(R.drawable.avatar)
                        .override(400, 400)
                        .into(this)

                }
            }
        }

        @JvmStatic
        @BindingAdapter("bindHeader")
        fun TextView.bindHeader(textString: String?) {
            text = textString ?: "Unknown"
        }

        @JvmStatic
        @BindingAdapter("bindCountry")
        fun TextView.bindCountry(textString: String?) {
            text = textString ?: "Unknown"
        }

        @JvmStatic
        @BindingAdapter("bindLastMsg")
        fun TextView.bindLastMsg(textString: String?) {
            text = textString ?: "Start the conversation by saying Hi!"
        }


        @JvmStatic
        @BindingAdapter("bindDate")
        fun TextView.bindDate(textString: String?) {
            text = textString ?: "Undefined"
        }
    }
}

