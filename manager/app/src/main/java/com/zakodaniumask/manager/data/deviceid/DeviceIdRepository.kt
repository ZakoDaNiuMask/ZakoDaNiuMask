// SPDX-License-Identifier: GPL-3.0-or-later
// Ported from Duck-ToolBox (MIT), ui/src/features/device-ids; modified for ZakoDaNiuMask.
package com.zakodaniumask.manager.data.deviceid

import com.zakodaniumask.manager.data.shell.KsuCliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Attestation device IDs written into the Qualcomm Keymaster trusted application.
 */
data class DeviceIdsProfile(
    val brand: String = "",
    val device: String = "",
    val product: String = "",
    val serial: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val imei: String = "",
    val imei2: String = "",
    val meid: String = "",
    val meid2: String = "",
    val taName: String = "keymaster64",
    val taPath: String = "/vendor/firmware_mnt/image",
)

data class ProvisionedId(
    val label: String,
    val value: String,
)

data class DeviceIdProvisionResult(
    val count: Int,
    val ids: List<ProvisionedId>,
    val dryRun: Boolean,
    val taName: String,
    val taPath: String,
    val loadedLibrary: String?,
    val taApiVersion: String?,
    val taVersion: String?,
    val commandHex: String,
    val responseHex: String?,
)

class DeviceIdRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private fun shq(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    suspend fun defaults(): DeviceIdsProfile? = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            val output = shell.newJob()
                .add("${ksuCliRepository.getKsuDaemonPath()} device-ids defaults")
                .to(ArrayList<String>(), null)
                .exec()
            if (!output.isSuccess) return@runCatching null
            parseProfile(JSONObject(output.out.joinToString("\n").trim()))
        }.getOrNull()
    }

    suspend fun provision(
        profile: DeviceIdsProfile,
        dryRun: Boolean,
    ): Result<DeviceIdProvisionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val args = buildList {
                fun opt(flag: String, value: String) {
                    if (value.isNotBlank()) {
                        add("--$flag")
                        add(shq(value))
                    }
                }
                opt("brand", profile.brand)
                opt("device", profile.device)
                opt("product", profile.product)
                opt("serial", profile.serial)
                opt("manufacturer", profile.manufacturer)
                opt("model", profile.model)
                opt("imei", profile.imei)
                opt("imei2", profile.imei2)
                opt("meid", profile.meid)
                opt("meid2", profile.meid2)
                opt("ta-name", profile.taName)
                opt("ta-path", profile.taPath)
                if (dryRun) add("--dry-run")
            }
            val command =
                "${ksuCliRepository.getKsuDaemonPath()} device-ids provision ${args.joinToString(" ")}"
            val result = ksuCliRepository.getRootShell().newJob()
                .add(command)
                .to(ArrayList<String>(), null)
                .exec()
            if (!result.isSuccess) {
                throw IllegalStateException(
                    result.err.joinToString("\n").trim().ifBlank {
                        "device ID provisioning failed"
                    }
                )
            }
            parseResult(JSONObject(result.out.joinToString("\n").trim()))
        }
    }

    private fun parseProfile(json: JSONObject) = DeviceIdsProfile(
        brand = json.optString("brand"),
        device = json.optString("device"),
        product = json.optString("product"),
        serial = json.optString("serial"),
        manufacturer = json.optString("manufacturer"),
        model = json.optString("model"),
        imei = json.optString("imei"),
        imei2 = json.optString("imei2"),
        meid = json.optString("meid"),
        meid2 = json.optString("meid2"),
        taName = json.optString("ta_name", "keymaster64"),
        taPath = json.optString("ta_path", "/vendor/firmware_mnt/image"),
    )

    private fun parseResult(json: JSONObject) = DeviceIdProvisionResult(
        count = json.optInt("count", 0),
        ids = json.optJSONArray("ids")?.let(::parseIds).orEmpty(),
        dryRun = json.optBoolean("dry_run", false),
        taName = json.optString("ta_name"),
        taPath = json.optString("ta_path"),
        loadedLibrary = json.optNullableString("loaded_library"),
        taApiVersion = json.optNullableString("ta_api_version"),
        taVersion = json.optNullableString("ta_version"),
        commandHex = json.optString("command_hex"),
        responseHex = json.optNullableString("response_hex"),
    )

    private fun parseIds(array: JSONArray): List<ProvisionedId> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ProvisionedId(
                label = item.optString("label"),
                value = item.optString("value"),
            )
        }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
