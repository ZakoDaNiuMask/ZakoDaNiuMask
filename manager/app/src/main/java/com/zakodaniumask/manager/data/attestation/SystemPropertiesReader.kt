/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 *
 * Ported from Duck-Detector-Refactoring (https://github.com/eltavine/Duck-Detector-Refactoring).
 */

package com.zakodaniumask.manager.data.attestation

/** Reads system properties through the hidden SystemProperties API (app process, no root). */
object SystemPropertiesReader {

    private val getMethod by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
        }.getOrNull()
    }

    fun read(name: String): PropertyReadResult {
        val method = getMethod ?: return PropertyReadResult(available = false)
        return runCatching {
            val value = method.invoke(null, name, null) as? String
            if (value.isNullOrEmpty()) {
                PropertyReadResult(available = false)
            } else {
                PropertyReadResult(available = true, value = value)
            }
        }.getOrDefault(PropertyReadResult(available = false))
    }
}
