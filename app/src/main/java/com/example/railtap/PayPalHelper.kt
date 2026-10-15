package com.example.railtap

import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Talks to the PayPal SANDBOX (fake money, for testing only).
 *
 * The secret key is inside the app here, which is only okay for testing.
 * For a real app, this code should run on a server instead.
 *
 * All functions make network calls, so call them from a background thread.
 */
object PayPalHelper {

    private const val BASE_URL = "https://api-m.sandbox.paypal.com"
    const val RETURN_URL = "railtap://paypal/return"
    const val CANCEL_URL = "railtap://paypal/cancel"

    private val http = OkHttpClient()
    private val JSON = "application/json".toMediaType()

    // Sends a request and returns the response as JSON (throws an error if PayPal says no)
    private fun send(request: Request): JSONObject {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("PayPal error ${response.code}: $body")
            }
            return JSONObject(body)
        }
    }

    // Step 1: get an access token using the Client ID and Secret
    private fun getAccessToken(): String {
        val request = Request.Builder()
            .url("$BASE_URL/v1/oauth2/token")
            .header("Authorization", Credentials.basic(BuildConfig.PAYPAL_CLIENT_ID, BuildConfig.PAYPAL_SECRET))
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()
        return send(request).getString("access_token")
    }

    // Step 2: create an order and return the PayPal link the user needs to open
    fun createOrder(amountPesos: String, referenceNo: String): String {
        val body = """
            {
              "intent": "CAPTURE",
              "purchase_units": [
                {
                  "reference_id": "$referenceNo",
                  "description": "RailTap booking $referenceNo",
                  "amount": { "currency_code": "PHP", "value": "$amountPesos" }
                }
              ],
              "payment_source": {
                "paypal": {
                  "experience_context": {
                    "brand_name": "RailTap",
                    "return_url": "$RETURN_URL",
                    "cancel_url": "$CANCEL_URL",
                    "user_action": "PAY_NOW"
                  }
                }
              }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("$BASE_URL/v2/checkout/orders")
            .header("Authorization", "Bearer ${getAccessToken()}")
            .post(body.toRequestBody(JSON))
            .build()

        val links = send(request).getJSONArray("links")
        for (i in 0 until links.length()) {
            val link = links.getJSONObject(i)
            if (link.getString("rel") == "payer-action" || link.getString("rel") == "approve") {
                return link.getString("href")
            }
        }
        throw Exception("PayPal did not return an approval link")
    }

    // Step 3: after the user approves, capture (complete) the payment.
    // Returns "COMPLETED" if the money went through.
    fun captureOrder(orderId: String): String {
        val request = Request.Builder()
            .url("$BASE_URL/v2/checkout/orders/$orderId/capture")
            .header("Authorization", "Bearer ${getAccessToken()}")
            .post("{}".toRequestBody(JSON))
            .build()
        return send(request).getString("status")
    }
}
