package com.zakodaniumask.manager.domain.usecase

import com.zakodaniumask.manager.data.packageinfo.SuperUserRepository

class GetSuperUserAppGroupUseCase(private val repository: SuperUserRepository) {
    suspend operator fun invoke(uid: Int, primaryPackageName: String) =
        repository.getAppGroup(uid, primaryPackageName)
}
