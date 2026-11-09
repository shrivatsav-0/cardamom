package com.shrivatsav.cardamom

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.math.ceil

object LicenseConfig {
    const val PRODUCT_ID = "cardamom_lifetime_pro"
    const val DEFAULT_PRICE = "₹99"
    // Cloudflare Worker endpoint (update with user's deployed worker URL)
    const val WORKER_ENDPOINT = "https://cardamom-licensing-api.shrivatsav0412.workers.dev"
    private const val SALT = "Cardamom_Vault_Salt_2026_Secure"
    const val TRIAL_DURATION_MS = 7L * 24 * 60 * 60 * 1000L

    fun getDeviceHash(context: Context): String {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device_cardamom"
            val input = "$androidId:$SALT"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "fallback_hash_cardamom_${context.packageName.hashCode()}"
        }
    }
}

class TrialManager(private val context: Context, private val prefs: Prefs) {

    fun getLicenseState(): LicenseState {
        if (prefs.isPremium) return LicenseState.Premium
        if (!prefs.trialStarted) return LicenseState.NotStarted

        val now = System.currentTimeMillis()
        val lastSeen = prefs.lastSeenTimestamp

        // Anti-Clock Rollback: If device system clock is set backwards
        if (lastSeen > 0 && now < lastSeen) {
            // Tampered time detected: rely on last seen time + elapsed realtime ticks
            val expiresAt = prefs.trialExpiresDate
            if (lastSeen >= expiresAt) {
                return LicenseState.TrialExpired
            }
            val msLeft = (expiresAt - lastSeen).coerceAtLeast(0)
            val daysLeft = ceil(msLeft.toDouble() / (24 * 60 * 60 * 1000.0)).toInt().coerceIn(1, 7)
            return LicenseState.TrialActive(daysLeft)
        }

        prefs.lastSeenTimestamp = now

        val expiresAt = prefs.trialExpiresDate
        if (expiresAt > 0 && now >= expiresAt) {
            return LicenseState.TrialExpired
        }

        val msLeft = (expiresAt - now).coerceAtLeast(0)
        val daysLeft = ceil(msLeft.toDouble() / (24 * 60 * 60 * 1000.0)).toInt().coerceIn(1, 7)
        return LicenseState.TrialActive(daysLeft)
    }

    fun startTrial(scope: CoroutineScope, onResult: (LicenseState) -> Unit) {
        prefs.trialStarted = true
        val now = System.currentTimeMillis()
        prefs.trialStartDate = now
        prefs.trialExpiresDate = now + LicenseConfig.TRIAL_DURATION_MS
        prefs.lastSeenTimestamp = now

        onResult(LicenseState.TrialActive(daysLeft = 7))

        // Asynchronously sync with Cloudflare Worker
        syncWithServer(scope, onResult)
    }

    fun syncWithServer(scope: CoroutineScope, onResult: (LicenseState) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val deviceHash = LicenseConfig.getDeviceHash(context)
            try {
                val url = URL("${LicenseConfig.WORKER_ENDPOINT}/v1/trial/status")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val payload = JSONObject().apply {
                    put("device_hash", deviceHash)
                }

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                if (conn.responseCode in 200..299) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)
                    val status = json.optString("status")
                    val isPaid = json.optBoolean("is_paid", false)
                    val expiresAt = json.optLong("expires_at", 0L)
                    val daysLeft = json.optInt("days_left", 0)

                    withContext(Dispatchers.Main) {
                        if (isPaid) {
                            prefs.isPremium = true
                            onResult(LicenseState.Premium)
                        } else if (status == "EXPIRED") {
                            prefs.trialExpiresDate = System.currentTimeMillis() - 1000L
                            onResult(LicenseState.TrialExpired)
                        } else if (status == "ACTIVE") {
                            if (expiresAt > 0) prefs.trialExpiresDate = expiresAt
                            onResult(LicenseState.TrialActive(daysLeft.coerceIn(1, 7)))
                        }
                    }
                }
            } catch (e: Exception) {
                // Network unavailable: local state already established securely
            }
        }
    }
}

class BillingManager(
    private val context: Context,
    private val prefs: Prefs,
    private val onPremiumUnlocked: (isNewPurchase: Boolean) -> Unit
) : PurchasesUpdatedListener {

    private val TAG = "CardamomBilling"
    private var billingClient: BillingClient? = null
    var productDetails: ProductDetails? = null
        private set
    var formattedPrice: String = LicenseConfig.DEFAULT_PRICE
        private set

    private val candidateProductIds = listOf(
        LicenseConfig.PRODUCT_ID,
        "cardamom_premium",
        "cardamom_pro",
        "premium",
        "lifetime",
        "subscription",
        "cardamom_sub"
    ).distinct()

    fun initialize(scope: CoroutineScope) {
        val pendingParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingParams)
            .build()

        startConnection(scope)
    }

    private fun startConnection(scope: CoroutineScope, onConnected: (() -> Unit)? = null) {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.d(TAG, "onBillingSetupFinished: code=${billingResult.responseCode}, msg=${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails {
                        onConnected?.invoke()
                    }
                    queryExistingPurchases()
                } else {
                    onConnected?.invoke()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.d(TAG, "onBillingServiceDisconnected")
            }
        })
    }

    fun queryProductDetails(onComplete: ((ProductDetails?) -> Unit)? = null) {
        if (billingClient?.isReady != true) {
            onComplete?.invoke(null)
            return
        }

        // 1. Query INAPP products
        val inappProductList = candidateProductIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val inappParams = QueryProductDetailsParams.newBuilder()
            .setProductList(inappProductList)
            .build()

        billingClient?.queryProductDetailsAsync(inappParams) { billingResult, queryResult ->
            val inappList = queryResult.productDetailsList
            Log.d(TAG, "query INAPP result: code=${billingResult.responseCode}, count=${inappList.size}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !inappList.isNullOrEmpty()) {
                val details = inappList.first()
                productDetails = details
                val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                if (!price.isNullOrBlank()) formattedPrice = price
                onComplete?.invoke(details)
                return@queryProductDetailsAsync
            }

            // 2. Query SUBS products (Subscriptions)
            val subsProductList = candidateProductIds.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }
            val subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subsProductList)
                .build()

            billingClient?.queryProductDetailsAsync(subsParams) { subsResult, subsQueryResult ->
                val subsList = subsQueryResult.productDetailsList
                Log.d(TAG, "query SUBS result: code=${subsResult.responseCode}, count=${subsList.size}")
                if (subsResult.responseCode == BillingClient.BillingResponseCode.OK && !subsList.isNullOrEmpty()) {
                    val details = subsList.first()
                    productDetails = details
                    val offer = details.subscriptionOfferDetails?.firstOrNull()
                    val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    if (!price.isNullOrBlank()) formattedPrice = price
                    onComplete?.invoke(details)
                } else {
                    onComplete?.invoke(null)
                }
            }
        }
    }

    fun queryExistingPurchases() {
        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(inappParams) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handlePurchase(purchase, isNewPurchase = false)
                        return@queryPurchasesAsync
                    }
                }
            }

            // Check subscriptions as well
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            billingClient?.queryPurchasesAsync(subsParams) { subsResult, subsPurchases ->
                if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in subsPurchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            handlePurchase(purchase, isNewPurchase = false)
                            return@queryPurchasesAsync
                        }
                    }
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity) {
        Log.d(TAG, "launchBillingFlow requested. isReady=${billingClient?.isReady}, hasProductDetails=${productDetails != null}")

        fun executeLaunch(details: ProductDetails) {
            val builder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)

            if (details.productType == BillingClient.ProductType.SUBS) {
                val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                if (!offerToken.isNullOrBlank()) {
                    builder.setOfferToken(offerToken)
                }
            }

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(builder.build()))
                .build()

            val result = billingClient?.launchBillingFlow(activity, billingFlowParams)
            Log.d(TAG, "launchBillingFlow result: code=${result?.responseCode}, msg=${result?.debugMessage}")
            if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
                showStoreFallback(activity, "Google Play error: ${result?.debugMessage ?: "Code ${result?.responseCode}"}")
            }
        }

        val details = productDetails
        if (details != null && billingClient?.isReady == true) {
            executeLaunch(details)
            return
        }

        // When productDetails is still loading or connection is initializing:
        Toast.makeText(activity, "Connecting to Google Play...", Toast.LENGTH_SHORT).show()

        if (billingClient?.isReady != true) {
            startConnection(CoroutineScope(Dispatchers.Main)) {
                val freshDetails = productDetails
                activity.runOnUiThread {
                    if (freshDetails != null) {
                        executeLaunch(freshDetails)
                    } else {
                        showStoreFallback(activity, "Product '${LicenseConfig.PRODUCT_ID}' is not active in Google Play Console yet.")
                    }
                }
            }
        } else {
            queryProductDetails { freshDetails ->
                activity.runOnUiThread {
                    if (freshDetails != null) {
                        executeLaunch(freshDetails)
                    } else {
                        showStoreFallback(activity, "Product '${LicenseConfig.PRODUCT_ID}' is not active in Google Play Console yet.")
                    }
                }
            }
        }
    }

    private fun showStoreFallback(activity: Activity, reason: String) {
        AlertDialog.Builder(activity)
            .setTitle("Google Play Store")
            .setMessage("$reason\n\nWould you like to open the Play Store page?")
            .setPositiveButton("Open Play Store") { _, _ ->
                openPlayStore(activity)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    fun openPlayStore(activity: Activity) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(activity, "Could not open Google Play Store", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun restorePurchases(scope: CoroutineScope, onResult: (Boolean) -> Unit) {
        if (billingClient?.isReady != true) {
            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(res: BillingResult) {
                    if (res.responseCode == BillingClient.BillingResponseCode.OK) {
                        checkAndRestore(scope, onResult)
                    } else {
                        onResult(false)
                    }
                }
                override fun onBillingServiceDisconnected() {
                    onResult(false)
                }
            })
        } else {
            checkAndRestore(scope, onResult)
        }
    }

    private fun checkAndRestore(scope: CoroutineScope, onResult: (Boolean) -> Unit) {
        val inappParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(inappParams) { billingResult, purchases ->
            var restored = false
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        handlePurchase(purchase, isNewPurchase = false)
                        restored = true
                        break
                    }
                }
            }

            if (restored) {
                scope.launch(Dispatchers.Main) { onResult(true) }
                return@queryPurchasesAsync
            }

            // Check subs
            val subsParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            billingClient?.queryPurchasesAsync(subsParams) { subsResult, subsPurchases ->
                if (subsResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in subsPurchases) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            handlePurchase(purchase, isNewPurchase = false)
                            restored = true
                            break
                        }
                    }
                }
                scope.launch(Dispatchers.Main) { onResult(restored) }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Log.d(TAG, "onPurchasesUpdated: code=${billingResult.responseCode}, count=${purchases?.size ?: 0}")
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchase(purchase, isNewPurchase = true)
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase, isNewPurchase: Boolean = true) {
        // Only unlock for the expected product in PURCHASED state.
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.products.none { it == LicenseConfig.PRODUCT_ID }) return

        // Server is the source of truth: do NOT set isPremium locally first.
        // The worker verifies the token with the Google Play Developer API and
        // only then returns PAID. Fail closed if verification is unavailable.
        CoroutineScope(Dispatchers.IO).launch {
            val verified = try {
                val deviceHash = LicenseConfig.getDeviceHash(context)
                val url = URL("${LicenseConfig.WORKER_ENDPOINT}/v1/purchase/verify")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = JSONObject().apply {
                    put("device_hash", deviceHash)
                    put("purchase_token", purchase.purchaseToken)
                    put("product_id", LicenseConfig.PRODUCT_ID)
                }
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use {
                    it.write(payload.toString())
                    it.flush()
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body).optBoolean("is_paid", false)
                } else false
            } catch (_: Exception) { false }

            withContext(Dispatchers.Main) {
                if (verified) {
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient?.acknowledgePurchase(acknowledgePurchaseParams) { _ -> }
                    }
                    prefs.isPremium = true
                    onPremiumUnlocked(isNewPurchase)
                } else {
                    Toast.makeText(context, "Purchase could not be verified. Please check your connection and restore purchases.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}
