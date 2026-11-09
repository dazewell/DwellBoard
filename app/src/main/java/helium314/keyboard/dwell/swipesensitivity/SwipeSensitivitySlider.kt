// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.dwell.swipesensitivity

import androidx.compose.runtime.Composable
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.SliderPreference

@Composable
fun SwipeSensitivitySlider(setting: Setting, default: Int) {
    SliderPreference(
        name = setting.title,
        key = setting.key,
        default = default,
        range = SwipeSensitivity.MIN.toFloat()..SwipeSensitivity.MAX.toFloat(),
        stepSize = 5,
        description = { "$it%" }
    )
}
