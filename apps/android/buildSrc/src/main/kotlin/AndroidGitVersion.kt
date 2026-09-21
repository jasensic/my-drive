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
    companion object {
        private val SEMVER = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        fun resolve(
            startDir: File,
            overrideName: String? = null,
            overrideCode: Int? = null,
        ): AndroidGitVersion {
            if (!overrideName.isNullOrBlank() && overrideCode != null) {
                return AndroidGitVersion(versionCode = overrideCode, versionName = overrideName)
            }
            val repoRoot = generateSequence(startDir.absoluteFile) { it.parentFile }
                .firstOrNull { dir -> File(dir, ".git").exists() }
                ?: return fallback()
            return fromGit(repoRoot)
        }

        internal fun fromParts(
            tag: TaggedSemver?,
            commitCount: Int,
            commitsAfterTag: Int,
        ): AndroidGitVersion {
            val count = commitCount.coerceAtLeast(1)
            val (major, minor, patch) = if (tag == null) {
                Triple(0, 0, count)
            } else {
                Triple(tag.major, tag.minor, tag.patch + commitsAfterTag.coerceAtLeast(0))
            }
            val versionName = "$major.$minor.$patch"
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

        private fun fromGit(repoRoot: File): AndroidGitVersion {
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
            return fromParts(tag, commitCount, distance)
        }

        private fun fallback() = fromParts(tag = null, commitCount = 1, commitsAfterTag = 0)

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
