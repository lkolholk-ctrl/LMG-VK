package com.lmg.vk.ui.screens

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.lmg.vk.ui.mix.VkMixLottieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun CachedCatalogLottie(url: String, animationId: String, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val composition by produceState(VkMixLottieStore.cachedComposition(animationId, url), animationId, url) {
        value = withContext(Dispatchers.IO) { VkMixLottieStore.loadComposition(context, animationId, url) }
    }
    CatalogLottie(composition, modifier)
}

@Composable
internal fun BundledCatalogLottie(resource: Int, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val composition by produceState<LottieComposition?>(null, resource) {
        value = withContext(Dispatchers.IO) { LottieCompositionFactory.fromRawResSync(context, resource).value }
    }
    CatalogLottie(composition, modifier)
}

@Composable
private fun CatalogLottie(composition: LottieComposition?, modifier: Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { arrayOfNulls<LottieAnimationView>(1) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder[0]?.resumeAnimation()
                Lifecycle.Event.ON_STOP -> holder[0]?.pauseAnimation()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder[0]?.cancelAnimation()
            holder[0] = null
        }
    }
    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { context ->
            LottieAnimationView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                repeatCount = LottieDrawable.INFINITE
                setSafeMode(true)
                holder[0] = this
            }
        },
        update = { view ->
            if (composition != null && view.composition !== composition) {
                view.setComposition(composition)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.playAnimation()
            }
        },
        onRelease = { view ->
            view.cancelAnimation()
            view.setImageDrawable(null)
            holder[0] = null
        },
    )
}
