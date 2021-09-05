package com.example.messenger

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
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
        @BindingAdapter("bindImageMessage")
        fun ImageView.bindImageMessage(uri: String?) {
            uri?.let {
                Glide.with(context)
                    .load(uri)
                    .override(1024, 1024)
                    .into(this)
            }

        }

        @JvmStatic
        @BindingAdapter("textCardViewVisibility")
        fun CardView.textCardViewVisibility(text: String?) {
            visibility = if (text.isNullOrBlank() || text.isNullOrEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        @JvmStatic
        @BindingAdapter("imageCardViewVisibility")
        fun CardView.imageCardViewVisibility(imageUrl: String?) {
            visibility = if (imageUrl.isNullOrBlank() || imageUrl.isNullOrEmpty()) {
                View.GONE
            } else {
                View.VISIBLE

            }
        }

        @JvmStatic
        @BindingAdapter("setMessageStatus")
        fun ImageView.setMessageStatus(status: Boolean) {
            when (status) {
                true -> {
                    Glide.with(context)
                        .load(R.drawable.ic_baseline_check_circle_outline_white_15)
                        .into(this)
                }
                false -> {
                    Glide.with(context)
                        .load(R.drawable.ic_baseline_access_time_white_15)
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


        @JvmStatic
        @BindingAdapter("getDateFromTimeStamp")
        fun TextView.getDateFromTimeStamp(timeStamp: Long) {
            text = Utils.getDateStringFromTimeStamp(timeStamp)
        }


        @JvmStatic
        @BindingAdapter("getTimeFromTimeStamp")
        fun TextView.getTimeFromTimeStamp(timeStamp: Long) {
            text = Utils.getTimeStringFromTimeStamp(timeStamp)
        }

        @JvmStatic
        @BindingAdapter("bindMessage")
        fun TextView.bindMessage(message: String?) {
            message?.let {
                text = message
            }

        }
    }
}


