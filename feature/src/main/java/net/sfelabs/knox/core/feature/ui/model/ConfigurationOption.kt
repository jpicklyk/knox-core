package net.sfelabs.knox.core.feature.ui.model

sealed interface ConfigurationOption {
    val key: String
    val label: String

    data class Toggle(
        override val key: String,
        override val label: String,
        val isEnabled: Boolean
    ) : ConfigurationOption

    data class Choice(
        override val key: String,
        override val label: String,
        val selected: String,
        val options: List<String>
    ) : ConfigurationOption

    data class NumberInput(
        override val key: String,
        override val label: String,
        val value: Int,
        val range: IntRange? = null
    ) : ConfigurationOption

    data class TextInput(
        override val key: String,
        override val label: String,
        val value: String,
        val hint: String? = null,
        val maxLength: Int? = null
    ) : ConfigurationOption

    data class TextList(
        override val key: String,
        override val label: String,
        val values: Set<String>,
        val hint: String? = null
    ) : ConfigurationOption
}
