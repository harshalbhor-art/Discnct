package com.discnct.app.paywall

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The in-app product id — create this under Play Console > Monetize > Products > In-app products. */
const val LEVEL_2_PRODUCT_ID = "level2_unlock"

/**
 * Thin wrapper over Play Billing for the one thing this app sells: a one-time, non-consumable
 * unlock of Level 2 and Level 3. There is no server — Play's own purchase record is the entire
 * source of truth, re-read with [refresh] rather than cached across app starts, so a refund or a
 * reinstall on the same account both resolve themselves with nothing here to keep in sync.
 */
class BillingRepository(context: Context) {

    private val _isPurchased = MutableStateFlow(false)
    val isPurchased: StateFlow<Boolean> = _isPurchased.asStateFlow()

    /** Play's own formatted price for [LEVEL_2_PRODUCT_ID] (e.g. "₹149.00") — null until [refresh] loads it. */
    private val _priceLabel = MutableStateFlow<String?>(null)
    val priceLabel: StateFlow<String?> = _priceLabel.asStateFlow()

    private var productDetails: ProductDetails? = null

    // Only for the fire-and-forget acknowledgement a purchase needs — everything else runs on
    // whatever scope calls the suspend functions below (the paywall view model's own).
    private val ackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases?.forEach { purchase -> ackScope.launch { handlePurchase(purchase) } }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases()
        .build()

    /** Connects to Play; [onReady] is where the caller should kick off the first [refresh]. */
    fun start(onReady: () -> Unit) {
        if (client.isReady) {
            onReady()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }

            override fun onBillingServiceDisconnected() {
                // Play's own guidance: nothing to do here, just try again next time refresh() runs.
            }
        })
    }

    /** Re-checks Play's purchase record and the product's live price. Call on connect and on every resume. */
    suspend fun refresh() {
        if (!client.isReady) return

        val productParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(LEVEL_2_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val productResult = client.queryProductDetails(productParams)
        productDetails = productResult.productDetailsList?.firstOrNull()
        _priceLabel.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice

        val purchasesResult = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        _isPurchased.value = purchasesResult.purchasesList.any { purchase ->
            purchase.products.contains(LEVEL_2_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        purchasesResult.purchasesList.forEach { handlePurchase(it) }
    }

    /** Opens Play's own purchase sheet. No-ops if [refresh] hasn't loaded product details yet. */
    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(LEVEL_2_PRODUCT_ID)) return
        _isPurchased.value = true
        // Play refunds an unacknowledged purchase automatically after a few days, so this has to
        // happen for the unlock to stick — best-effort, since Play retries the purchase update
        // (and so this listener) if it never gets an acknowledgement.
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(ackParams)
        }
    }
}
