/*
 * Copyright 2026 Duck Apps Contributor
 * If you have any questions, suggestions, or other inquiries, please email Eltavine <me@eltavine.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported from Duck-Detector-Refactoring (https://github.com/eltavine/Duck-Detector-Refactoring).
 *
 * NOTE: Only the preliminary (pure-Kotlin) probes are ported. Duck Detector's native
 * /proc process-context collector (SuNativeBridge.nativeCollectSnapshot) is intentionally not
 * ported, so the snapshot is always reported as unavailable and SuRepository falls back to
 * reading /proc/self/attr/current.
 */

package com.zakodaniumask.manager.data.detection

class SuNativeBridge {

    fun collectSnapshot(): SuNativeSnapshot = SuNativeSnapshot(available = false)
}
