package com.dparadox.paradoxuploader.domain.util

data class LicenseOption(val label: String, val url: String)

/** Licenses a folder can apply to its files (stored on archive.org as the `licenseurl` field). */
object Licenses {
    val ALL: List<LicenseOption> = listOf(
        LicenseOption("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
        LicenseOption("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"),
        LicenseOption("CC BY-NC 4.0", "https://creativecommons.org/licenses/by-nc/4.0/"),
        LicenseOption("CC BY-NC-SA 4.0", "https://creativecommons.org/licenses/by-nc-sa/4.0/"),
        LicenseOption("CC BY-ND 4.0", "https://creativecommons.org/licenses/by-nd/4.0/"),
        LicenseOption("CC BY-NC-ND 4.0", "https://creativecommons.org/licenses/by-nc-nd/4.0/"),
        LicenseOption("CC0 1.0 (public domain)", "https://creativecommons.org/publicdomain/zero/1.0/")
    )

    fun labelFor(url: String?): String? = ALL.firstOrNull { it.url == url }?.label
}
