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
}
