package com.lmg.vk.network

internal data class PhysicalNetworkRoute<T>(
    val network: T,
    val physical: Boolean,
    val blocked: Boolean,
    val suspended: Boolean,
    val validated: Boolean,
    val transportPriority: Int,
)

internal fun <T> selectPhysicalNetwork(routes: List<PhysicalNetworkRoute<T>>, current: T?): T? =
    routes.asSequence()
        .filter { it.physical && !it.blocked && !it.suspended }
        .maxByOrNull { ((if (it.validated) 100 else 0) + it.transportPriority) * 2 +
            if (it.network == current) 1 else 0 }
        ?.network
