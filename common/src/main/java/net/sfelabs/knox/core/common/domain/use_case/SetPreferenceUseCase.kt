package net.sfelabs.knox.core.common.domain.use_case

import net.sfelabs.knox.core.common.domain.repository.PreferencesRepository
import javax.inject.Inject

class SetPreferenceUseCase<T> @Inject constructor(
    private val repository: PreferencesRepository
){
    suspend operator fun invoke(key: String, value: T) {
        repository.setValue(key, value)
    }
}