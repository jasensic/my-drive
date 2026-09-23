import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidGitVersionTest {
    @Test
    fun untaggedBranchUsesCommitCount() {
        val first = AndroidGitVersion.fromParts(tag = null, commitCount = 1, commitsAfterTag = 0)
        assertEquals("0.0.1", first.versionName)
        assertEquals(1, first.versionCode)

        val later = AndroidGitVersion.fromParts(tag = null, commitCount = 4, commitsAfterTag = 0)
        assertEquals("0.0.4", later.versionName)
        assertEquals(4, later.versionCode)
    }

    @Test
    fun taggedCommitKeepsTagAndLaterCommitsBumpPatch() {
        val tag = TaggedSemver(ref = "v1.0.0", major = 1, minor = 0, patch = 0)
        val atTag = AndroidGitVersion.fromParts(tag, commitCount = 12, commitsAfterTag = 0)
        assertEquals("1.0.0", atTag.versionName)
        assertEquals(1_000_000, atTag.versionCode)

        val after = AndroidGitVersion.fromParts(tag, commitCount = 14, commitsAfterTag = 2)
        assertEquals("1.0.2", after.versionName)
        assertEquals(1_000_002, after.versionCode)
    }

    @Test
    fun pickLatestTagUsesHighestSemverOnBranch() {
        val latest = AndroidGitVersion.pickLatestTag(
            sequenceOf("0.0.3", "v1.0.0", "0.9.9", "not-a-version"),
        )
        assertEquals("v1.0.0", latest?.ref)
        assertEquals(1, latest?.major)
    }

    @Test
    fun parseAcceptsOptionalVPrefix() {
        assertEquals(2, AndroidGitVersion.parseSemver("v2.3.4")?.major)
        assertEquals(3, AndroidGitVersion.parseSemver("v2.3.4")?.minor)
        assertEquals(4, AndroidGitVersion.parseSemver("2.3.4")?.patch)
        assertEquals(null, AndroidGitVersion.parseSemver("release-1"))
    }

    @Test
    fun pullRequestAppendsPrSuffix() {
        val tagged = TaggedSemver(ref = "v1.0.0", major = 1, minor = 0, patch = 0)
        val pr = AndroidGitVersion.fromParts(
            tag = tagged,
            commitCount = 13,
            commitsAfterTag = 1,
            prNumber = 1,
            official = false,
        )
        assertEquals("1.0.1-PR1", pr.versionName)
        assertEquals(1_000_001, pr.versionCode)
        assertEquals("my-drive-1.0.1-PR1.apk", pr.apkFileName())
    }

    @Test
    fun unofficialBranchWithoutPrNumberGetsBareSuffix() {
        val build = AndroidGitVersion.fromParts(
            tag = null,
            commitCount = 4,
            commitsAfterTag = 0,
            official = false,
        )
        assertEquals("0.0.4-PR", build.versionName)
    }

    @Test
    fun officialOnlyOnDefaultBranchOrTag() {
        assertEquals(
            true,
            AndroidGitVersion.isOfficialRef(
                currentBranch = "main",
                githubRef = "refs/heads/main",
                githubEventName = "push",
                prNumber = null,
            ),
        )
        assertEquals(
            true,
            AndroidGitVersion.isOfficialRef(
                currentBranch = "HEAD",
                githubRef = "refs/tags/v1.0.0",
                githubEventName = "push",
                prNumber = null,
            ),
        )
        assertEquals(
            false,
            AndroidGitVersion.isOfficialRef(
                currentBranch = "feature",
                githubRef = "refs/pull/1/merge",
                githubEventName = "pull_request",
                prNumber = 1,
            ),
        )
    }

    @Test
    fun detectPrNumberFromGithubRef() {
        assertEquals(
            42,
            AndroidGitVersion.detectPrNumber(
                githubEventName = "pull_request",
                githubRef = "refs/pull/42/merge",
            ),
        )
        assertEquals(
            null,
            AndroidGitVersion.detectPrNumber(
                githubEventName = "push",
                githubRef = "refs/heads/main",
            ),
        )
    }
}
