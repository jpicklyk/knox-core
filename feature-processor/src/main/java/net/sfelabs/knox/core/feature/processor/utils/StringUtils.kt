package net.sfelabs.knox.core.feature.processor.utils

import java.util.Locale

fun String.capitalizeWords(): String {
    return split("_").joinToString("") {
        it.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
                Locale.getDefault()
            ) else it.toString()
        }
    }
}