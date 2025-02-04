package hk.eduhk.typeduck.data

import com.blankj.utilcode.util.ResourceUtils
import hk.eduhk.typeduck.util.Const
import timber.log.Timber
import java.io.File

object DataManager {
    private val prefs get() = AppPrefs.defaultInstance()
    @JvmStatic
    val sharedDataDir = File(prefs.profile.sharedDataDir)
    @JvmStatic
    val userDataDir = File(prefs.profile.userDataDir)
    val customDefault = File(sharedDataDir, "default.custom.yaml")
    val commonPatch = File(sharedDataDir, "common.custom.yaml")
    @JvmStatic
    val buildDir = File(userDataDir, "build")

    sealed class Diff {
        object New : Diff()
        object Update : Diff()
        object Keep : Diff()
    }

    @JvmStatic
    fun getDataDir(child: String = ""): String {
        return if (File(prefs.profile.sharedDataDir, child).exists()) {
            File(prefs.profile.sharedDataDir, child).absolutePath
        } else {
            File(prefs.profile.userDataDir, child).absolutePath
        }
    }

    private fun diff(old: String, new: String): Diff {
        return when {
            old.isBlank() -> Diff.New
            !new.contentEquals(old) -> Diff.Update
            else -> Diff.Keep
        }
    }

    @JvmStatic
    fun sync() {
        val newHash = Const.buildGitHash
        val oldHash = prefs.internal.lastBuildGitHash

        diff(oldHash, newHash).run {
            Timber.d("Diff: $this")
            when (this) {
                is Diff.New -> ResourceUtils.copyFileFromAssets(
                    "rime", sharedDataDir.absolutePath
                )
                is Diff.Update -> ResourceUtils.copyFileFromAssets(
                    "rime", sharedDataDir.absolutePath
                )
                is Diff.Keep -> {}
            }
        }

        // FIXME：缺失 default.custom.yaml 会导致方案列表为空
        if (!customDefault.exists()) {
            Timber.d("Creating empty default.custom.yaml ...")
            customDefault.createNewFile()
        }
        // Don't combine candidates
        Timber.d("Creating common.custom.yaml ...")
        commonPatch.writeText(
            """
            |patch:
            |  __patch:
            |    - common:/separate_candidates
            |    - common:/show_full_code
            """.trimMargin()
        )

        Timber.i("Synced!")
    }
}
