package com.ivor.openstream.di

import okhttp3.Interceptor

/**
 * APKs are currently published from assembleDebug. Even debug builds must never
 * print TMDB API keys, gateway authorization headers or signed media URLs.
 * Developers may use a separate local network inspector with explicit consent.
 */
fun createLoggingInterceptor(detailed: Boolean): Interceptor =
    Interceptor { chain -> chain.proceed(chain.request()) }
