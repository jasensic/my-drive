import java.io.File

internal data class TaggedSemver(
    val ref: String,
    val major: Int,
    val minor: Int,
    val patch: Int,
)

data class AndroidGitVersion(
    val versionCode: Int,
    val versionName: String,
) {
    fun apkFileName(appName: String = "my-drive"): String = "$appName-$versionName.apk"

    companion object {
        private val SEMVER = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        fun resolve(
            startDir: File,
            overrideName: String? = null,
            overrideCode: Int? = null,
            prNumberProperty: String? = null,
            githubRef: String? = System.getenv("GITHUB_REF"),
            githubEventName: String? = System.getenv("GITHUB_EVENT_NAME"),
        ): AndroidGitVersion {
            val prNumber = detectPrNumber(property = prNumberProperty)
            if (!overrideName.isNullOrBlank() && overrideCode != null) {
                return AndroidGitVersion(versionCode = overrideCode, versionName = overrideName)
            }
            val repoRoot = generateSequence(startDir.absoluteFile) { it.parentFile }
                .firstOrNull { dir -> File(dir, ".git").exists() }
                ?: return fromParts(
                    tag = null,
                    commitCount = 1,
                    commitsAfterTag = 0,
                    prNumber = prNumber,
                    official = isOfficialRef(
                        currentBranch = null,
                        githubRef = githubRef,
                        githubEventName = githubEventName,
                        prNumber = prNumber,
                    ),
                )
            return fromGit(repoRoot, prNumber, githubRef, githubEventName)
        }

        internal fun detectPrNumber(
            property: String? = null,
            envPrNumber: String? = System.getenv("ANDROID_PR_NUMBER"),
            githubEventName: String? = System.getenv("GITHUB_EVENT_NAME"),
            githubRef: String? = System.getenv("GITHUB_REF"),
        ): Int? {
            property?.toIntOrNull()?.let { return it }
            envPrNumber?.toIntOrNull()?.let { return it }
            if (githubEventName != "pull_request") return null
            return Regex("""refs/pull/(\d+)""").find(githubRef.orEmpty())
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        }

        internal fun isOfficialRef(
            currentBranch: String?,
            githubRef: String?,
            githubEventName: String?,
            prNumber: Int?,
        ): Boolean {
            if (prNumber != null || githubEventName == "pull_request") return false
            if (githubRef.orEmpty().startsWith("refs/tags/")) return true
            val branch = when {
                githubRef.orEmpty().startsWith("refs/heads/") ->
                    githubRef!!.removePrefix("refs/heads/")
                else -> currentBranch
            }
            return branch == "main" || branch == "master"
        }

        internal fun fromParts(
            tag: TaggedSemver?,
            commitCount: Int,
            commitsAfterTag: Int,
            prNumber: Int? = null,
            official: Boolean = prNumber == null,
        ): AndroidGitVersion {
            val count = commitCount.coerceAtLeast(1)
            val (major, minor, patch) = if (tag == null) {
                Triple(0, 0, count)
            } else {
                Triple(tag.major, tag.minor, tag.patch + commitsAfterTag.coerceAtLeast(0))
            }
            val suffix = when {
                prNumber != null -> "-PR$prNumber"
                !official -> "-PR"
                else -> ""
            }
            val versionName = "$major.$minor.$patch$suffix"
            val encoded = major * 1_000_000 + minor * 10_000 + patch
            return AndroidGitVersion(
                versionCode = maxOf(encoded, count),
                versionName = versionName,
            )
        }

        internal fun parseSemver(raw: String): TaggedSemver? {
            val match = SEMVER.matchEntire(raw.trim()) ?: return null
            return TaggedSemver(
                ref = raw.trim(),
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
            )
        }

        internal fun pickLatestTag(tags: Sequence<String>): TaggedSemver? =
            tags.mapNotNull { parseSemver(it) }
                .maxWithOrNull(compareBy({ it.major }, { it.minor }, { it.patch }))

        private fun fromGit(
            repoRoot: File,
            prNumber: Int?,
            githubRef: String?,
            githubEventName: String?,
        ): AndroidGitVersion {
            val commitCount = git(repoRoot, "rev-list", "--count", "HEAD")
                ?.toIntOrNull()
                ?: 1
            val tag = pickLatestTag(
                (git(repoRoot, "tag", "--merged", "HEAD") ?: "")
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
            )
            val distance = if (tag == null) {
                0
            } else {
                git(repoRoot, "rev-list", "--count", "${tag.ref}..HEAD")?.toIntOrNull() ?: 0
            }
            val branch = git(repoRoot, "rev-parse", "--abbrev-ref", "HEAD")
            return fromParts(
                tag,
                commitCount,
                distance,
                prNumber = prNumber,
                official = isOfficialRef(
                    currentBranch = branch,
                    githubRef = githubRef,
                    githubEventName = githubEventName,
                    prNumber = prNumber,
                ),
            )
        }

        private fun git(repoRoot: File, vararg args: String): String? {
            return try {
                val process = ProcessBuilder("git", *args)
                    .directory(repoRoot)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText().trim()
                if (process.waitFor() == 0 && output.isNotEmpty()) output else null
            } catch (_: Exception) {
                null
            }
        }
    }
}
