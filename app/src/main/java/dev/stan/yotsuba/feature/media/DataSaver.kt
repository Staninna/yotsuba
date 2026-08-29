package dev.stan.yotsuba.feature.media

import dev.stan.yotsuba.core.network.NetworkStatus

/**
 * Whether the viewer should hold back the heavy bytes -- no video autoplay, full-size
 * images behind a "Load" tap -- given the data-saver setting and the connection.
 *
 * Only a metered connection with the setting on defers anything. Wifi ignores the
 * setting: the point is the phone bill, not the bandwidth. Offline defers nothing either;
 * there is nothing to spend, and the cache may still answer.
 */
fun defersHeavyMedia(dataSaver: Boolean, status: NetworkStatus): Boolean =
    dataSaver && status.isMetered
