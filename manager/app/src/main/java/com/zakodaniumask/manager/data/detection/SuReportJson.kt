// SPDX-License-Identifier: GPL-3.0-or-later
// JSON bridge for the Duck-Detector-derived SuReport; see data/detection/SuRepository.kt.

package com.zakodaniumask.manager.data.detection

import org.json.JSONArray
import org.json.JSONObject

fun SuReport.toJsonString(): String {
    return JSONObject().apply {
        put("stage", stage.name)
        put("suBinaries", JSONArray(suBinaries))
        put("daemons", JSONArray().apply {
            daemons.forEach { daemon ->
                put(JSONObject().put("name", daemon.name).put("path", daemon.path))
            }
        })
        put("selfContext", selfContext)
        put("selfContextAbnormal", selfContextAbnormal)
        put("suspiciousProcesses", JSONArray(suspiciousProcesses))
        put("nativeAvailable", nativeAvailable)
        put("checkedSuPathCount", checkedSuPathCount)
        put("checkedDaemonPathCount", checkedDaemonPathCount)
        put("unobservablePathCount", unobservablePathCount)
        put("checkedProcessCount", checkedProcessCount)
        put("deniedProcessCount", deniedProcessCount)
        put("methods", JSONArray().apply {
            methods.forEach { method ->
                put(
                    JSONObject()
                        .put("label", method.label)
                        .put("summary", method.summary)
                        .put("outcome", method.outcome.name)
                        .put("detail", method.detail)
                )
            }
        })
        put("errorMessage", errorMessage)
    }.toString()
}

fun parseSuReport(json: String): SuReport {
    val root = JSONObject(json)

    fun stringList(key: String): List<String> {
        val array = root.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.optString(it) }
    }

    val daemons = root.optJSONArray("daemons")?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            SuDaemonFinding(
                name = item.optString("name"),
                path = item.optString("path"),
            )
        }
    }.orEmpty()

    val methods = root.optJSONArray("methods")?.let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            SuMethodResult(
                label = item.optString("label"),
                summary = item.optString("summary"),
                outcome = runCatching { SuMethodOutcome.valueOf(item.optString("outcome")) }
                    .getOrDefault(SuMethodOutcome.SUPPORT),
                detail = item.optString("detail").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }.orEmpty()

    return SuReport(
        stage = runCatching { SuStage.valueOf(root.optString("stage")) }
            .getOrDefault(SuStage.FAILED),
        suBinaries = stringList("suBinaries"),
        daemons = daemons,
        selfContext = root.optString("selfContext"),
        selfContextAbnormal = root.optBoolean("selfContextAbnormal"),
        suspiciousProcesses = stringList("suspiciousProcesses"),
        nativeAvailable = root.optBoolean("nativeAvailable"),
        checkedSuPathCount = root.optInt("checkedSuPathCount"),
        checkedDaemonPathCount = root.optInt("checkedDaemonPathCount"),
        unobservablePathCount = root.optInt("unobservablePathCount"),
        checkedProcessCount = root.optInt("checkedProcessCount"),
        deniedProcessCount = root.optInt("deniedProcessCount"),
        methods = methods,
        errorMessage = root.optString("errorMessage").takeIf { it.isNotBlank() && it != "null" },
    )
}
